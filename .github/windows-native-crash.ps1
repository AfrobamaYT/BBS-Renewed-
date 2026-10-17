param([Parameter(Mandatory=$true)][ValidateSet("prepare","collect")][string]$Mode)
$ErrorActionPreference = "Stop"
if ($env:GITHUB_ACTIONS -ne "true" -or $env:RUNNER_OS -ne "Windows" -or $env:RUNNER_ENVIRONMENT -ne "github-hosted") {
    throw "Native crash collection is restricted to disposable GitHub-hosted Windows runners"
}
$taskDumpDir = Join-Path $env:RUNNER_TEMP "bbs-native-dumps"
$taskState = Join-Path $env:RUNNER_TEMP "bbs-native-dumps-state.json"
$taskOutput = Join-Path $env:GITHUB_WORKSPACE "build/production-diagnostics/windows"
$taskKey = "HKLM:\SOFTWARE\Microsoft\Windows\Windows Error Reporting\LocalDumps\java.exe"
$taskRegKey = "HKLM\SOFTWARE\Microsoft\Windows\Windows Error Reporting\LocalDumps\java.exe"
New-Item -ItemType Directory -Force $taskOutput | Out-Null
if ($Mode -eq "prepare") {
    New-Item -ItemType Directory -Force $taskDumpDir | Out-Null
    $taskHadKey = Test-Path $taskKey
    $taskBackup = Join-Path $env:RUNNER_TEMP "bbs-previous-localdumps.reg"
    if ($taskHadKey) {
        & reg.exe export $taskRegKey $taskBackup /y | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Cannot preserve existing CI dump configuration" }
    }
    @{ hadKey=$taskHadKey; backup=$taskBackup; startedUtc=[DateTime]::UtcNow.ToString("o") } |
        ConvertTo-Json | Set-Content -Encoding utf8 $taskState
    New-Item -Force $taskKey | Out-Null
    New-ItemProperty -Force $taskKey -Name DumpFolder -PropertyType ExpandString -Value $taskDumpDir | Out-Null
    New-ItemProperty -Force $taskKey -Name DumpType -PropertyType DWord -Value 1 | Out-Null
    New-ItemProperty -Force $taskKey -Name DumpCount -PropertyType DWord -Value 2 | Out-Null
    Write-Output "Enabled local Java minidumps in disposable CI; raw dumps are not uploaded"
    exit 0
}
if (-not (Test-Path $taskState)) {
    @{ prepared=$false } | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $taskOutput "collection.json")
    exit 0
}
$taskSaved = Get-Content -Raw $taskState | ConvertFrom-Json
try {
    Start-Sleep -Seconds 2
    $taskEvents = @(Get-WinEvent -FilterHashtable @{
        LogName="Application"; Id=1000,1001; StartTime=([DateTime]::Parse($taskSaved.startedUtc)).ToLocalTime()
    } -ErrorAction SilentlyContinue | Where-Object { $_.Message -match "java\.exe" })
    $taskEvents | ForEach-Object { $_.ToXml() } |
        Set-Content -Encoding utf8 (Join-Path $taskOutput "java-application-events.xml")
    $taskCdb = Join-Path ${env:ProgramFiles(x86)} "Windows Kits/10/Debuggers/x64/cdb.exe"
    $taskDumps = @(Get-ChildItem $taskDumpDir -Filter "*.dmp" -File -ErrorAction SilentlyContinue)
    $taskStacks = @()
    foreach ($taskDump in $taskDumps) {
        if (-not (Test-Path $taskCdb)) { break }
        $taskStack = Join-Path $taskOutput ($taskDump.BaseName + "-stack.txt")
        $taskArguments = '-z "' + $taskDump.FullName + '" -logo "' + $taskStack + '" -c ".symfix; .ecxr; kv; q"'
        $taskProcess = Start-Process -FilePath $taskCdb -ArgumentList $taskArguments -PassThru
        if (-not $taskProcess.WaitForExit(45000)) { $taskProcess.Kill(); $taskProcess.WaitForExit() }
        $taskStacks += @{ file=[IO.Path]::GetFileName($taskStack); exitCode=$taskProcess.ExitCode }
    }
    @{
        prepared=$true; cdbAvailable=(Test-Path $taskCdb); applicationEvents=$taskEvents.Count
        dumps=@($taskDumps | ForEach-Object { @{ name=$_.Name; bytes=$_.Length } })
        stacks=$taskStacks; rawDumpsUploaded=$false
    } | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 (Join-Path $taskOutput "collection.json")
}
finally {
    if (Test-Path $taskKey) { Remove-Item -Recurse -Force $taskKey }
    if ($taskSaved.hadKey) {
        & reg.exe import $taskSaved.backup | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Cannot restore the previous CI dump configuration" }
    }
}
