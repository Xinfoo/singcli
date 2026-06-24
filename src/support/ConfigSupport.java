import java.util.ArrayList;
import java.util.List;

// 配置文件工具：修改 sing-box JSON 配置，并读取节点切换需要的信息。
final class ConfigSupport {
    // 配置缺少 Clash API 或 selector tag 时使用的默认值。
    private static final String DEFAULT_CLASH_CONTROLLER = "127.0.0.1:9090";
    private static final String DEFAULT_SELECTOR_TAG = "proxy";

    // 生成配置时替换成的本地 mixed 入站，供 HTTP/SOCKS 客户端连接。
    private static final String NEW_INBOUNDS_VALUE = """
[
    {
      "listen": "127.0.0.1",
      "listen_port": 7897,
      "type": "mixed",
      "users": []
    }
  ]""";

    // experimental.clash_api 字段的默认内容。
    private static final String CLASH_API_VALUE = """
{
      "external_controller": "127.0.0.1:9090",
      "secret": ""
    }""";

    // 顶层 experimental 字段不存在时插入的完整默认对象。
    private static final String EXPERIMENTAL_VALUE = """
{
    "clash_api": {
      "external_controller": "127.0.0.1:9090",
      "secret": ""
    }
  }""";

    // 工具类不需要实例化。
    private ConfigSupport() {
    }

    // 规范化下载到的配置：替换入站，并确保 Clash API 可用。
    static String normalizeConfig(String json) {
        json = replaceTopLevelInbounds(json);
        return ensureClashApi(json);
    }

    // 从配置中提取节点切换需要的 selector、节点列表、Clash API 地址和 secret。
    static ConfigView readConfigView(String json) {
        // 先定位顶层 outbounds，节点信息都在这里面。
        FieldLocation outboundsField = findFieldInObject(json, "outbounds");
        if (outboundsField == null || json.charAt(outboundsField.valueStart) != '[') {
            throw new IllegalArgumentException("Top-level outbounds array was not found");
        }

        // 只截取 outbounds 数组，减少后续查找范围。
        String outboundsArray = json.substring(outboundsField.valueStart, outboundsField.valueEnd + 1);
        SelectorView selector = findSelector(outboundsArray);
        if (selector.nodes.isEmpty()) {
            throw new IllegalArgumentException("The selector outbound has no selectable nodes");
        }

        // Clash API 默认使用本地 9090；配置里存在 experimental.clash_api 时覆盖默认值。
        String controller = DEFAULT_CLASH_CONTROLLER;
        String secret = "";
        FieldLocation experimental = findFieldInObject(json, "experimental");
        if (experimental != null && json.charAt(experimental.valueStart) == '{') {
            String experimentalJson = json.substring(experimental.valueStart, experimental.valueEnd + 1);
            FieldLocation clashApi = findFieldInObject(experimentalJson, "clash_api");
            // 只接受对象形式的 clash_api，字段类型不对时保留默认值。
            if (clashApi != null && experimentalJson.charAt(clashApi.valueStart) == '{') {
                String clashApiJson = experimentalJson.substring(clashApi.valueStart, clashApi.valueEnd + 1);
                controller = stringFieldOrDefault(clashApiJson, "external_controller", DEFAULT_CLASH_CONTROLLER);
                secret = stringFieldOrDefault(clashApiJson, "secret", "");
            }
        }

        return new ConfigView(selector.tag, selector.nodes, controller, secret);
    }

    // 从顶层 inbounds 中读取本地代理监听地址，供 Windows 系统代理设置使用。
    static String localProxyAddress(String json) {
        FieldLocation inboundsField = findFieldInObject(json, "inbounds");
        if (inboundsField == null || json.charAt(inboundsField.valueStart) != '[') {
            throw new IllegalArgumentException("Top-level inbounds array was not found");
        }

        String inboundsArray = json.substring(inboundsField.valueStart, inboundsField.valueEnd + 1);
        for (String inbound : objectElements(inboundsArray)) {
            int port = intFieldOrDefault(inbound, "listen_port", -1);
            if (port <= 0) {
                continue;
            }
            String listen = stringFieldOrDefault(inbound, "listen", "127.0.0.1");
            return listen + ":" + port;
        }
        throw new IllegalArgumentException("No local inbound listen_port was found");
    }

