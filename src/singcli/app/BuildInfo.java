package singcli.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

// 读取构建输入 JAR 的 Manifest。Native Image 会把该常量资源一同编入原生可执行文件。
public record BuildInfo(String version, String buildTime, String buildJdk) {
    public static BuildInfo load() {
        try (InputStream input = BuildInfo.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (input == null) {
                return developmentBuild();
            }

            Attributes attributes = new Manifest(input).getMainAttributes();
            return new BuildInfo(
                    valueOrDefault(attributes, "Implementation-Version", "development"),
                    valueOrDefault(attributes, "Build-Time", "unknown"),
                    valueOrDefault(attributes, "Build-Jdk", "unknown"));
        } catch (IOException exception) {
            return developmentBuild();
        }
    }

    private static String valueOrDefault(Attributes attributes, String name, String defaultValue) {
        String value = attributes.getValue(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static BuildInfo developmentBuild() {
        return new BuildInfo("development", "unknown", "unknown");
    }
}
