package config;

import json.JsonObjectSupport;

// 把下载的 sing-box 配置调整为 singcli 所需的入站和 Clash API 结构。
final class ConfigNormalizer {
    private static final String INBOUNDS_VALUE = """
[
    {
      "listen": "127.0.0.1",
      "listen_port": 7897,
      "type": "mixed",
      "users": []
    }
  ]""";

    private static final String CLASH_API_VALUE = """
{
      "external_controller": "127.0.0.1:9090",
      "secret": ""
    }""";

    private static final String EXPERIMENTAL_VALUE = """
{
    "clash_api": {
      "external_controller": "127.0.0.1:9090",
      "secret": ""
    }
  }""";

    private ConfigNormalizer() {
    }

    static String normalize(String json) {
        return ensureClashApi(replaceInbounds(json));
    }

    private static String replaceInbounds(String json) {
        JsonObjectSupport.FieldLocation inbounds = JsonObjectSupport.findField(json, "inbounds");
        if (inbounds == null) {
            return JsonObjectSupport.addField(json, "inbounds", INBOUNDS_VALUE, "  ");
        }
        if (json.charAt(inbounds.valueStart()) != '[') {
            throw new IllegalArgumentException("The inbounds field is not an array");
        }
        return json.substring(0, inbounds.valueStart())
                + INBOUNDS_VALUE
                + json.substring(inbounds.valueEnd() + 1);
    }

    private static String ensureClashApi(String json) {
        JsonObjectSupport.FieldLocation experimental = JsonObjectSupport.findField(json, "experimental");
        if (experimental == null) {
            return JsonObjectSupport.addField(json, "experimental", EXPERIMENTAL_VALUE, "  ");
        }
        if (json.charAt(experimental.valueStart()) != '{') {
            return json.substring(0, experimental.valueStart())
                    + EXPERIMENTAL_VALUE
                    + json.substring(experimental.valueEnd() + 1);
        }

        String experimentalJson = json.substring(experimental.valueStart(), experimental.valueEnd() + 1);
        String updated = JsonObjectSupport.replaceOrAddField(
                experimentalJson, "clash_api", CLASH_API_VALUE, "    ");
        return json.substring(0, experimental.valueStart())
                + updated
                + json.substring(experimental.valueEnd() + 1);
    }
}