    // 在 outbounds 数组中寻找 selector；优先 tag 为 proxy 的 selector，否则使用第一个 selector。
    private static SelectorView findSelector(String outboundsArray) {
        SelectorView fallback = null;
        for (String outbound : objectElements(outboundsArray)) {
            String type = stringFieldOrDefault(outbound, "type", "");
            if (!"selector".equals(type)) {
                continue;
            }
            String tag = stringFieldOrDefault(outbound, "tag", "");
            List<String> nodes = stringArrayField(outbound, "outbounds");
            SelectorView selector = new SelectorView(tag, nodes);
            // 约定优先操作 tag 为 proxy 的选择器。
            if (DEFAULT_SELECTOR_TAG.equals(tag)) {
                return selector;
            }
            // 如果没有 proxy，保留第一个 selector 作为兜底。
            if (fallback == null) {
                fallback = selector;
            }
        }
        // 找不到任何 selector 时，节点切换功能无法工作。
        if (fallback == null) {
            throw new IllegalArgumentException("No selector outbound was found");
        }
        return fallback;
    }

    // 确保配置中存在 experimental.clash_api；已有字段时替换或补充。
    private static String ensureClashApi(String json) {
        FieldLocation experimental = findFieldInObject(json, "experimental");
        if (experimental == null) {
            // 顶层没有 experimental 时直接添加完整默认对象。
            return addFieldToObject(json, "experimental", EXPERIMENTAL_VALUE, "  ");
        }
        if (json.charAt(experimental.valueStart) != '{') {
            // experimental 存在但不是对象时，用默认对象替换原值。
            return json.substring(0, experimental.valueStart)
                    + EXPERIMENTAL_VALUE
                    + json.substring(experimental.valueEnd + 1);
        }
        // experimental 是对象时，在对象内部替换或添加 clash_api。
        String experimentalJson = json.substring(experimental.valueStart, experimental.valueEnd + 1);
        String updatedExperimental = replaceOrAddFieldInObject(experimentalJson, "clash_api", CLASH_API_VALUE, "    ");
        return json.substring(0, experimental.valueStart)
                + updatedExperimental
                + json.substring(experimental.valueEnd + 1);
    }

    // 替换顶层 inbounds 为本工具期望的 mixed 入站；缺失时新增。
    private static String replaceTopLevelInbounds(String json) {
        FieldLocation inbounds = findFieldInObject(json, "inbounds");
        if (inbounds == null) {
            // 没有 inbounds 时在顶层对象添加默认入站。
            return addFieldToObject(json, "inbounds", NEW_INBOUNDS_VALUE, "  ");
        }
        if (json.charAt(inbounds.valueStart) != '[') {
            throw new IllegalArgumentException("The inbounds field is not an array");
        }
        // 已有 inbounds 数组时整体替换为默认 mixed 入站。
        return json.substring(0, inbounds.valueStart)
                + NEW_INBOUNDS_VALUE
                + json.substring(inbounds.valueEnd + 1);
    }

    // 在对象 JSON 中替换指定字段；字段不存在时新增。
    private static String replaceOrAddFieldInObject(String objectJson, String field, String value, String indent) {
        FieldLocation location = findFieldInObject(objectJson, field);
        if (location != null) {
            // 只替换字段值，保留字段名和其它内容。
            return objectJson.substring(0, location.valueStart)
                    + value
                    + objectJson.substring(location.valueEnd + 1);
        }
        return addFieldToObject(objectJson, field, value, indent);
    }

    // 向 JSON 对象末尾添加一个字段，indent 用于控制新字段缩进。
    private static String addFieldToObject(String objectJson, String field, String value, String indent) {
        int objectStart = skipWhitespace(objectJson, 0);
        if (objectStart >= objectJson.length() || objectJson.charAt(objectStart) != '{') {
            throw new IllegalArgumentException("Target is not a JSON object");
        }
        int objectEnd = findMatchingObjectEnd(objectJson, objectStart);
        // 空对象不需要前置逗号，非空对象需要在原字段后追加逗号。
        boolean empty = skipWhitespace(objectJson, objectStart + 1) == objectEnd;
        String insertion = (empty ? "\n" : ",\n")
                + indent
                + "\""
                + field
                + "\": "
                + value
                + "\n";
        return objectJson.substring(0, objectEnd) + insertion + objectJson.substring(objectEnd);
    }

