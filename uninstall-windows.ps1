param(
    [string] $InstallDir = "$env:ProgramFiles\singcli",

    [ValidateSet("Machine", "User")]
    [string] $PathScope = "Machine"
)

$ErrorActionPreference = "Stop"

$InstallDir = [System.IO.Path]::GetFullPath($InstallDir)
$InstalledSingBoxManifest = Join-Path $InstallDir "sing-box-files.txt"

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Restart-AsAdministratorIfNeeded {
    if ($PathScope -ne "Machine" -or (Test-IsAdministrator)) {
        return
    }

    $arguments = @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", "`"$PSCommandPath`"",
        "-InstallDir", "`"$InstallDir`"",
        "-PathScope", $PathScope
    )

    Write-Host "Requesting administrator privileges for Machine PATH uninstall..."
    $process = Start-Process -FilePath "powershell.exe" -ArgumentList $arguments -Verb RunAs -Wait -PassThru
    exit $process.ExitCode
}

function Normalize-PathForCompare([string] $Path) {
    return [System.IO.Path]::GetFullPath($Path).TrimEnd('\')
}

function Send-EnvironmentChangeNotification {
    $signature = @"
using System;
using System.Runtime.InteropServices;

public static class EnvironmentChangeNotification {
    [DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern IntPtr SendMessageTimeout(
        IntPtr hWnd,
        int Msg,
        UIntPtr wParam,
        string lParam,
        int fuFlags,
        int uTimeout,
        out UIntPtr lpdwResult);
}
"@
    Add-Type -TypeDefinition $signature
    $result = [UIntPtr]::Zero
    [EnvironmentChangeNotification]::SendMessageTimeout(
        [IntPtr] 0xffff,
        0x001a,
        [UIntPtr]::Zero,
        "Environment",
        0x0002,
        5000,
        [ref] $result
    ) | Out-Null
}

function Remove-InstallDirFromPath {
    $currentPath = [Environment]::GetEnvironmentVariable("Path", $PathScope)
    if ($currentPath) {
        $normalizedInstallDir = Normalize-PathForCompare $InstallDir
        $entries = $currentPath -split ';' | Where-Object {
            $_ -and $_.Trim() -and ((Normalize-PathForCompare $_) -ne $normalizedInstallDir)
        }
        [Environment]::SetEnvironmentVariable("Path", ($entries -join ';'), $PathScope)
    }

    if ($env:Path) {
        $normalizedInstallDir = Normalize-PathForCompare $InstallDir
        $sessionEntries = $env:Path -split ';' | Where-Object {
            $_ -and $_.Trim() -and ((Normalize-PathForCompare $_) -ne $normalizedInstallDir)
        }
        $env:Path = $sessionEntries -join ';'
    }

    Send-EnvironmentChangeNotification
}

Restart-AsAdministratorIfNeeded

Remove-InstallDirFromPath

$installedFiles = @(
    (Join-Path $InstallDir "singcli.jar"),
    (Join-Path $InstallDir "singcli.cmd")
)

if (Test-Path $InstalledSingBoxManifest) {
    $singBoxFiles = Get-Content $InstalledSingBoxManifest | Where-Object { $_ -and $_.Trim() }
    foreach ($fileName in $singBoxFiles) {
        $file = Join-Path $InstallDir $fileName
        if (Test-Path $file) {
            Remove-Item $file -Force
        }
    }
    Remove-Item $InstalledSingBoxManifest -Force
}

foreach ($file in $installedFiles) {
    if (Test-Path $file) {
        Remove-Item $file -Force
    }
}

$installedUninstaller = Join-Path $InstallDir "uninstall-windows.ps1"
$runningScript = $PSCommandPath
if ((Test-Path $installedUninstaller) -and ((Normalize-PathForCompare $installedUninstaller) -ne (Normalize-PathForCompare $runningScript))) {
    Remove-Item $installedUninstaller -Force
}

if (Test-Path $InstallDir) {
    $remaining = Get-ChildItem -Path $InstallDir -Force -ErrorAction SilentlyContinue
    if (-not $remaining) {
        Remove-Item $InstallDir -Force
    }
}

Write-Host "Uninstalled singcli from: $InstallDir"
Write-Host "PATH scope: $PathScope"
if (Test-Path $installedUninstaller) {
    Write-Host "The running uninstaller remains and can be deleted manually: $installedUninstaller"
}
Write-Host "Open a new terminal before checking the singcli command."
