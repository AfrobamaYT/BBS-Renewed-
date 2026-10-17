$ErrorActionPreference = "Stop"
if ($IsWindows) { throw "Run this negative control on the Linux runner" }
$taskRejected = $false
try { & "$PSScriptRoot/windows-postmortem.ps1" collect }
catch {
    if ($_.Exception.Message -ne "Postmortem capture is restricted to disposable GitHub-hosted Windows runners") { throw }
    $taskRejected = $true
}
if (-not $taskRejected) { throw "Postmortem capture did not reject this non-Windows host" }
Write-Output "Postmortem host guard rejected before any native configuration access"
