import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

// Windows 系统代理取消命令：关闭当前用户系统代理并清理自动配置 URL。
class UnsetSystemProxy {
    static int run(String[] args) {
        if (!ProcessSupport.isWindows()) {
            System.err.println("Unset system proxy is only supported on Windows.");
            return 1;
        }

        try {
            applyWindowsProxyUnset();
            System.out.println("Windows system proxy disabled.");
            return 0;
        } catch (Exception e) {
            System.err.println("Unset system proxy failed: " + errorMessage(e));
            return 1;
        }
    }

    // 通过 PowerShell 修改注册表，并调用 WinInet 的 InternetSetOptionW 刷新系统代理。
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

        ProcessBuilder builder = new ProcessBuilder(List.of(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-EncodedCommand",
                encodedPowerShellCommand(script)
        ));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(output.isEmpty() ? "PowerShell exited with code " + exitCode : output);
        }
    }

    // PowerShell 的 EncodedCommand 要求使用 UTF-16LE 后再 Base64。
    private static String encodedPowerShellCommand(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    private static String errorMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
