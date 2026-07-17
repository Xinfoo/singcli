package singcli.json;

import java.util.ArrayList;
import java.util.List;

// 处理 JSON 文本的字符串转义、容器边界和数组对象元素。
public final class JsonSyntax {
    private JsonSyntax() {
    }

    public static int skipWhitespace(String text, int start) {
        int i = start;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    public static int findValueEnd(String json, int valueStart) {
        if (valueStart >= json.length()) {
            throw new IllegalArgumentException("Field value is empty");
        }
        return switch (json.charAt(valueStart)) {
            case '"' -> parseString(json, valueStart).end();
            case '[' -> findMatchingArrayEnd(json, valueStart);
            case '{' -> findMatchingObjectEnd(json, valueStart);
            default -> findSimpleValueEnd(json, valueStart);
        };
    }

    private static int findSimpleValueEnd(String json, int valueStart) {
        int i = valueStart;
        while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}' && json.charAt(i) != ']') {
            i++;
        }
        return i - 1;
    }

    public static int findMatchingArrayEnd(String json, int arrayStart) {
        return findMatchingEnd(json, arrayStart, '[', ']', "JSON array is not properly closed");
    }

    public static int findMatchingObjectEnd(String json, int objectStart) {
        return findMatchingEnd(json, objectStart, '{', '}', "JSON object is not properly closed");
    }

    private static int findMatchingEnd(String json, int start, char opening, char closing, String error) {
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') {
                i = parseString(json, i).end();
            } else if (ch == opening) {
                depth++;
            } else if (ch == closing && --depth == 0) {
                return i;
            }
        }
        throw new IllegalArgumentException(error);
    }

    public static List<String> objectElements(String arrayJson) {
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
            if (arrayJson.charAt(i) == '{') {
                int objectEnd = findMatchingObjectEnd(arrayJson, i);
                objects.add(arrayJson.substring(i, objectEnd + 1));
                i = objectEnd;
            }
        }
        return objects;
    }

    public static ParsedString parseString(String json, int quoteStart) {
        StringBuilder value = new StringBuilder();
        for (int i = quoteStart + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') {
                return new ParsedString(value.toString(), i);
            }
            if (ch != '\\') {
                value.append(ch);
                continue;
            }
            if (++i >= json.length()) {
                throw new IllegalArgumentException("JSON string escape is incomplete");
            }
            char escaped = json.charAt(i);
            switch (escaped) {
                case '"', '\\', '/' -> value.append(escaped);
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
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

    public static String escapeString(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
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

    public record ParsedString(String value, int end) {
    }
}
