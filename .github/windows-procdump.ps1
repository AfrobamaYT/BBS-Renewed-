param([Parameter(Mandatory=$true)][ValidateSet("prepare","watch","collect")][string]$Mode)
$ErrorActionPreference = "Stop"
if ($env:GITHUB_ACTIONS -ne "true" -or $env:RUNNER_OS -ne "Windows" -or $env:RUNNER_ENVIRONMENT -ne "github-hosted") {
    throw "ProcDump monitoring is restricted to disposable GitHub-hosted Windows runners"
}
$taskRoot = Join-Path $env:RUNNER_TEMP "bbs-procdump"
$taskState = Join-Path $taskRoot "state.json"
$taskPidFile = Join-Path $taskRoot "watcher.pid"
$taskDumpDir = Join-Path $env:RUNNER_TEMP "bbs-native-dumps"
$taskOutput = Join-Path $env:GITHUB_WORKSPACE "build/production-diagnostics/windows"
function Save-CaptureState($value) {
    $value | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 ($taskState + ".tmp")
    Move-Item -Force ($taskState + ".tmp") $taskState
}
if ($Mode -eq "prepare") {
    New-Item -ItemType Directory -Force $taskRoot,$taskDumpDir,$taskOutput | Out-Null
    $taskZip = Join-Path $taskRoot "Procdump.zip"
    Invoke-WebRequest "https://download.sysinternals.com/files/Procdump.zip" -OutFile $taskZip
    Expand-Archive -Force $taskZip $taskRoot
    $taskBinary = Join-Path $taskRoot "procdump64.exe"
    $taskSignature = Get-AuthenticodeSignature $taskBinary
    if ($taskSignature.Status -ne "Valid" -or $taskSignature.SignerCertificate.Subject -notmatch "Microsoft Corporation") {
        throw "Require the signed Microsoft ProcDump binary"
    }
    Save-CaptureState @{ binary=$taskBinary; sha256=(Get-FileHash $taskBinary -Algorithm SHA256).Hash
        captureMode=$(if ($env:BBS_PROCDUMP_POSTMORTEM -eq "1") { "postmortem" }
            elseif ($env:BBS_PROCDUMP_FIRST_CHANCE -eq "1") { "first-chance" } else { "monitor" })
        startedUtc=[DateTime]::UtcNow.ToString("o"); state="prepared"; rawDumpsUploaded=$false }
    if ($env:BBS_PROCDUMP_POSTMORTEM -eq "1") {
        & "$PSScriptRoot/windows-postmortem.ps1" prepare -Binary $taskBinary
    }
    $taskArguments = '-NoProfile -File "' + $PSCommandPath + '" watch'
    $taskWatcher = Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList $taskArguments -PassThru -WindowStyle Hidden
    $taskWatcher.Id | Set-Content $taskPidFile
    Write-Output "Prepared signed native capture and identity observation for the uniquely marked production JVM"
    exit 0
}
if (-not (Test-Path $taskState)) {
    if ($Mode -eq "watch") { throw "Missing ProcDump preparation" }
    exit 0
}
$taskMeta = Get-Content -Raw $taskState | ConvertFrom-Json -AsHashtable
if ($Mode -eq "watch") {
    $taskDeadline = [DateTime]::UtcNow.AddSeconds(330)
    try {
        do {
            $taskCandidates = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
                Where-Object { $_.CommandLine -match '(?:^|\s)-Dbbs\.ci\.production=true(?:\s|$)' })
            if ($taskCandidates.Count -gt 1) { throw "Ambiguous marked production JVMs" }
            if ($taskCandidates.Count -eq 1) { break }
            Start-Sleep -Milliseconds 200
        } while ([DateTime]::UtcNow -lt $taskDeadline)
        if ($taskCandidates.Count -ne 1) { throw "No marked production JVM observed before the monitor deadline" }
        $taskMeta.gamePid = [int]$taskCandidates[0].ProcessId
        $taskMeta.gameCommandLine = $taskCandidates[0].CommandLine
        $taskMeta.state = "observed"
        Save-CaptureState $taskMeta
        if ($taskMeta.captureMode -eq "postmortem") {
            $taskMeta.state = "observed-unattached"
            exit 0
        }
        $taskArguments = '-accepteula -mm -e -t -n 2 ' + $taskMeta.gamePid + ' "' + $taskDumpDir + '"'
        if ($taskMeta.captureMode -eq "first-chance") {
            $taskMeta.dumpLimit = 128
            $taskArguments = '-accepteula -mt -e 1 -f C0000005,C0000374 -t -n 128 ' + $taskMeta.gamePid + ' "' + $taskDumpDir + '"'
        }
        $taskMonitor = Start-Process -FilePath $taskMeta.binary -ArgumentList $taskArguments -PassThru -NoNewWindow `
            -RedirectStandardOutput (Join-Path $taskOutput "procdump.stdout.log") `
            -RedirectStandardError (Join-Path $taskOutput "procdump.stderr.log")
        $taskMeta.monitorPid = $taskMonitor.Id
        $taskMeta.state = "monitoring"
        Save-CaptureState $taskMeta
        $taskRemaining = [Math]::Max(1, ($taskDeadline - [DateTime]::UtcNow).TotalMilliseconds)
        if (-not $taskMonitor.WaitForExit([int]$taskRemaining)) {
            & $taskMeta.binary -cancel $taskMeta.gamePid | Out-Null
            if (-not $taskMonitor.WaitForExit(10000)) { throw "ProcDump did not terminate after documented cancellation" }
            $taskMeta.state = "deadline"
        } else { $taskMeta.state = "completed" }
        $taskMeta.monitorExitCode = $taskMonitor.ExitCode
    } catch {
        $taskMeta.state = "failed"
        $taskMeta.error = $_.Exception.Message
    } finally {
        $taskMeta.finishedUtc = [DateTime]::UtcNow.ToString("o")
        Save-CaptureState $taskMeta
    }
    exit 0
}
if ($taskMeta.monitorPid) {
    $taskMonitor = Get-Process -Id $taskMeta.monitorPid -ErrorAction SilentlyContinue
    if ($taskMonitor -and $taskMonitor.ProcessName -eq "procdump64") {
        if (-not $taskMonitor.WaitForExit(15000)) {
            & $taskMeta.binary -cancel $taskMeta.gamePid | Out-Null
            if (-not $taskMonitor.WaitForExit(10000)) { throw "Cannot stop the owned ProcDump monitor" }
        }
    }
}
if (Test-Path $taskPidFile) {
    $taskWatcher = Get-Process -Id ([int](Get-Content $taskPidFile)) -ErrorAction SilentlyContinue
    if ($taskWatcher -and -not $taskWatcher.WaitForExit(10000)) {
        $taskCommand = (Get-CimInstance Win32_Process -Filter "ProcessId=$($taskWatcher.Id)").CommandLine
        if ($taskCommand -notlike "*$PSCommandPath*watch*") { throw "Recorded watcher PID has a different owner" }
        $taskWatcher.Kill(); $taskWatcher.WaitForExit()
    }
}
$taskMeta = Get-Content -Raw $taskState | ConvertFrom-Json -AsHashtable
if ($taskMeta.captureMode -eq "postmortem") {
    try {
        Copy-Item $taskState (Join-Path $taskOutput "postmortem-game.json")
        if (-not $taskMeta.gamePid -or $taskMeta.state -eq "failed") {
            throw "Postmortem identity observation failed: $($taskMeta.error)"
        }
    } finally { & "$PSScriptRoot/windows-postmortem.ps1" collect }
    exit 0
}
$taskMeta.dumpFiles = @(Get-ChildItem $taskDumpDir -Filter "*.dmp" -File -ErrorAction SilentlyContinue |
    ForEach-Object { @{ name=$_.Name; bytes=$_.Length } })
foreach ($taskLogName in @("procdump.stdout.log", "procdump.stderr.log")) {
    $taskLogPath = Join-Path $taskOutput $taskLogName
    if (Test-Path $taskLogPath) {
        $taskBytes = [IO.File]::ReadAllBytes($taskLogPath)
        $taskText = if ($taskBytes -contains 0) { [Text.Encoding]::Unicode.GetString($taskBytes) }
            else { [Text.Encoding]::UTF8.GetString($taskBytes) }
        $taskText | Set-Content -Encoding utf8 (Join-Path $taskOutput ($taskLogName + ".utf8.txt"))
    }
}
Save-CaptureState $taskMeta
Copy-Item $taskState (Join-Path $taskOutput "procdump-collection.json")
if ($taskMeta.captureMode -eq "first-chance" -and $taskMeta.dumpFiles.Count -ge $taskMeta.dumpLimit) {
    throw "First-chance capture exhausted its dump limit; do not treat it as complete game coverage"
}
if (-not $taskMeta.gamePid -or $taskMeta.state -eq "failed" -or $taskMeta.dumpFiles.Count -eq 0) {
    throw "Requested native monitoring failed: $($taskMeta.error)"
}