    // 在一个 JSON 对象的顶层查找字段位置，不会匹配嵌套对象或数组里的同名字段。
    private static FieldLocation findFieldInObject(String objectJson, String field) {
        int objectStart = skipWhitespace(objectJson, 0);
        if (objectStart >= objectJson.length() || objectJson.charAt(objectStart) != '{') {
            return null;
        }

        int objectDepth = 0;
        int arrayDepth = 0;
        for (int i = objectStart; i < objectJson.length(); i++) {
            char ch = objectJson.charAt(i);
            if (ch == '"') {
                // 字符串可能是字段名，也可能是普通值；解析后跳过整个字符串。
                ParsedString parsed = parseString(objectJson, i);
                if (objectDepth == 1 && arrayDepth == 0) {
                    int colon = skipWhitespace(objectJson, parsed.end + 1);
                    // 只有对象第一层且紧跟冒号的字符串才按字段名处理。
                    if (colon < objectJson.length() && objectJson.charAt(colon) == ':' && field.equals(parsed.value)) {
                        int valueStart = skipWhitespace(objectJson, colon + 1);
                        int valueEnd = findValueEnd(objectJson, valueStart);
                        return new FieldLocation(i, parsed.end, colon, valueStart, valueEnd);
                    }
                }
                i = parsed.end;
                continue;
            }

            // 维护对象和数组深度，用于判断当前是否仍在顶层对象中。
            if (ch == '{') {
                objectDepth++;
            } else if (ch == '}') {
                objectDepth--;
                if (objectDepth == 0) {
                    break;
                }
            } else if (ch == '[') {
                arrayDepth++;
            } else if (ch == ']') {
                arrayDepth--;
            }
        }

        return null;
    }

