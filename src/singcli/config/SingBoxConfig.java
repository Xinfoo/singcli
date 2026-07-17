package singcli.config;

import singcli.json.JsonObjectFields;
import singcli.json.JsonSyntax;

import java.util.List;

// sing-box 配置读取入口：向 commands 层提供业务所需的配置视图和字段值。
public final class SingBoxConfig {
    private static final String DEFAULT_CLASH_CONTROLLER = "127.0.0.1:9090";
    private static final String DEFAULT_SELECTOR_TAG = "proxy";

    private SingBoxConfig() {
    }

    public static String normalizeConfig(String json) {
        return ConfigNormalizer.normalize(json);
    }

    // 提取 selector、节点列表、Clash API 地址和 secret。
    public static ConfigView readConfigView(String json) {
        JsonObjectFields.FieldLocation outbounds = JsonObjectFields.findField(json, "outbounds");
        if (outbounds == null || json.charAt(outbounds.valueStart()) != '[') {
            throw new IllegalArgumentException("Top-level outbounds array was not found");
        }

        String outboundsJson = json.substring(outbounds.valueStart(), outbounds.valueEnd() + 1);
        SelectorView selector = findSelector(outboundsJson);
        if (selector.nodes().isEmpty()) {
            throw new IllegalArgumentException("The selector outbound has no selectable nodes");
        }

        String controller = DEFAULT_CLASH_CONTROLLER;
        String secret = "";
        JsonObjectFields.FieldLocation experimental = JsonObjectFields.findField(json, "experimental");
        if (experimental != null && json.charAt(experimental.valueStart()) == '{') {
            String experimentalJson = json.substring(experimental.valueStart(), experimental.valueEnd() + 1);
            JsonObjectFields.FieldLocation clashApi = JsonObjectFields.findField(experimentalJson, "clash_api");
            if (clashApi != null && experimentalJson.charAt(clashApi.valueStart()) == '{') {
                String clashApiJson = experimentalJson.substring(clashApi.valueStart(), clashApi.valueEnd() + 1);
                controller = JsonObjectFields.stringField(
                        clashApiJson, "external_controller", DEFAULT_CLASH_CONTROLLER);
                secret = JsonObjectFields.stringField(clashApiJson, "secret", "");
            }
        }

        return new ConfigView(selector.tag(), selector.nodes(), controller, secret);
    }

    // 读取第一个本地入站地址，供 Windows 系统代理设置使用。
    public static String localProxyAddress(String json) {
        JsonObjectFields.FieldLocation inbounds = JsonObjectFields.findField(json, "inbounds");
        if (inbounds == null || json.charAt(inbounds.valueStart()) != '[') {
            throw new IllegalArgumentException("Top-level inbounds array was not found");
        }

        String inboundsJson = json.substring(inbounds.valueStart(), inbounds.valueEnd() + 1);
        for (String inbound : JsonSyntax.objectElements(inboundsJson)) {
            int port = JsonObjectFields.intField(inbound, "listen_port", -1);
            if (port > 0) {
                String listen = JsonObjectFields.stringField(inbound, "listen", "127.0.0.1");
                return listen + ":" + port;
            }
        }
        throw new IllegalArgumentException("No local inbound listen_port was found");
    }

    public static String stringFieldOrDefault(String objectJson, String field, String fallback) {
        return JsonObjectFields.stringField(objectJson, field, fallback);
    }

    public static String jsonEscape(String value) {
        return JsonSyntax.escapeString(value);
    }

    private static SelectorView findSelector(String outboundsJson) {
        SelectorView fallback = null;
        for (String outbound : JsonSyntax.objectElements(outboundsJson)) {
            if (!"selector".equals(JsonObjectFields.stringField(outbound, "type", ""))) {
                continue;
            }
            String tag = JsonObjectFields.stringField(outbound, "tag", "");
            List<String> nodes = JsonObjectFields.stringArrayField(outbound, "outbounds");
            SelectorView selector = new SelectorView(tag, nodes);
            if (DEFAULT_SELECTOR_TAG.equals(tag)) {
                return selector;
            }
            if (fallback == null) {
                fallback = selector;
            }
        }
        if (fallback == null) {
            throw new IllegalArgumentException("No selector outbound was found");
        }
        return fallback;
    }

    public record ConfigView(String selectorTag, List<String> nodes, String controller, String secret) {
    }

    private record SelectorView(String tag, List<String> nodes) {
    }
}
