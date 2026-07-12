import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

// 显示由 Clash API 9090 管理的 sing-box 进程及其 selector 当前节点。
class StatusSingBox {
    private static final int CLASH_API_PORT = 9090;

    static int run(String[] args) {
        try {
            List<ProcessHandle> running = ProcessSupport.findRunningSingBoxProcesses();
            if (running.isEmpty()) {
                System.out.println("sing-box is not running.");
                return 0;
            }
            ProcessHandle process = selectProcess(running);
            Path configPath = ProcessSupport.configPath(process).orElse(AppPathsSupport.configPath());
            if (!Files.isRegularFile(configPath)) {
                throw new IllegalStateException("Could not read the config used by sing-box process " + process.pid());
            }
            ConfigSupport.ConfigView view = ConfigSupport.readConfigView(
                    Files.readString(configPath, StandardCharsets.UTF_8));
            String currentNode = queryCurrentNode(view);

            System.out.println("sing-box is running.");
            ProcessSupport.printProcessTable(List.of(process));
            System.out.println("Config: " + configPath.toAbsolutePath().normalize());
            System.out.println("Current node: " + (currentNode.isEmpty() ? "Unknown" : currentNode));
            return 0;
        } catch (Exception e) {
            System.err.println("Status failed: " + ProcessSupport.errorMessage(e));
            return 1;
        }
    }

    private static ProcessHandle selectProcess(List<ProcessHandle> running) {
        if (running.size() == 1) return running.get(0);
        List<ProcessHandle> matches = running.stream()
                .filter(process -> ProcessSupport.isListeningOnTcpPort(process, CLASH_API_PORT)).toList();
        if (matches.size() == 1) return matches.get(0);
        if (matches.isEmpty()) {
            throw new IllegalStateException("Multiple sing-box processes were detected, but none listens on Clash API port 9090");
        }
        throw new IllegalStateException("Multiple sing-box processes are listening on Clash API port 9090");
    }

    private static String queryCurrentNode(ConfigSupport.ConfigView view) throws Exception {
        String controller = view.controller().startsWith("http://") || view.controller().startsWith("https://")
                ? view.controller() : "http://" + view.controller();
        String proxy = URLEncoder.encode(view.selectorTag(), StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(controller + "/proxies/" + proxy))
                .timeout(Duration.ofSeconds(8)).GET();
        if (!view.secret().isEmpty()) builder.header("Authorization", "Bearer " + view.secret());
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Clash API returned HTTP " + response.statusCode());
        }
        return ConfigSupport.stringFieldOrDefault(response.body(), "now", "");
    }
}
