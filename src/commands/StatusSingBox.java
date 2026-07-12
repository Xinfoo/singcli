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
import java.util.Scanner;

// 显示由 Clash API 9090 管理的 sing-box 进程及其 selector 当前节点。
class StatusSingBox {
    // singcli 生成配置时固定启用该 Clash API 端口，多进程场景据此识别受管理的实例。
    private static final int CLASH_API_PORT = 9090;

    // 命令行直接执行 singcli status 时，在结果显示后等待用户按 Enter 再退出。
    static int runDirect(String[] args) {
        int exitCode = run(args);
        waitForEnter();
        return exitCode;
    }

    // 执行状态查询本身；Index 调用此方法，后续交互由 Index 统一处理。
    static int run(String[] args) {
        try {
            // status 是只读操作：没有进程时直接报告状态，不作为命令执行失败处理。
            List<ProcessHandle> running = ProcessSupport.findRunningSingBoxProcesses();
            if (running.isEmpty()) {
                System.out.println("sing-box is not running.");
                return 0;
            }

            // 单进程直接使用；多进程则只选择实际占用 Clash API 9090 端口的进程。
            ProcessHandle process = selectProcess(running);
            // 优先读取进程命令行里的 -c/--config 路径；无法取得时退回 singcli 默认配置。
            Path configPath = ProcessSupport.configPath(process).orElse(AppPathsSupport.configPath());
            if (!Files.isRegularFile(configPath)) {
                throw new IllegalStateException("Could not read the config used by sing-box process " + process.pid());
            }

            // 配置中包含 selector tag、Clash API 地址和鉴权 secret，查询当前节点需要这些信息。
            ConfigSupport.ConfigView view = ConfigSupport.readConfigView(
                    Files.readString(configPath, StandardCharsets.UTF_8));
            String currentNode = queryCurrentNode(view);

            // 进程表沿用其它命令的统一格式，并额外显示 status 特有的配置和节点信息。
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

    private static void waitForEnter() {
        Scanner scanner = InputSupport.scanner();
        System.out.println();
        System.out.print("Press Enter to exit...");
        // 输入被重定向且已经到达 EOF 时直接结束，避免抛出 NoSuchElementException。
        if (scanner.hasNextLine()) {
            scanner.nextLine();
        }
    }

    // 多个 sing-box 同时运行时，不要求用户选择，只保留监听 9090 的那个实例。
    private static ProcessHandle selectProcess(List<ProcessHandle> running) {
        if (running.size() == 1) {
            return running.get(0);
        }
        List<ProcessHandle> matches = running.stream()
                .filter(process -> ProcessSupport.isListeningOnTcpPort(process, CLASH_API_PORT)).toList();
        if (matches.size() == 1) {
            return matches.get(0);
        }
        // 无法唯一确定目标时中止，避免把其它 sing-box 实例的信息误认为 singcli 的状态。
        if (matches.isEmpty()) {
            throw new IllegalStateException("Multiple sing-box processes were detected, but none listens on Clash API port 9090");
        }
        throw new IllegalStateException("Multiple sing-box processes are listening on Clash API port 9090");
    }

    // 调用 Clash API 的 selector 详情接口，响应中的 now 字段就是当前选中的节点。
    private static String queryCurrentNode(ConfigSupport.ConfigView view) throws Exception {
        // external_controller 通常不带协议；没有协议时按本地 Clash API 的常见 HTTP 方式访问。
        String controller = view.controller().startsWith("http://") || view.controller().startsWith("https://")
                ? view.controller() : "http://" + view.controller();
        // selector tag 作为 URL 路径段使用，需要编码其中的空格及特殊字符。
        String proxy = URLEncoder.encode(view.selectorTag(), StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(controller + "/proxies/" + proxy))
                .timeout(Duration.ofSeconds(8)).GET();
        // 配置了 secret 时按 Clash API 约定发送 Bearer token。
        if (!view.secret().isEmpty()) {
            builder.header("Authorization", "Bearer " + view.secret());
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        // 非成功响应不能代表真实节点状态，因此作为 status 命令失败返回。
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Clash API returned HTTP " + response.statusCode());
        }
        return ConfigSupport.stringFieldOrDefault(response.body(), "now", "");
    }
}
