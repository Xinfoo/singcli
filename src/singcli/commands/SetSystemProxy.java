package singcli.commands;

import singcli.config.SingBoxConfig;
import singcli.platform.AppPaths;
import singcli.platform.ElevatedPowerShell;
import singcli.process.SingBoxProcessManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Windows 系统代理设置命令：读取 singcli 配置中的本地代理地址并写入当前用户注册表。
public class SetSystemProxy {
    private static final Path CONFIG_PATH = AppPaths.configPath();

    public static int run(String[] args) {
        if (!SingBoxProcessManager.isWindows()) {
            System.err.println("Set system proxy is only supported on Windows.");
            return 1;
        }

        try {
            if (!Files.exists(CONFIG_PATH)) {
                throw new IllegalArgumentException("config.json was not found: " + CONFIG_PATH.toAbsolutePath().normalize());
            }

            String config = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            String proxyAddress = SingBoxConfig.localProxyAddress(config);
            applyWindowsProxy(proxyAddress);
            System.out.println("Windows system proxy enabled: " + proxyAddress);
            return 0;
        } catch (Exception e) {
            System.err.println("Set system proxy failed: " + SingBoxProcessManager.errorMessage(e));
            return 1;
        }
    }

    // 通过管理员 PowerShell 写入注册表，并调用 WinInet 的 InternetSetOptionW 刷新系统代理。
    private static void applyWindowsProxy(String proxyAddress) throws Exception {
        String script = """
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$path = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings'
if (-not (Test-Path -LiteralPath $path)) {
    New-Item -Path $path | Out-Null
}
New-ItemProperty -LiteralPath $path -Name 'ProxyServer' -Value '%s' -PropertyType String -Force | Out-Null
New-ItemProperty -LiteralPath $path -Name 'ProxyOverride' -Value '<local>' -PropertyType String -Force | Out-Null
New-ItemProperty -LiteralPath $path -Name 'AutoDetect' -Value 0 -PropertyType DWord -Force | Out-Null
New-ItemProperty -LiteralPath $path -Name 'ProxyEnable' -Value 1 -PropertyType DWord -Force | Out-Null
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
""".formatted(powershellSingleQuoted(proxyAddress));

        ElevatedPowerShell.run(script);
    }

    // PowerShell 单引号字符串内部用两个单引号表示一个单引号。
    private static String powershellSingleQuoted(String value) {
        return value.replace("'", "''");
    }
}
