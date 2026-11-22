package top.hibermod.hbns.mainloading.client;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.resources.language.LanguageManager;

/**
 * 中文条目表。
 *
 * <p>有些模组的界面直接使用 {@code Component.translatable("模组.键")}，但包里没有对应
 * 语言文件，界面上就露出原始键名（例如 {@code chunksmith.worldenter.title}、
 * {@code gui.roadweaver.initgen.stage.coarse_sampling}）。资源包这条路在不同启动方式下
 * 并不总是生效，所以改由 {@link HbnsLanguage} 直接接管查询。
 *
 * <p>只补中文环境；其它语言保持原样。
 */
public final class LangInjector {

    private LangInjector() {
    }

    private static final Map<String, String> ZH = new LinkedHashMap<>();

    static {
        // Chunksmith：首次进入世界的预生成界面（与 fork 分支 hbns-i18n 使用的键一一对应）
        ZH.put("chunksmith.worldenter.title", "正在准备世界");
        ZH.put("chunksmith.worldenter.headline", "正在预生成地形，让你到达时一切就绪。");
        ZH.put("chunksmith.worldenter.headline_multi", "正在预生成 %s（第 %s / %s 个），完成后即可进入。");
        ZH.put("chunksmith.worldenter.progress", "%s%%  --  %s 个区块");
        ZH.put("chunksmith.worldenter.eta", "预计还需 %s");
        ZH.put("chunksmith.worldenter.eta_estimating", "正在估算…");
        ZH.put("chunksmith.worldenter.enter_now", "立即进入世界");
        ZH.put("chunksmith.worldenter.hint", "现在进入不会中断生成，它会在后台继续。");
        ZH.put("chunksmith.worldenter.time_unknown", "未知");
        ZH.put("chunksmith.worldenter.time_less_than_minute", "不到一分钟");
        ZH.put("chunksmith.worldenter.time_minute", "%s 分钟");
        ZH.put("chunksmith.worldenter.time_minutes", "%s 分钟");
        ZH.put("chunksmith.worldenter.time_hour", "%s 小时");
        ZH.put("chunksmith.worldenter.time_hours", "%s 小时");
        ZH.put("chunksmith.worldenter.time_hour_minute", "%s %s");

        // RoadWeaver：初始道路生成
        ZH.put("gui.roadweaver.initgen.title", "RoadWeaver 初始生成");
        ZH.put("gui.roadweaver.initgen.summary", "总计：%s  生成中：%s  完成：%s  失败：%s");
        ZH.put("gui.roadweaver.initgen.stage_progress", "阶段进度：%s%%");
        ZH.put("gui.roadweaver.initgen.tiles", "地形块：%s/%s | 内存：%s | 磁盘：%s | 已采样：%s");
        ZH.put("gui.roadweaver.initgen.throughput", "上一批：%s | 吞吐：%s 样本/秒 | 初始线程：%s | 活动线程：%s");
        ZH.put("gui.roadweaver.initgen.exact_throughput", "精确采样：%s/%s 列 | %s 列/秒 | 预计剩余：%s");
        ZH.put("gui.roadweaver.initgen.exact_batch", "上一批：%s 列 / %s 毫秒 | %s 列/秒");
        ZH.put("gui.roadweaver.initgen.exact_paths", "精确路径：%s/%s");
        ZH.put("gui.roadweaver.initgen.exact_paths_remaining", "剩余路径：%s | 阶段进度：%s%%");
        ZH.put("gui.roadweaver.initgen.sampling_modes", "采样：配置 %s，实际 %s");
        ZH.put("gui.roadweaver.initgen.device", "后端：%s | 设备：%s | 偏好：%s");
        ZH.put("gui.roadweaver.initgen.fallback", "回退：%s");
        ZH.put("gui.roadweaver.initgen.stage.planning", "规划结构与连接");
        ZH.put("gui.roadweaver.initgen.stage.coarse_sampling", "粗采样地形");
        ZH.put("gui.roadweaver.initgen.stage.coarse_pathing", "计算粗略路径");
        ZH.put("gui.roadweaver.initgen.stage.exact_sampling", "精确采样地形");
        ZH.put("gui.roadweaver.initgen.stage.exact_pathing", "计算精确路径");
        ZH.put("gui.roadweaver.initgen.stage.road_generation", "生成最终道路");
        ZH.put("gui.roadweaver.initgen.stage.post_processing", "后处理与落盘");
        ZH.put("gui.roadweaver.initgen.stage.finished", "初始生成完成");
    }

    /** 中文条目（只读视图，供 {@link HbnsLanguage} 使用）。 */
    public static Map<String, String> zhEntries() {
        return ZH;
    }

    /** 按语言键直接查中文。 */
    public static String lookup(String key) {
        return key == null ? null : ZH.get(key);
    }

    /** 当前游戏语言是否是中文。 */
    public static boolean isChinese() {
        try {
            LanguageManager lm = net.minecraft.client.Minecraft.getInstance().getLanguageManager();
            String code = lm.getSelected().toLowerCase(Locale.ROOT);
            return code.startsWith("zh");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 判断一段「已经显示出来的文字」是不是 RoadWeaver 的阶段名。
     *
     * <p>用于给走 FML 进度条那条路径的文字补 {@code [RoadWeaver]} 前缀：
     * 那条路径拿到的是译文（或原始键），这里和我们的中文表/键前缀比对。
     */
    public static boolean isRoadWeaverLabel(String text) {
        if (text == null) {
            return false;
        }
        if (text.startsWith("gui.roadweaver.")) {
            return true;
        }
        for (Map.Entry<String, String> e : ZH.entrySet()) {
            if (e.getKey().startsWith("gui.roadweaver.initgen.stage.") && e.getValue().equals(text)) {
                return true;
            }
        }
        return false;
    }
}
