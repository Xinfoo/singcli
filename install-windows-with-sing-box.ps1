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
$Uninstaller = Join-Path $Root "uninstall-windows.ps1"
$SingBoxRoot = Join-Path $Root "sing-box"
$InstallDir = [System.IO.Path]::GetFullPath($InstallDir)
$InstalledJar = Join-Path $InstallDir "singcli.jar"
$InstalledLauncher = Join-Path $InstallDir "singcli.cmd"
$InstalledUninstaller = Join-Path $InstallDir "uninstall-windows.ps1"
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
    if ($Force) {
        $arguments += "-Force"
    }

    Write-Host "Requesting administrator privileges for Machine PATH installation..."
    $process = Start-Process -FilePath "powershell.exe" -ArgumentList $arguments -Verb RunAs -Wait -PassThru
    exit $process.ExitCode
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

function Resolve-SingBoxBundleDirectory {
    if (-not (Test-Path $SingBoxRoot)) {
        throw "Windows sing-box directory was not found: $SingBoxRoot"
    }

    $executables = @(Get-ChildItem -Path $SingBoxRoot -Filter "sing-box.exe" -File -Recurse)
    if ($executables.Count -eq 0) {
        throw "Windows sing-box executable was not found under: $SingBoxRoot"
    }
    if ($executables.Count -gt 1) {
        $paths = ($executables | ForEach-Object { $_.FullName }) -join [Environment]::NewLine
        throw "Multiple Windows sing-box executables were found. Keep only one bundle under sing-box:$([Environment]::NewLine)$paths"
    }

    return $executables[0].Directory.FullName
}

function Copy-SingBoxBundle([string] $BundleDir) {
    $files = @()
    $files += Get-Item (Join-Path $BundleDir "sing-box.exe")
    $files += Get-ChildItem -Path $BundleDir -Filter "*.dll" -File

    foreach ($file in $files) {
        Copy-Item $file.FullName (Join-Path $InstallDir $file.Name) -Force
    }

    $files | ForEach-Object { $_.Name } | Set-Content -Path $InstalledSingBoxManifest -Encoding UTF8
}

Restart-AsAdministratorIfNeeded

if (-not $Force) {
    Assert-NoSingcliCommandConflict
}

if (-not (Test-Path $Jar)) {
    throw "Prebuilt jar was not found: $Jar. Put singcli.jar in the dist directory before running this installer."
}

if (-not (Test-Path $Launcher)) {
    throw "Windows launcher was not found: $Launcher"
}

if (-not (Test-Path $Uninstaller)) {
    throw "Windows uninstaller was not found: $Uninstaller"
}

$SingBoxBundleDir = Resolve-SingBoxBundleDirectory

New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
Copy-Item $Jar $InstalledJar -Force
Copy-Item $Launcher $InstalledLauncher -Force
Copy-Item $Uninstaller $InstalledUninstaller -Force
Add-InstallDirToPath
Copy-SingBoxBundle $SingBoxBundleDir

Write-Host "Installed singcli to: $InstallDir"
Write-Host "Launcher: $InstalledLauncher"
Write-Host "Jar: $InstalledJar"
Write-Host "Uninstaller: $InstalledUninstaller"
Write-Host "PATH scope: $PathScope"
Write-Host "sing-box bundle: $SingBoxBundleDir"
Write-Host "Installed sing-box files to: $InstallDir"
Write-Host "Open a new terminal and run: singcli help"
