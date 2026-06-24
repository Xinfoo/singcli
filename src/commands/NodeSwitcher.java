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
import java.util.Optional;
import java.util.Scanner;

class NodeSwitcher {
    // switch 命令读取和校验的配置文件路径必须与 get/start 使用同一套规则。
    private static final Path CONFIG_PATH = AppPaths.configPath();

    static int run(String[] args) {
        // 主流程统一放在 try 块中，失败时打印原因并返回非零退出码。
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            // 没有配置文件时无法知道 Clash API 和 selector 信息。
            if (!Files.exists(CONFIG_PATH)) {
                throw new IllegalArgumentException("config.json was not found: " + CONFIG_PATH.toAbsolutePath().normalize() + ". Generate the config first");
            }
            Path currentConfig = normalizeConfigPath(CONFIG_PATH);

            // 先选择要操作的 sing-box 进程；用户取消选择时正常退出。
            Optional<ProcessHandle> selectedProcess = chooseSingBoxProcess(scanner);
            if (selectedProcess.isEmpty()) {
                return 0;
            }
            ProcessHandle process = selectedProcess.get();
            if (!ensureProcessUsesConfig(process, currentConfig)) {
                return 0;
            }
            // 解析配置，得到 selector tag、候选节点、Clash API 地址和 secret。
            String config = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            ConfigSupport.ConfigView view = ConfigSupport.readConfigView(config);
            // 查询当前节点，查询失败时本次切换不能继续。
            Optional<String> currentNode = queryCurrentNode(view);
            if (currentNode.isEmpty()) {
                throw new IllegalStateException("Could not read the current node");
            }
            String current = currentNode.get();
            printNodeList(view.nodes(), current);

            // 用户输入节点编号；空输入表示只查看节点列表并退出。
            System.out.print("Select a node number, or press Enter to exit: ");
            String choice = scanner.nextLine().trim();
            if (choice.isEmpty()) {
                return 0;
            }

            // 把编号转换为节点名，再调用 Clash API 完成切换。
            int index = parseChoice(choice, view.nodes().size());
            String target = view.nodes().get(index - 1);
            switchNode(view, target);
            System.out.println("Switched to: " + target);
            return 0;
        } catch (Exception e) {
            System.err.println("Switch failed: " + ProcessSupport.errorMessage(e));
            return 1;
        }
    }

    // 搜索运行中的 sing-box 进程；多个进程时要求用户明确选择。
    private static Optional<ProcessHandle> chooseSingBoxProcess(Scanner scanner) {
        List<ProcessHandle> running = ProcessSupport.findRunningSingBoxProcesses();
        if (running.isEmpty()) {
            throw new IllegalStateException("No running sing-box process was detected.");
        }
        // 只有一个进程时直接选中，同时打印给用户确认。
        if (running.size() == 1) {
            ProcessHandle process = running.get(0);
            System.out.println("Detected a sing-box process:");
            ProcessSupport.printProcessTable(running);
            return Optional.of(process);
        }

        // 多个进程时显示编号列表，避免误操作其它 sing-box 实例。
        System.out.println("Detected multiple sing-box processes. Select one:");
        ProcessSupport.printIndexedProcessTable(running);
        System.out.print("Enter the process number to operate on, or press Enter to exit: ");
        String choice = scanner.nextLine().trim();
        // 空输入表示用户取消切换操作。
        if (choice.isEmpty()) {
            System.out.println("Exited.");
            return Optional.empty();
        }
        try {
            // 输入必须是列表范围内的编号。
            int index = Integer.parseInt(choice);
            if (index >= 1 && index <= running.size()) {
                return Optional.of(running.get(index - 1));
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Invalid process number: " + choice);
    }

    private static boolean ensureProcessUsesConfig(ProcessHandle process, Path currentConfig) {
        Optional<Path> processConfig = ProcessSupport.configPath(process);
        if (processConfig.isEmpty()) {
            System.err.println("Switch aborted: could not determine the config file used by sing-box process " + process.pid());
            return false;
        }

        // 两边都转换为规范路径后比较，可处理相对路径和符号链接。
        Path normalizedProcessConfig = normalizeConfigPath(processConfig.get());
        if (!normalizedProcessConfig.equals(currentConfig)) {
            System.err.println("Switch aborted: selected sing-box process " + process.pid()
                    + " is using a different config file: " + normalizedProcessConfig
                    + " (current: " + currentConfig + ")");
            return false;
        }
        return true;
    }

    // 把路径转成绝对规范路径；存在的路径尽量解析到真实路径。
    private static Path normalizeConfigPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            return absolute.toRealPath();
        } catch (Exception ignored) {
            return absolute;
        }
    }

    // 通过 Clash API 查询 selector 当前使用的节点名称。
    private static Optional<String> queryCurrentNode(ConfigSupport.ConfigView view) {
        try {
            HttpResponse<String> response = sendClashRequest(view, "GET", view.selectorTag(), null);
            // 成功响应中读取 now 字段；没有 now 字段时返回空字符串并继续展示 Unknown。
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return Optional.of(ConfigSupport.stringFieldOrDefault(response.body(), "now", ""));
            }
            System.out.println("Failed to read the current node. HTTP " + response.statusCode());
        } catch (Exception e) {
            System.out.println("Failed to read the current node: " + ProcessSupport.errorMessage(e));
            System.out.println("Make sure sing-box is running and using a config.json with Clash API enabled.");
        }
        return Optional.empty();
    }

    // 通过 Clash API 把 selector 切换到目标节点。
    private static void switchNode(ConfigSupport.ConfigView view, String target) throws Exception {
        // 节点名来自配置文件，写入 JSON 请求体前需要转义。
        String body = "{\"name\":\"" + ConfigSupport.jsonEscape(target) + "\"}";
        HttpResponse<String> response = sendClashRequest(view, "PUT", view.selectorTag(), body);
        // Clash API 返回非 2xx 时把响应体带回错误信息，方便定位失败原因。
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    // 构造并发送 Clash API 请求；GET 用于查询，PUT 用于切换节点。
    private static HttpResponse<String> sendClashRequest(
            ConfigSupport.ConfigView view,
            String method,
            String proxyName,
            String body
    ) throws Exception {
        // 配置里的 external_controller 可能不带协议，默认按 http 访问。
        String controller = view.controller().startsWith("http://") || view.controller().startsWith("https://")
                ? view.controller()
                : "http://" + view.controller();
        // selector tag 是 URL 路径的一部分，需要进行路径段编码。
        String url = controller + "/proxies/" + encodePathSegment(proxyName);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8));
        // 如果配置了 secret，按 Clash API 约定添加 Bearer token。
        if (!view.secret().isEmpty()) {
            builder.header("Authorization", "Bearer " + view.secret());
        }
        // PUT 请求携带 JSON 请求体；其它调用路径目前都按 GET 处理。
        if ("PUT".equals(method)) {
            builder.header("Content-Type", "application/json");
            builder.PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    // 打印可选节点列表，并用星号标记当前节点。
    private static void printNodeList(List<String> nodes, String current) {
        System.out.println();
        System.out.println("Current node: " + (current.isEmpty() ? "Unknown" : current));
        System.out.println("Available nodes:");
        for (int i = 0; i < nodes.size(); i++) {
            String node = nodes.get(i);
            String marker = node.equals(current) ? " *" : "";
            System.out.printf("%2d. %s%s%n", i + 1, node, marker);
        }
    }

    // 把用户输入的节点编号转换成整数，并校验编号范围。
    private static int parseChoice(String choice, int max) {
        try {
            int index = Integer.parseInt(choice);
            if (index >= 1 && index <= max) {
                return index;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Invalid node number: " + choice);
    }

    // 编码 URL 路径段；URLEncoder 会把空格编码为 +，这里改成路径更常见的 %20。
    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // 统一提取异常消息；异常没有消息时返回类名，避免输出空白错误。
}
