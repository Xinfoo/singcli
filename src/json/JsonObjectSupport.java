package json;

import java.util.ArrayList;
import java.util.List;

// 在 JSON 对象顶层定位、读取、替换和添加字段。
public final class JsonObjectSupport {
    private JsonObjectSupport() {
    }

    public static FieldLocation findField(String objectJson, String field) {
        int objectStart = JsonSyntaxSupport.skipWhitespace(objectJson, 0);
        if (objectStart >= objectJson.length() || objectJson.charAt(objectStart) != '{') {
            return null;
        }

        int objectDepth = 0;
        int arrayDepth = 0;
        for (int i = objectStart; i < objectJson.length(); i++) {
            char ch = objectJson.charAt(i);
            if (ch == '"') {
                JsonSyntaxSupport.ParsedString parsed = JsonSyntaxSupport.parseString(objectJson, i);
                if (objectDepth == 1 && arrayDepth == 0) {
                    int colon = JsonSyntaxSupport.skipWhitespace(objectJson, parsed.end() + 1);
                    if (colon < objectJson.length() && objectJson.charAt(colon) == ':'
                            && field.equals(parsed.value())) {
                        int valueStart = JsonSyntaxSupport.skipWhitespace(objectJson, colon + 1);
                        int valueEnd = JsonSyntaxSupport.findValueEnd(objectJson, valueStart);
                        return new FieldLocation(valueStart, valueEnd);
                    }
                }
                i = parsed.end();
            } else if (ch == '{') {
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

    public static String replaceOrAddField(String objectJson, String field, String value, String indent) {
        FieldLocation location = findField(objectJson, field);
        if (location == null) {
            return addField(objectJson, field, value, indent);
        }
        return objectJson.substring(0, location.valueStart())
                + value
                + objectJson.substring(location.valueEnd() + 1);
    }

    public static String addField(String objectJson, String field, String value, String indent) {
        int objectStart = JsonSyntaxSupport.skipWhitespace(objectJson, 0);
        if (objectStart >= objectJson.length() || objectJson.charAt(objectStart) != '{') {
            throw new IllegalArgumentException("Target is not a JSON object");
        }
        int objectEnd = JsonSyntaxSupport.findMatchingObjectEnd(objectJson, objectStart);
        boolean empty = JsonSyntaxSupport.skipWhitespace(objectJson, objectStart + 1) == objectEnd;
        String insertion = (empty ? "\n" : ",\n")
                + indent + "\"" + field + "\": " + value + "\n";
        return objectJson.substring(0, objectEnd) + insertion + objectJson.substring(objectEnd);
    }

    public static List<String> stringArrayField(String objectJson, String field) {
        FieldLocation location = findField(objectJson, field);
        if (location == null || objectJson.charAt(location.valueStart()) != '[') {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        int arrayEnd = JsonSyntaxSupport.findMatchingArrayEnd(objectJson, location.valueStart());
        for (int i = location.valueStart() + 1; i < arrayEnd; i++) {
            i = JsonSyntaxSupport.skipWhitespace(objectJson, i);
            if (i >= arrayEnd) {
                break;
            }
            if (objectJson.charAt(i) == '"') {
                JsonSyntaxSupport.ParsedString parsed = JsonSyntaxSupport.parseString(objectJson, i);
                values.add(parsed.value());
                i = parsed.end();
            }
        }
        return values;
    }

    public static String stringField(String objectJson, String field, String fallback) {
        FieldLocation location = findField(objectJson, field);
        if (location == null || objectJson.charAt(location.valueStart()) != '"') {
            return fallback;
        }
        return JsonSyntaxSupport.parseString(objectJson, location.valueStart()).value();
    }

    public static int intField(String objectJson, String field, int fallback) {
        FieldLocation location = findField(objectJson, field);
        if (location == null || location.valueStart() > location.valueEnd()) {
            return fallback;
        }
        String value = objectJson.substring(location.valueStart(), location.valueEnd() + 1).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public record FieldLocation(int valueStart, int valueEnd) {
    }
}
