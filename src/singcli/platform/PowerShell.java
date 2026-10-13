package singcli.platform;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// 在当前用户身份下同步执行一次 PowerShell 脚本，并把失败原因返回给命令层。
final class PowerShell {
    private PowerShell() {
    }

    static void run(String script) throws Exception {
        String wrappedScript = """
                $ErrorActionPreference = 'Stop'
                [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
                try {
                """ + script + """

                }
                catch {
                    [Console]::Error.WriteLine($_.Exception.ToString())
                    exit 1
                }
                """;
        // EncodedCommand 要求 UTF-16LE；脚本作为单独参数传入，不经过 shell 拼接。
        String encoded = Base64.getEncoder().encodeToString(wrappedScript.getBytes(StandardCharsets.UTF_16LE));
        Process process = new ProcessBuilder(
                "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(output.isEmpty()
                    ? "PowerShell exited with code " + exitCode
                    : output);
        }
    }
}
