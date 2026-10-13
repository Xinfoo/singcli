package singcli.commands;

import singcli.app.AppLog;
import singcli.config.SingBoxConfig;
import singcli.platform.AppPaths;
import singcli.platform.KdeSystemProxy;
import singcli.platform.WindowsSystemProxy;
import singcli.process.SingBoxProcessManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// 系统代理设置命令：读取本地代理地址，通过 Windows API 或 Linux KDE 配置启用代理。
public final class SetSystemProxy {
    private static final Path CONFIG_PATH = AppPaths.configPath();

    private SetSystemProxy() {
    }

    public static int run(String[] args) {
        if (!SingBoxProcessManager.isWindows() && !SingBoxProcessManager.isLinux()) {
            AppLog.failure("system_proxy.unsupported", "os", System.getProperty("os.name"));
            System.err.println("Set system proxy is only supported on Windows and Linux KDE (kwriteconfig6).");
            return 1;
        }

        try {
            if (!Files.exists(CONFIG_PATH)) {
                throw new IllegalArgumentException("config.json was not found: " + CONFIG_PATH.toAbsolutePath().normalize());
            }

            String config = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            String proxyAddress = SingBoxConfig.localProxyAddress(config);
            AppLog.info("system_proxy.enable.requested", "address", proxyAddress,
                    "platform", SingBoxProcessManager.isWindows() ? "Windows" : "KDE6");
            if (SingBoxProcessManager.isWindows()) {
                WindowsSystemProxy.set(proxyAddress);
                System.out.println("Windows system proxy enabled: " + proxyAddress);
            } else {
                KdeSystemProxy.set(proxyAddress);
                System.out.println("Linux KDE system proxy enabled: " + proxyAddress);
            }
            AppLog.info("system_proxy.enabled", "address", proxyAddress);
            return 0;
        } catch (Exception e) {
            AppLog.error("system_proxy.enable.failed", e);
            System.err.println("Set system proxy failed: " + SingBoxProcessManager.errorMessage(e));
            return 1;
        }
    }
}
