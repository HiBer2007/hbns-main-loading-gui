package top.hibermod.hbns.mainloading.client;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 加载进度数据层。
 *
 * <h2>原则：只显示真实数据，不猜</h2>
 * <p>之前版本把「重载资源包」「编译着色器」常驻在列表里，导致进单人存档时也显示这两条——
 * 而那时根本没有重载/编译，属于编造。现在改成：
 * <ul>
 *   <li>每个加载界面进入时 {@link #reset()}，旧步骤不残留；</li>
 *   <li>步骤必须由**真实事件**开启，并在事件真正结束时置为 DONE；</li>
 *   <li>拿不到进度的界面（如 {@code ReceivingLevelScreen}）只显示不确定进度条，不编步骤。</li>
 * </ul>
 */
public final class LoadTrace {

    public enum State {
        PENDING, ACTIVE, DONE
    }

    public record Step(String name, State state, float progress) {
    }

    private static final Object LOCK = new Object();
    private static final List<Step> LOCAL = new ArrayList<>();

    private LoadTrace() {
    }

    /** 进入新界面时清空，避免上一个界面的步骤残留。 */
    public static void reset() {
        synchronized (LOCK) {
            LOCAL.clear();
        }
    }

    public static void step(String name, State state, float progress) {
        synchronized (LOCK) {
            for (int i = 0; i < LOCAL.size(); i++) {
                if (LOCAL.get(i).name().equals(name)) {
                    LOCAL.set(i, new Step(name, state, progress));
                    return;
                }
            }
            LOCAL.add(new Step(name, state, progress));
        }
    }

    public static void begin(String name) {
        step(name, State.ACTIVE, -1f);
    }

    public static void done(String name) {
        step(name, State.DONE, 1f);
    }

    /** 最后一个 ACTIVE 的本地步骤名（只含界面自己打点的，不含 FML 进度条）。 */
    public static String activeLocal() {
        synchronized (LOCK) {
            for (int i = LOCAL.size() - 1; i >= 0; i--) {
                if (LOCAL.get(i).state() == State.ACTIVE) {
                    return LOCAL.get(i).name();
                }
            }
        }
        return null;
    }

    /** 把最后一个 ACTIVE 的本地步骤标记为完成。 */
    public static void completeActiveLocal() {
        String name = activeLocal();
        if (name != null) {
            done(name);
        }
    }
    /** 移除某个步骤（例如事件证明它并没有发生）。 */
    public static void remove(String name) {
        synchronized (LOCK) {
            LOCAL.removeIf(s -> s.name().equals(name));
        }
    }

    public static boolean has(String name) {
        synchronized (LOCK) {
            return LOCAL.stream().anyMatch(s -> s.name().equals(name));
        }
    }

    // ---- FML 进度（只有启动早期有数据，进游戏后为空）----

    private static Method mGetProgress;
    private static Method mSteps;
    private static Method mProgress;
    private static Method mLabel;
    private static Method mGetText;
    private static boolean reflectTried;

    private static void initReflection() {
        if (reflectTried) {
            return;
        }
        reflectTried = true;
        try {
            Class<?> snm = Class.forName("net.neoforged.fml.loading.progress.StartupNotificationManager");
            mGetProgress = snm.getMethod("getCurrentProgress");
            Class<?> pm = Class.forName("net.neoforged.fml.loading.progress.ProgressMeter");
            mSteps = pm.getMethod("steps");
            mProgress = pm.getMethod("progress");
            mLabel = pm.getMethod("label");
            Class<?> msg = Class.forName("net.neoforged.fml.loading.progress.Message");
            mGetText = msg.getMethod("getText");
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Step> fmlSteps() {
        initReflection();
        List<Step> out = new ArrayList<>();
        if (mGetProgress == null) {
            return out;
        }
        try {
            List<Object> meters = (List<Object>) mGetProgress.invoke(null);
            for (Object meter : meters) {
                int steps = (Integer) mSteps.invoke(meter);
                float p = (Float) mProgress.invoke(meter);
                String text;
                try {
                    Object msgObj = mLabel.invoke(meter);
                    text = msgObj == null ? "" : String.valueOf(mGetText.invoke(msgObj));
                } catch (Throwable t) {
                    text = "";
                }
                if (text.isEmpty()) {
                    continue;
                }
                if (steps == 0) {
                    out.add(new Step(text, State.ACTIVE, -1f));
                } else {
                    float v = Math.min(1f, p);
                    out.add(new Step(text, v >= 1f ? State.DONE : State.ACTIVE, v));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 当前要显示的步骤：FML 真实进度条在前，界面自己打点的在后，最后是接管来的第三方进度。 */
    public static List<Step> merged() {
        List<Step> out = new ArrayList<>(fmlSteps());
        synchronized (LOCK) {
            out.addAll(LOCAL);
        }
        // 第三方模组的加载进度（目前接管 RoadWeaver 的初始道路生成）
        Step roadWeaver = RoadWeaverProbe.step();
        if (roadWeaver != null) {
            out.add(roadWeaver);
        }
        return tagRoadWeaver(out);
    }

    /**
     * 给所有来自 RoadWeaver 的步骤统一加 {@code [RoadWeaver]} 前缀。
     *
     * <p>它有时会把自己的阶段塞进 FML 的进度条里（而不是只走我们接管的快照），
     * 那种路径拿到的文字没有前缀，这里按「键名或译文」识别后补上。
     */
    private static List<Step> tagRoadWeaver(List<Step> in) {
        for (int i = 0; i < in.size(); i++) {
            Step s = in.get(i);
            String n = s.name();
            if (n == null || n.startsWith("[RoadWeaver]")) {
                continue;
            }
            if (n.startsWith("gui.roadweaver.") || LangInjector.isRoadWeaverLabel(n)) {
                in.set(i, new Step("[RoadWeaver] " + n, s.state(), s.progress()));
            }
        }
        return in;
    }
}

