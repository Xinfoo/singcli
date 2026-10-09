package singcli.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Linux KDE 系统代理：只通过 kwriteconfig6 写入当前用户的 kioslaverc。
public final class KdeSystemProxy {
    private static final String DEFAULT_BYPASS =
            "localhost,127.0.0.1,192.168.0.0/16,10.0.0.0/8,172.16.0.0/12,::1";

    private KdeSystemProxy() {
    }

    public static void set(String proxyAddress) throws IOException, InterruptedException {
        write("httpProxy", "http://" + proxyAddress);
        write("httpsProxy", "http://" + proxyAddress);
        write("socksProxy", "socks://" + proxyAddress);
        write("NoProxyFor", DEFAULT_BYPASS);
        // 地址写入成功后启用手动代理；不保存或恢复原来的设置。
        write("ProxyType", "1");
    }

    public static void unset() throws IOException, InterruptedException {
        // 关闭代理模式，保留已写入的地址和绕过列表。
        write("ProxyType", "0");
    }

    private static void write(String key, String value) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "kwriteconfig6", "--file", "kioslaverc", "--group", "Proxy Settings",
                "--key", key, value)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("kwriteconfig6 failed to write " + key + " (exit " + exitCode + ")"
                    + (output.isEmpty() ? "" : ": " + output));
        }
    }
}
