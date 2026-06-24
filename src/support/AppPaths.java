import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;

// 统一管理 singcli 自己使用的路径，避免各个命令分别硬编码 config.json 位置。
final class AppPaths {
    private static final String APP_DIR_NAME = "singcli";
    private static final String CONFIG_FILE_NAME = "config.json";

    private AppPaths() {
    }

    // 返回当前平台下最终使用的配置文件路径。
    static Path configPath() {
        return configDirectory().resolve(CONFIG_FILE_NAME);
    }

    // Linux 使用 XDG 配置目录，Windows 使用 APPDATA 下的 local/singcli。
    static Path configDirectory() {
        if (ProcessSupport.isWindows()) {
            return windowsConfigDirectory();
        }
        return linuxConfigDirectory();
    }

    // 返回当前程序的安装目录。jar 运行时取 jar 所在目录，开发环境运行 class 时取 class 输出目录。
    static Path installationDirectory() {
        CodeSource codeSource = AppPaths.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        }
        try {
            Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(location)) {
                Path parent = location.getParent();
                return parent == null ? location : parent;
            }
            return location;
        } catch (URISyntaxException e) {
            return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        }
    }

    // 优先遵循 XDG_CONFIG_HOME；未设置时使用 Linux 常见默认值 ~/.config。
    private static Path linuxConfigDirectory() {
        String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
        if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
            return Path.of(xdgConfigHome, APP_DIR_NAME);
        }
        return Path.of(System.getProperty("user.home"), ".config", APP_DIR_NAME);
    }

    // 按需求把配置放到 %APPDATA%\local\singcli；APPDATA 不存在时给出保守 fallback。
    private static Path windowsConfigDirectory() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "local", APP_DIR_NAME);
        }
        return Path.of(System.getProperty("user.home"), "AppData", "Roaming", "local", APP_DIR_NAME);
    }
}
