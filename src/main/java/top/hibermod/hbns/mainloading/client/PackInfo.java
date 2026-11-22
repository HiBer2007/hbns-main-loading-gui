package top.hibermod.hbns.mainloading.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 整合包元数据（名称 / 版本），数据源是整合包根目录的 {@code hbns_pack.json}。
 *
 * <p>与早期窗口模组里的同名类保持同一份格式，任何启动方式都能读到：
 * <pre>
 * { "name": "HBNS V3", "version": "0.0.3-alpha", "display": "HBNS V3 0.0.3-alpha" }
 * </pre>
 */
public final class PackInfo {

    private static final String FILE = "hbns_pack.json";
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern VERSION = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern DISPLAY = Pattern.compile("\"display\"\\s*:\\s*\"([^\"]*)\"");

    private static String cached;
    private static boolean tried;

    private PackInfo() {
    }

    /** 形如 {@code HBNS V3 0.0.3-alpha}；找不到文件返回空串。 */
    public static synchronized String display() {
        if (tried) {
            return cached == null ? "" : cached;
        }
        tried = true;
        try {
            Path p = locate();
            if (p == null) {
                return "";
            }
            String json = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            String display = first(DISPLAY, json);
            if (display.isEmpty()) {
                display = (first(NAME, json) + " " + first(VERSION, json)).trim();
            }
            cached = display;
        } catch (Throwable ignored) {
        }
        return cached == null ? "" : cached;
    }

    private static boolean posted;

    /**
     * 把整合包版本作为一条消息推给 FML 的消息队列，供左下角滚动区显示
     * （与早期窗口同一个通道、同一套淡出规则）。
     */
    public static synchronized void postMessageOnce() {
        if (posted) {
            return;
        }
        posted = true;
        String display = display();
        if (display == null || display.isEmpty()) {
            return;
        }
        try {
            Class<?> snm = Class.forName("net.neoforged.fml.loading.progress.StartupNotificationManager");
            var m = snm.getMethod("modLoaderMessage", String.class);
            m.invoke(null, display);
        } catch (Throwable ignored) {
        }
    }
    /** 游戏内 cwd 就是 gameDir；再兜底一层上级目录和 .minecraft/run 布局。 */
    private static Path locate() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path[] candidates = {
                cwd.resolve(FILE),
                cwd.resolve("..").resolve(FILE).normalize(),
                cwd.resolve(".minecraft").resolve(FILE),
                cwd.resolve("run").resolve(FILE),
        };
        for (Path p : candidates) {
            try {
                if (Files.isRegularFile(p)) {
                    return p;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String first(Pattern p, String json) {
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1).trim() : "";
    }
}



