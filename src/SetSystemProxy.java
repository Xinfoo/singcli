import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

// Windows 系统代理设置命令：读取 singcli 配置中的本地代理地址并写入当前用户注册表。
public class SetSystemProxy {
    private static final Path CONFIG_PATH = AppPaths.configPath();

    public static void main(String[] args) {
        if (!ProcessSupport.isWindows()) {
            System.err.println("Set system proxy is only supported on Windows.");
            System.exit(1);
        }

        try {
            if (!Files.exists(CONFIG_PATH)) {
                throw new IllegalArgumentException("config.json was not found: " + CONFIG_PATH.toAbsolutePath().normalize());
            }

            String config = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            String proxyAddress = ConfigSupport.localProxyAddress(config);
            applyWindowsProxy(proxyAddress);
            System.out.println("Windows system proxy enabled: " + proxyAddress);
        } catch (Exception e) {
            System.err.println("Set system proxy failed: " + errorMessage(e));
            System.exit(1);
        }
    }

    // 通过 PowerShell 写入注册表，并调用 WinInet 的 InternetSetOptionW 刷新系统代理。
    private static void applyWindowsProxy(String proxyAddress) throws Exception {
        String script = """
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$path = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings'
New-Item -Path $path -Force | Out-Null
Remove-ItemProperty -Path $path -Name 'ProxyServer' -ErrorAction SilentlyContinue
Remove-ItemProperty -Path $path -Name 'ProxyOverride' -ErrorAction SilentlyContinue
Remove-ItemProperty -Path $path -Name 'AutoDetect' -ErrorAction SilentlyContinue
Remove-ItemProperty -Path $path -Name 'AutoConfigURL' -ErrorAction SilentlyContinue
Remove-ItemProperty -Path $path -Name 'ProxyEnable' -ErrorAction SilentlyContinue
New-ItemProperty -Path $path -Name 'ProxyServer' -Value '%s' -PropertyType String | Out-Null
New-ItemProperty -Path $path -Name 'ProxyOverride' -Value '<local>' -PropertyType String | Out-Null
New-ItemProperty -Path $path -Name 'AutoDetect' -Value 0 -PropertyType DWord | Out-Null
New-ItemProperty -Path $path -Name 'ProxyEnable' -Value 1 -PropertyType DWord | Out-Null
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

    // PowerShell 单引号字符串内部用两个单引号表示一个单引号。
    private static String powershellSingleQuoted(String value) {
        return value.replace("'", "''");
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
