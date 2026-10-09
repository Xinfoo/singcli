package singcli.commands;

import singcli.platform.ElevatedPowerShell;
import singcli.platform.KdeSystemProxy;
import singcli.process.SingBoxProcessManager;

// 系统代理取消命令：关闭 Windows 或 Linux KDE 当前用户的系统代理。
public final class UnsetSystemProxy {
    private UnsetSystemProxy() {
    }

    public static int run(String[] args) {
        if (!SingBoxProcessManager.isWindows() && !SingBoxProcessManager.isLinux()) {
            System.err.println("Unset system proxy is only supported on Windows and Linux KDE (kwriteconfig6).");
            return 1;
        }

        try {
            if (SingBoxProcessManager.isWindows()) {
                applyWindowsProxyUnset();
                System.out.println("Windows system proxy disabled.");
            } else {
                KdeSystemProxy.unset();
                System.out.println("Linux KDE system proxy disabled.");
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Unset system proxy failed: " + SingBoxProcessManager.errorMessage(e));
            return 1;
        }
    }

    // 通过管理员 PowerShell 修改注册表，并调用 WinInet 的 InternetSetOptionW 刷新系统代理。
    private static void applyWindowsProxyUnset() throws Exception {
        String script = """
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$path = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings'
if (-not (Test-Path -LiteralPath $path)) {
    New-Item -Path $path | Out-Null
}
New-ItemProperty -LiteralPath $path -Name 'ProxyEnable' -Value 0 -PropertyType DWord -Force | Out-Null
New-ItemProperty -LiteralPath $path -Name 'AutoDetect' -Value 0 -PropertyType DWord -Force | Out-Null
$autoConfigUrl = Get-ItemProperty -LiteralPath $path -Name 'AutoConfigURL' -ErrorAction SilentlyContinue
if ($null -ne $autoConfigUrl) {
    Remove-ItemProperty -LiteralPath $path -Name 'AutoConfigURL'
}
$signature = @'
using System;
using System.Runtime.InteropServices;

public static class WinInetProxyRefresh {
    [DllImport("wininet.dll", EntryPoint = "InternetSetOptionW", ExactSpelling = true, SetLastError = true)]
    public static extern bool InternetSetOptionW(IntPtr hInternet, int dwOption, IntPtr lpBuffer, int dwBufferLength);
}
'@
Add-Type -TypeDefinition $signature
if (-not [WinInetProxyRefresh]::InternetSetOptionW([IntPtr]::Zero, 39, [IntPtr]::Zero, 0)) {
    throw "InternetSetOptionW(INTERNET_OPTION_SETTINGS_CHANGED) failed: $([Runtime.InteropServices.Marshal]::GetLastWin32Error())"
}
if (-not [WinInetProxyRefresh]::InternetSetOptionW([IntPtr]::Zero, 37, [IntPtr]::Zero, 0)) {
    throw "InternetSetOptionW(INTERNET_OPTION_REFRESH) failed: $([Runtime.InteropServices.Marshal]::GetLastWin32Error())"
}
""";

        ElevatedPowerShell.run(script);
    }
}