    // 根据值的起始字符找到 JSON 值的结束位置。
    private static int findValueEnd(String json, int valueStart) {
        if (valueStart >= json.length()) {
            throw new IllegalArgumentException("Field value is empty");
        }
        char ch = json.charAt(valueStart);
        // 字符串、数组、对象需要分别按结构匹配结束位置。
        if (ch == '"') {
            return parseString(json, valueStart).end;
        }
        if (ch == '[') {
            return findMatchingArrayEnd(json, valueStart);
        }
        if (ch == '{') {
            return findMatchingObjectEnd(json, valueStart);
        }

        // 数字、布尔、null 等简单值一直读到逗号或容器结束。
        int i = valueStart;
        while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}' && json.charAt(i) != ']') {
            i++;
        }
        return i - 1;
    }

    // 从数组起始位置开始，找到与之匹配的右方括号。
    private static int findMatchingArrayEnd(String json, int arrayStart) {
        int depth = 0;
        for (int i = arrayStart; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') {
                // 字符串内部的括号不参与结构计数。
                i = parseString(json, i).end;
            } else if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("JSON array is not properly closed");
    }

    // 从对象起始位置开始，找到与之匹配的右花括号。
    private static int findMatchingObjectEnd(String json, int objectStart) {
        int depth = 0;
        for (int i = objectStart; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') {
                // 字符串内部的花括号不参与结构计数。
                i = parseString(json, i).end;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("JSON object is not properly closed");
    }

    // 解析数组中的对象元素，返回每个对象元素的原始 JSON 字符串。
    private static List<String> objectElements(String arrayJson) {
        List<String> objects = new ArrayList<>();
        int arrayStart = skipWhitespace(arrayJson, 0);
        if (arrayStart >= arrayJson.length() || arrayJson.charAt(arrayStart) != '[') {
            throw new IllegalArgumentException("Target is not a JSON array");
        }
        int arrayEnd = findMatchingArrayEnd(arrayJson, arrayStart);
        for (int i = arrayStart + 1; i < arrayEnd; i++) {
            i = skipWhitespace(arrayJson, i);
            if (i >= arrayEnd) {
                break;
            }
            // 这里只收集对象元素，其它类型会被跳过。
            if (arrayJson.charAt(i) == '{') {
                int objectEnd = findMatchingObjectEnd(arrayJson, i);
                objects.add(arrayJson.substring(i, objectEnd + 1));
                i = objectEnd;
            }
        }
        return objects;
    }

    // 读取对象中指定字段的字符串数组；字段不存在或类型不对时返回空列表。
    private static List<String> stringArrayField(String objectJson, String field) {
        FieldLocation location = findFieldInObject(objectJson, field);
        if (location == null || objectJson.charAt(location.valueStart) != '[') {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        int arrayEnd = findMatchingArrayEnd(objectJson, location.valueStart);
        for (int i = location.valueStart + 1; i < arrayEnd; i++) {
            i = skipWhitespace(objectJson, i);
            if (i >= arrayEnd) {
                break;
            }
            // 只提取数组中的字符串元素，其它元素忽略。
            if (objectJson.charAt(i) == '"') {
                ParsedString parsed = parseString(objectJson, i);
                values.add(parsed.value);
                i = parsed.end;
            }
        }
        return values;
    }

    // 读取对象中指定字符串字段；字段不存在或类型不是字符串时返回默认值。
    static String stringFieldOrDefault(String objectJson, String field, String fallback) {
        FieldLocation location = findFieldInObject(objectJson, field);
        if (location == null || objectJson.charAt(location.valueStart) != '"') {
            return fallback;
        }
        return parseString(objectJson, location.valueStart).value;
    }

    // 读取对象中的整数字段；字段不存在、类型不对或无法解析时返回默认值。
    private static int intFieldOrDefault(String objectJson, String field, int fallback) {
        FieldLocation location = findFieldInObject(objectJson, field);
        if (location == null || location.valueStart > location.valueEnd) {
            return fallback;
        }
        String value = objectJson.substring(location.valueStart, location.valueEnd + 1).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // 跳过 JSON 中的空白字符，返回第一个非空白位置。
    private static int skipWhitespace(String text, int start) {
        int i = start;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    // 解析 JSON 字符串字面量，返回反转义后的内容和结束引号位置。
    private static ParsedString parseString(String json, int quoteStart) {
        StringBuilder value = new StringBuilder();
        for (int i = quoteStart + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            // 未转义的双引号表示字符串结束。
            if (ch == '"') {
                return new ParsedString(value.toString(), i);
            }
            // 普通字符直接追加到结果。
            if (ch != '\\') {
                value.append(ch);
                continue;
            }
            // 反斜杠后必须跟合法转义字符。
            if (++i >= json.length()) {
                throw new IllegalArgumentException("JSON string escape is incomplete");
            }
            char escaped = json.charAt(i);
            switch (escaped) {
                // 处理 JSON 支持的标准转义字符。
                case '"', '\\', '/' -> value.append(escaped);
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    // Unicode 十六进制转义需要读取 4 位字符。
                    if (i + 4 >= json.length()) {
                        throw new IllegalArgumentException("JSON Unicode escape is incomplete");
                    }
                    String hex = json.substring(i + 1, i + 5);
                    try {
                        value.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid JSON Unicode escape: " + hex);
                    }
                    i += 4;
                }
                default -> throw new IllegalArgumentException("Invalid JSON string escape: \\" + escaped);
            }
        }
        throw new IllegalArgumentException("JSON string is not properly closed");
    }

    // 把普通字符串转义成可放进 JSON 字符串值的内容。
    static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                // 常见控制字符和特殊字符使用 JSON 标准转义。
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    // 其它不可见控制字符使用 unicode 转义，普通字符原样保留。
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    // 对外返回的配置视图：包含切换节点所需的最小信息。
    record ConfigView(String selectorTag, List<String> nodes, String controller, String secret) {
    }

    // 内部 selector 视图：保存 selector tag 和它可选的 outbound 列表。
    private record SelectorView(String tag, List<String> nodes) {
    }

    // 字段位置信息：记录字段名、冒号、值起止位置，便于后续字符串替换。
    private record FieldLocation(int nameStart, int nameEnd, int colon, int valueStart, int valueEnd) {
    }

    // 解析后的 JSON 字符串：保存反转义内容和结束引号位置。
    private record ParsedString(String value, int end) {
    }
}
