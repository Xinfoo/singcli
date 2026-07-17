package singcli.commands;

import singcli.platform.ElevatedPowerShell;
import singcli.process.SingBoxProcessManager;

// Windows 系统代理取消命令：关闭当前用户系统代理并清理自动配置 URL。
public class UnsetSystemProxy {
    public static int run(String[] args) {
        if (!SingBoxProcessManager.isWindows()) {
            System.err.println("Unset system proxy is only supported on Windows.");
            return 1;
        }

        try {
            applyWindowsProxyUnset();
            System.out.println("Windows system proxy disabled.");
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
New-Item -Path $path -Force | Out-Null
Remove-ItemProperty -Path $path -Name 'ProxyEnable' -ErrorAction SilentlyContinue
New-ItemProperty -Path $path -Name 'ProxyEnable' -Value 0 -PropertyType DWord | Out-Null
Remove-ItemProperty -Path $path -Name 'AutoDetect' -ErrorAction SilentlyContinue
New-ItemProperty -Path $path -Name 'AutoDetect' -Value 0 -PropertyType DWord | Out-Null
if ($null -ne (Get-ItemProperty -Path $path -Name 'AutoConfigURL' -ErrorAction SilentlyContinue)) {
    Remove-ItemProperty -Path $path -Name 'AutoConfigURL' -ErrorAction SilentlyContinue
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
