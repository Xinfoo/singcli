param(
    [string] $InstallDir = "$env:ProgramFiles\singcli",

    [ValidateSet("Machine", "User")]
    [string] $PathScope = "Machine",

    [switch] $Force
)

$ErrorActionPreference = "Stop"

$Root = $PSScriptRoot
$Jar = Join-Path $Root "dist\singcli.jar"
$Launcher = Join-Path $Root "scripts\windows\singcli.cmd"
$InstallDir = [System.IO.Path]::GetFullPath($InstallDir)
$InstalledJar = Join-Path $InstallDir "singcli.jar"
$InstalledLauncher = Join-Path $InstallDir "singcli.cmd"

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Normalize-PathForCompare([string] $Path) {
    return [System.IO.Path]::GetFullPath($Path).TrimEnd('\')
}

function Assert-NoSingcliCommandConflict {
    $normalizedInstallDir = Normalize-PathForCompare $InstallDir
    $commands = Get-Command singcli -All -ErrorAction SilentlyContinue
    foreach ($command in $commands) {
        if ($command.CommandType -ne "Application") {
            throw "A non-application singcli command already exists in PowerShell: $($command.CommandType) $($command.Name). Remove it or use -Force."
        }
        if (-not $command.Source) {
            continue
        }
        $source = Normalize-PathForCompare $command.Source
        $sourceDir = Normalize-PathForCompare (Split-Path -Parent $source)
        if ($sourceDir -ne $normalizedInstallDir) {
            throw "A different singcli command already exists in PATH: $source. Use -Force only if you intentionally want $InstallDir earlier in PATH."
        }
    }
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

function Add-InstallDirToPath {
    $currentPath = [Environment]::GetEnvironmentVariable("Path", $PathScope)
    $entries = @()
    if ($currentPath) {
        $entries = $currentPath -split ';' | Where-Object { $_ -and $_.Trim() }
    }

    $normalizedInstallDir = Normalize-PathForCompare $InstallDir
    $alreadyPresent = $false
    foreach ($entry in $entries) {
        if ((Normalize-PathForCompare $entry) -eq $normalizedInstallDir) {
            $alreadyPresent = $true
            break
        }
    }

    if (-not $alreadyPresent) {
        $newPath = if ($currentPath) { "$InstallDir;$currentPath" } else { $InstallDir }
        [Environment]::SetEnvironmentVariable("Path", $newPath, $PathScope)
    }

    $sessionEntries = $env:Path -split ';' | Where-Object { $_ -and $_.Trim() }
    $sessionHasInstallDir = $false
    foreach ($entry in $sessionEntries) {
        if ((Normalize-PathForCompare $entry) -eq $normalizedInstallDir) {
            $sessionHasInstallDir = $true
            break
        }
    }
    if (-not $sessionHasInstallDir) {
        $env:Path = "$InstallDir;$env:Path"
    }

    Send-EnvironmentChangeNotification
}

if ($PathScope -eq "Machine" -and -not (Test-IsAdministrator)) {
    throw "Machine PATH installation requires an elevated PowerShell session. Re-run as Administrator, or use -PathScope User."
}

if (-not $Force) {
    Assert-NoSingcliCommandConflict
}

if (-not (Test-Path $Jar)) {
    throw "Prebuilt jar was not found: $Jar. Put singcli.jar in the dist directory before running this installer."
}

if (-not (Test-Path $Launcher)) {
    throw "Windows launcher was not found: $Launcher"
}

New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
Copy-Item $Jar $InstalledJar -Force
Copy-Item $Launcher $InstalledLauncher -Force

Add-InstallDirToPath

Write-Host "Installed singcli to: $InstallDir"
Write-Host "Launcher: $InstalledLauncher"
Write-Host "Jar: $InstalledJar"
Write-Host "PATH scope: $PathScope"
Write-Host "Open a new terminal and run: singcli help"
