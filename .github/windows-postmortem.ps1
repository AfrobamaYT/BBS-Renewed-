param([Parameter(Mandatory=$true)][ValidateSet("prepare","collect")][string]$Mode, [string]$Binary)
$ErrorActionPreference = "Stop"
if ($env:GITHUB_ACTIONS -ne "true" -or $env:RUNNER_OS -ne "Windows" -or $env:RUNNER_ENVIRONMENT -ne "github-hosted") {
    throw "Postmortem capture is restricted to disposable GitHub-hosted Windows runners"
}
$taskRoot = Join-Path $env:RUNNER_TEMP "bbs-postmortem"
$taskState = Join-Path $taskRoot "state.json"
$taskDumpDir = Join-Path $env:RUNNER_TEMP "bbs-native-dumps"
$taskOutput = Join-Path $env:GITHUB_WORKSPACE "build/production-diagnostics/windows"
function Save-PostmortemState($value) {
    $value | ConvertTo-Json -Depth 7 | Set-Content -Encoding utf8 $taskState
}
if ($Mode -eq "prepare") {
    if (Test-Path $taskState) { throw "Postmortem capture is already prepared" }
    if (-not (Test-Path $Binary)) { throw "Missing verified ProcDump binary" }
    New-Item -ItemType Directory -Force $taskRoot,$taskDumpDir,$taskOutput | Out-Null
    $taskKeys = @()
    foreach ($taskSuffix in @("SOFTWARE\Microsoft\Windows NT\CurrentVersion\AeDebug",
        "SOFTWARE\WOW6432Node\Microsoft\Windows NT\CurrentVersion\AeDebug")) {
        $taskReg = "HKLM\" + $taskSuffix
        $taskKey = "HKLM:\" + $taskSuffix
        $taskBackup = Join-Path $taskRoot ("aebug-" + $taskKeys.Count + ".reg")
        $taskHadKey = Test-Path $taskKey
        $taskPrior = if ($taskHadKey) { Get-ItemProperty $taskKey } else { $null }
        if ($taskHadKey) {
            & reg.exe export $taskReg $taskBackup /y | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "Cannot preserve the previous CI postmortem debugger" }
        }
        $taskKeys += @{ key=$taskKey; registry=$taskReg; hadKey=$taskHadKey; backup=$taskBackup
            previousAuto=$taskPrior.Auto; previousDebugger=$taskPrior.Debugger }
    }
    $taskMeta = @{ binary=$Binary; keys=$taskKeys; startedUtc=[DateTime]::UtcNow.ToString("o")
        controlVerified=$false; installed=$false; rawDumpsUploaded=$false }
    Save-PostmortemState $taskMeta
    & $Binary -accepteula -mm -i $taskDumpDir | Set-Content -Encoding utf8 (Join-Path $taskOutput "postmortem-install.txt")
    if ($LASTEXITCODE -ne 0) { throw "Cannot install the CI postmortem capture" }
    $taskMeta.installed = $true
    $taskInstalled = Get-ItemProperty $taskKeys[0].key
    if ($taskInstalled.Auto -ne "1" -or $taskInstalled.Debugger -notmatch [regex]::Escape($Binary)) {
        throw "Postmortem debugger registry readback does not match the requested binary"
    }
    $taskMeta.installedAuto = $taskInstalled.Auto
    $taskMeta.installedDebugger = $taskInstalled.Debugger
    Save-PostmortemState $taskMeta

    # Compile a native child with no CRT or imports; its only action is a null write.
    $taskVswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio/Installer/vswhere.exe"
    $taskCompiler = @(& $taskVswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
        -find 'VC\Tools\MSVC\**\bin\Hostx64\x64\cl.exe')
    if ($taskCompiler.Count -ne 1) { throw "Require one installed Microsoft native compiler for the capture control" }
    $taskLinker = Join-Path (Split-Path $taskCompiler[0]) "link.exe"
    $taskSource = Join-Path $PSScriptRoot "native-crash-control.c"
    $taskObject = Join-Path $taskRoot "control.obj"
    $taskControl = Join-Path $taskRoot "bbs_native_capture_control.exe"
    & $taskCompiler[0] /nologo /c /Od /GS- /Z7 /Zl ("/Fo" + $taskObject) $taskSource |
        Set-Content -Encoding utf8 (Join-Path $taskOutput "postmortem-control-compile.txt")
    if ($LASTEXITCODE -ne 0) { throw "Cannot compile the native capture control" }
    & $taskLinker /nologo /NODEFAULTLIB /ENTRY:entry /SUBSYSTEM:CONSOLE /DEBUG /MACHINE:X64 `
        ("/OUT:" + $taskControl) ("/PDB:" + (Join-Path $taskRoot "control.pdb")) $taskObject |
        Add-Content -Encoding utf8 (Join-Path $taskOutput "postmortem-control-compile.txt")
    if ($LASTEXITCODE -ne 0) { throw "Cannot link the native capture control" }
    $taskProcess = Start-Process -FilePath $taskControl -PassThru -WindowStyle Hidden
    if (-not $taskProcess.WaitForExit(45000)) {
        $taskProcess.Kill(); $taskProcess.WaitForExit()
        throw "Native postmortem control did not finish before its deadline"
    }
    $taskDeadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        $taskDumps = @(Get-ChildItem $taskDumpDir -Filter "bbs_native_capture_control*.dmp" -File)
        if ($taskDumps.Count -gt 0) { break }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $taskDeadline)
    if ($taskProcess.ExitCode -ne -1073741819 -or $taskDumps.Count -eq 0 -or $taskDumps[0].Length -eq 0) {
        throw "Native postmortem control must produce an access-violation exit and a real dump"
    }
    $taskMeta.controlVerified = $true
    $taskMeta.controlPid = $taskProcess.Id
    $taskMeta.controlExitCode = $taskProcess.ExitCode
    $taskMeta.controlSourceSha256 = (Get-FileHash $taskSource -Algorithm SHA256).Hash
    $taskMeta.controlDumps = @($taskDumps | ForEach-Object { @{ name=$_.Name; bytes=$_.Length } })
    Save-PostmortemState $taskMeta
    $taskMeta | ConvertTo-Json -Depth 7 | Set-Content -Encoding utf8 (Join-Path $taskOutput "postmortem-prepare.json")
    Write-Output "Postmortem capture fired on the owned native control; the game starts without an attached debugger"
    exit 0
}
if (-not (Test-Path $taskState)) { throw "Requested postmortem capture has no preparation state" }
$taskMeta = Get-Content -Raw $taskState | ConvertFrom-Json -AsHashtable
if ($taskMeta.restored) { exit 0 }
try {
    $taskMeta.dumps = @(Get-ChildItem $taskDumpDir -Filter "*.dmp" -File |
        ForEach-Object { @{ name=$_.Name; bytes=$_.Length } })
    if (-not $taskMeta.controlVerified) { throw "Postmortem capture control did not pass" }
}
finally {
    $taskRestoreErrors = @()
    foreach ($taskKey in $taskMeta.keys) {
        try {
            if (Test-Path $taskKey.key) { Remove-Item -Recurse -Force $taskKey.key }
            if ($taskKey.hadKey) {
                & reg.exe import $taskKey.backup | Out-Null
                if ($LASTEXITCODE -ne 0) { throw "Cannot restore the previous CI postmortem debugger" }
                $taskRestored = Get-ItemProperty $taskKey.key
                if ($taskRestored.Auto -ne $taskKey.previousAuto -or $taskRestored.Debugger -ne $taskKey.previousDebugger) {
                    throw "Restored postmortem debugger differs from the original readback"
                }
            } elseif (Test-Path $taskKey.key) { throw "New postmortem debugger key remains after cleanup" }
        } catch {
            $taskRestoreErrors += $_.Exception.Message
        }
    }
    $taskMeta.restored = $taskRestoreErrors.Count -eq 0
    $taskMeta.restoreErrors = $taskRestoreErrors
    Save-PostmortemState $taskMeta
    $taskMeta | ConvertTo-Json -Depth 7 | Set-Content -Encoding utf8 (Join-Path $taskOutput "postmortem-collection.json")
    if ($taskRestoreErrors.Count -gt 0) { throw ($taskRestoreErrors -join "; ") }
}
