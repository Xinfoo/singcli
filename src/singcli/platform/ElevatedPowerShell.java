package singcli.platform;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

// 在独立的管理员 PowerShell 窗口中执行需要 UAC 授权的脚本。
public final class ElevatedPowerShell {
    private ElevatedPowerShell() {
    }

    public static void run(String script) throws Exception {
        String launcherScript = """
                $ErrorActionPreference = 'Stop'
                [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
                try {
                    $arguments = @(
                        '-NoLogo',
                        '-NoProfile',
                        '-NonInteractive',
                        '-ExecutionPolicy', 'Bypass',
                        '-EncodedCommand', '%s'
                    )
                    $process = Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList $arguments -Wait -PassThru
                    exit $process.ExitCode
                }
                catch {
                    [Console]::Error.WriteLine($_.Exception.Message)
                    exit 1
                }
                """.formatted(encodedCommand(script));

        ProcessBuilder builder = new ProcessBuilder(List.of(
                "powershell.exe",
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-EncodedCommand",
                encodedCommand(launcherScript)
        ));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(output.isEmpty()
                    ? "Elevated PowerShell exited with code " + exitCode
                    : output);
        }
    }

    // PowerShell 的 EncodedCommand 要求先编码为 UTF-16LE，再转换为 Base64。
    private static String encodedCommand(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }
}
