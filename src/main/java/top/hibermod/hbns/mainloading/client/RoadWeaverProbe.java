package top.hibermod.hbns.mainloading.client;

import java.lang.reflect.Method;

/**
 * RoadWeaver 进度桥。
 *
 * <p>RoadWeaver 会在载入存档时把「初始道路生成」画成它自己的卡片式进度条
 * （{@code LoadingGenerationOverlayRenderer}，挂在我们同样接管的
 * {@code LevelLoadingScreen} 上）。既然整个界面的渲染已由我们接管，它的绘制不会执行，
 * 于是这里读取它的**进度快照**，转成我们内部的一条子进度条——数据仍然是它的，
 * 外观统一成 HBNS 的。
 *
 * <p>注意 {@code snapshot(boolean)} 的布尔参数并不是「是否活动」，而是它内部用于
 * 单调递增百分比的下限开关；真正的状态要看 {@code stage()}。所以这里：
 * <ul>
 *   <li>用 {@code snapshot(true)} 取快照；</li>
 *   <li>{@code stage} 为 {@code FINISHED}（或百分比已到 100）时不显示；</li>
 *   <li>其它阶段显示为一条子进度条，标题用它自己的阶段名。</li>
 * </ul>
 *
 * <p>全部通过反射，缺失该模组时静默跳过，不产生硬依赖。
 */
public final class RoadWeaverProbe {

    private static boolean tried;
    private static Method mSnapshot;
    private static Method mStage;
    private static Method mStageLabel;
    private static Method mOverallPercent;
    private static Method mLabelKey;
    private static Object finishedStage;

    private RoadWeaverProbe() {
    }

    private static void init() {
        if (tried) {
            return;
        }
        tried = true;
        try {
            Class<?> tracker = Class.forName(
                    "net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressTracker");
            Class<?> snapshot = Class.forName(
                    "net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressSnapshot");
            Class<?> stage = Class.forName(
                    "net.shiroha233.roadweaver.generation.progress.InitialGenerationStage");
            mSnapshot = tracker.getMethod("snapshot", boolean.class);
            mStage = snapshot.getMethod("stage");
            mStageLabel = snapshot.getMethod("stageLabel");
            mOverallPercent = snapshot.getMethod("overallPercent");
            mLabelKey = stage.getMethod("labelKey");
            finishedStage = stage.getField("FINISHED").get(null);
        } catch (Throwable ignored) {
            mSnapshot = null;
        }
    }

    /** 是否有该模组（只用于日志/诊断）。 */
    public static boolean present() {
        init();
        return mSnapshot != null;
    }

    /**
     * 读取 RoadWeaver 当前进度。
     *
     * @return 没有该模组、或它当前已结束时返回 {@code null}
     */
    public static LoadTrace.Step step() {
        init();
        if (mSnapshot == null) {
            return null;
        }
        try {
            Object snap = mSnapshot.invoke(null, true);
            if (snap == null) {
                return null;
            }
            Object stage = mStage.invoke(snap);
            if (stage == null || stage.equals(finishedStage)) {
                return null;
            }
            int pct = (Integer) mOverallPercent.invoke(snap);
            if (pct >= 100) {
                return null;
            }
            String label = String.valueOf(mStageLabel.invoke(snap));
            // stageLabel() 返回的是「已翻译文本」，但模组自身没有中文语言文件，
            // 于是这里拿到的是原始键名。先按阶段枚举的 labelKey 精确翻译，再回落。
            String translated = translateStage(stage, label);
            if (translated != null) {
                label = translated;
            }
            if (label == null || label.isEmpty() || "null".equals(label)) {
                label = "RoadWeaver";
            }
            // 明确标出这是第三方模组的子进度，避免和主进度混淆
            label = "[RoadWeaver] " + label;
            float p = Math.max(0f, Math.min(1f, pct / 100f));
            return new LoadTrace.Step(label, LoadTrace.State.ACTIVE, p);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 用阶段枚举自带的 labelKey 去查我们自己的中文表。 */
    private static String translateStage(Object stage, String fallbackText) {
        if (!LangInjector.isChinese()) {
            return null;
        }
        try {
            Object labelKey = mLabelKey.invoke(stage);
            if (labelKey != null) {
                String zh = LangInjector.lookup(String.valueOf(labelKey));
                if (zh != null) {
                    return zh;
                }
            }
        } catch (Throwable ignored) {
        }
        // 兜底：返回文本本身就是键名时，直接按文本查（RoadWeaver 的 stageLabel 就返回键名）
        if (fallbackText != null && fallbackText.startsWith("gui.roadweaver.")) {
            return LangInjector.lookup(fallbackText);
        }
        return null;
    }
}
