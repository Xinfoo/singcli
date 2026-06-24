param(
    [string] $InstallDir = "$env:ProgramFiles\singcli",

    [ValidateSet("Machine", "User")]
    [string] $PathScope = "Machine",

    [switch] $Force
)

$ErrorActionPreference = "Stop"

$Root = $PSScriptRoot
$BaseInstaller = Join-Path $Root "install-windows.ps1"
$SingBoxRoot = Join-Path $Root "sing-box"
$InstallDir = [System.IO.Path]::GetFullPath($InstallDir)
$InstalledSingBoxManifest = Join-Path $InstallDir "sing-box-files.txt"

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

if (-not (Test-Path $BaseInstaller)) {
    throw "Base Windows installer was not found: $BaseInstaller"
}

$SingBoxBundleDir = Resolve-SingBoxBundleDirectory

$baseArgs = @(
    "-InstallDir", $InstallDir,
    "-PathScope", $PathScope
)
if ($Force) {
    $baseArgs += "-Force"
}

& $BaseInstaller @baseArgs
Copy-SingBoxBundle $SingBoxBundleDir

Write-Host "sing-box bundle: $SingBoxBundleDir"
Write-Host "Installed sing-box files to: $InstallDir"
