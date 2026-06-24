import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Scanner;

class ConfigGet {
    // 配置文件不再写入当前工作目录，而是写入 AppPathsSupport 计算出的平台配置目录。
    private static final Path CONFIG_PATH = AppPathsSupport.configPath();

    static int run(String[] args) {
        // 读取用户输入的订阅或配置地址，并把下载、规范化、写文件放在同一个错误处理块中。
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            System.out.print("Enter the config URL: ");
            String url = scanner.nextLine().trim();
            // URL 为空时没有可下载对象，直接作为参数错误处理。
            if (url.isEmpty()) {
                throw new IllegalArgumentException("URL cannot be empty");
            }

            // 下载原始配置，补齐本工具需要的入站和 Clash API，再写入本地配置文件。
            String originalConfig = downloadText(url);
            String updatedConfig = ConfigSupport.normalizeConfig(originalConfig);
            // 首次运行时配置目录可能还不存在，需要先创建目录。
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, updatedConfig, StandardCharsets.UTF_8);

            System.out.println("Config file written to: " + CONFIG_PATH.toAbsolutePath().normalize());
            return 0;
        } catch (Exception e) {
            System.err.println("Generation failed: " + e.getMessage());
            return 1;
        }
    }

    // 使用 JDK URLConnection 下载文本内容，并设置连接和读取超时，避免请求无限等待。
    private static String downloadText(String urlText) throws Exception {
        URLConnection connection = URI.create(urlText).toURL().openConnection();
        connection.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
        connection.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        // 一次性读取响应体，按 UTF-8 作为 JSON 文本返回。
        try (InputStream input = connection.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
