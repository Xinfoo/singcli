package singcli.commands;

import singcli.app.AppLog;
import singcli.app.ConsoleInput;
import singcli.config.SingBoxConfig;
import singcli.platform.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Scanner;

import javax.net.ssl.SSLException;

public final class ConfigGet {
    // 配置文件不再写入当前工作目录，而是写入 AppPaths 计算出的平台配置目录。
    private static final Path CONFIG_PATH = AppPaths.configPath();

    private ConfigGet() {
    }

    public static int run(String[] args) {
        String stage = "input";
        String url = "";
        // 读取用户输入的订阅或配置地址，并把下载、规范化、写文件放在同一个错误处理块中。
        try {
            Scanner scanner = ConsoleInput.scanner();
            AppLog.info("config.url.requested");
            System.out.print("Enter the config URL: ");
            url = scanner.nextLine().trim();
            // URL 为空时没有可下载对象，直接作为参数错误处理。
            if (url.isEmpty()) {
                throw new IllegalArgumentException("URL cannot be empty");
            }

            // 下载原始配置，补齐本工具需要的入站和 Clash API，再写入本地配置文件。
            stage = "download";
            String originalConfig = downloadText(url);
            stage = "normalize";
            AppLog.info("config.normalization.started");
            String updatedConfig = SingBoxConfig.normalizeConfig(originalConfig);
            AppLog.info("config.normalization.completed");
            // 首次运行时配置目录可能还不存在，需要先创建目录。
            stage = "write";
            AppLog.info("config.write.started", "path", CONFIG_PATH.toAbsolutePath().normalize());
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, updatedConfig, StandardCharsets.UTF_8);
            AppLog.info("config.write.completed", "path", CONFIG_PATH.toAbsolutePath().normalize(),
                    "bytes", updatedConfig.getBytes(StandardCharsets.UTF_8).length);

            System.out.println("Config file written to: " + CONFIG_PATH.toAbsolutePath().normalize());
            return 0;
        } catch (Exception e) {
            AppLog.errorRedacted("config.get.failed", e, url, "stage", stage,
                    "error_category", errorCategory(e, stage),
                    "http_status", e instanceof HttpStatusException http ? http.statusCode : "unavailable");
            System.err.println("Generation failed: " + e.getMessage());
            return 1;
        }
    }

    // 使用 JDK URLConnection 下载文本内容，并设置连接和读取超时，避免请求无限等待。
    private static String downloadText(String urlText) throws Exception {
        URI uri = URI.create(urlText);
        // 只记录协议和主机；订阅路径、查询参数和用户信息可能包含密钥。
        AppLog.info("config.download.started", "source_scheme", uri.getScheme(), "source_host", uri.getHost());
        long started = System.nanoTime();
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
        connection.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        try {
            if (connection instanceof HttpURLConnection http) {
                int statusCode = http.getResponseCode();
                AppLog.info("config.download.response", "http_status", statusCode,
                        "content_type", http.getContentType());
                if (statusCode >= 400) {
                    throw new HttpStatusException(statusCode, http.getResponseMessage());
                }
            }
            // 一次性读取响应体，按 UTF-8 作为 JSON 文本返回，不将配置内容写入日志。
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readAllBytes();
                AppLog.info("config.download.completed", "bytes", bytes.length,
                        "duration_ms", (System.nanoTime() - started) / 1_000_000);
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } finally {
            if (connection instanceof HttpURLConnection http) {
                http.disconnect();
            }
        }
    }

    private static String errorCategory(Exception error, String stage) {
        if (error instanceof HttpStatusException) {
            return "HTTP_ERROR";
        }
        if (error instanceof SocketTimeoutException) {
            return "TIMEOUT";
        }
        if (error instanceof UnknownHostException) {
            return "DNS_ERROR";
        }
        if (error instanceof SSLException) {
            return "TLS_ERROR";
        }
        if ("normalize".equals(stage)) {
            return "CONFIG_ERROR";
        }
        if ("write".equals(stage)) {
            return "FILE_ERROR";
        }
        if (error instanceof NoSuchElementException || "input".equals(stage)) {
            return "INPUT_ERROR";
        }
        if (error instanceof IllegalArgumentException || error instanceof MalformedURLException) {
            return "INVALID_URL";
        }
        return error instanceof IOException ? "DOWNLOAD_IO_ERROR" : "UNEXPECTED_ERROR";
    }

    private static final class HttpStatusException extends IOException {
        private final int statusCode;

        private HttpStatusException(int statusCode, String reason) {
            super("HTTP " + statusCode + (reason == null ? "" : ": " + reason));
            this.statusCode = statusCode;
        }
    }
}
