package app;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

// 统一管理标准输入，避免不同命令关闭或预读取 System.in，导致交互菜单无法继续使用。
public final class ConsoleInput {
    private static final Scanner SCANNER = new Scanner(System.in, StandardCharsets.UTF_8);

    private ConsoleInput() {
    }

    // Scanner 在程序整个生命周期内复用，不由单个命令关闭。
    public static Scanner scanner() {
        return SCANNER;
    }
}
