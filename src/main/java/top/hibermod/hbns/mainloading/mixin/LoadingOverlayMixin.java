package top.hibermod.hbns.mainloading.mixin;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.hibermod.hbns.mainloading.client.LoadTrace;
import top.hibermod.hbns.mainloading.client.OverlayRenderer;

/**
 * 完整接管原版加载遮罩的渲染与生命周期。
 *
 * <p>HEAD 直接 cancel：原版「红底 Mojang logo + 进度条」一点都画不出来，状态机由我们驱动。
 * 这解决了之前「淡出途中闪一下原生红色加载界面」的问题——原来是 TAIL 覆盖，
 * 一旦我们提前 return，原版底图立刻露出来。
 *
 * <p>流程：reload 未完成 → 画自己的界面；reload 完成 → 保持 500ms → 500ms 淡出 →
 * 调 onFinish 让 Minecraft 移除遮罩，直接进主菜单。
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    @Shadow
    @Final
    private ReloadInstance reload;

    @Shadow
    @Final
    private Consumer<Optional<Throwable>> onFinish;

    /** 背景延迟 0.3s 后开始淡出；前景（文字/进度条）立即淡出；两者各 1s。 */
    private static final long BG_DELAY_MS = 300L;
    private static final long FADE_MS = 1000L;
    /** 【实验开关】true = reload 完成即刻退出（不做我们的淡出），用于确认是否与原生过渡冲突。 */
    private static final boolean INSTANT_EXIT = true;

    private long hbns$doneAt = -1L;
    /** 每个遮罩实例只重置一次，避免残留上一个界面的步骤。 */
    private static Object hbns$lastOverlay;
    private boolean hbns$finished;
    private static long hbns$logAt;
    /** 启动阶段只允许一个遮罩正常走完；之后同一个启动流程里再出现的新遮罩直接结束，避免重复显示。 */
    private static boolean hbns$startupFinished;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void hbns$render(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();
        OverlayRenderer.beginScreen(this);
        try {
            // 主加载界面首帧推一次整合包版本消息（左下角滚动区）
            top.hibermod.hbns.mainloading.client.PackInfo.postMessageOnce();
            if (hbns$lastOverlay != this) {
                hbns$lastOverlay = this;
                LoadTrace.reset();
                // 通知早期窗口：主加载界面已经开始出画面，可以把交接窗口 B 亮出来了。
                // 早期窗口线程一直在等这个信号，收到后才停掉自己并销毁窗口 A。
                System.setProperty("hbns.loading.handoff", "1");
                // 记录「主加载界面开始渲染」的绝对时刻，用于核对它相对各模组初始化的先后顺序
                org.slf4j.LoggerFactory.getLogger("hbns-loading").info(
                        "[hbns-loading] main loading overlay first frame at {}ms (uptime={}ms, screen={})",
                        System.currentTimeMillis(),
                        java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime(),
                        net.minecraft.client.Minecraft.getInstance().screen);
                // 启动阶段已经收尾过一次 → 这是重复遮罩，直接结束，不再显示
                if (hbns$startupFinished && net.minecraft.client.Minecraft.getInstance().screen == null) {
                    try {
                        net.minecraft.client.Minecraft.getInstance().setOverlay(null);
                    } catch (Throwable ignored) {
                    }
                    if (onFinish != null) {
                        onFinish.accept(Optional.empty());
                    }
                    return;
                }
            }
            boolean done = reload != null && reload.isDone();
            if (done && hbns$doneAt < 0L) {
                hbns$doneAt = System.currentTimeMillis();
            }

            float progress = done ? 1f : safeProgress();
            // 这个遮罩本身就是「资源重载」界面：步骤可能被首帧 reset 掉，按真实状态补回，
            // 并把真实进度（reload.getActualProgress）喂给这一条。
            String stepName = top.hibermod.hbns.mainloading.client.Fonts.ready() ? "重载资源包" : "加载资源";
            if (!done && !LoadTrace.has(stepName)) {
                LoadTrace.begin(stepName);
            }
            LoadTrace.step(stepName,
                    done ? LoadTrace.State.DONE : LoadTrace.State.ACTIVE, progress);
            // fg：文字与进度条，reload 完成即开始淡出
            // bg：背景，晚 0.3s 开始；两者各 1s，总时长 1.3s
            float fgAlpha = 1f;
            float bgAlpha = 1f;
            if (hbns$doneAt > 0L) {
                long elapsed = System.currentTimeMillis() - hbns$doneAt;
                fgAlpha = 1f - Math.min(1f, elapsed / (float) FADE_MS);
                if (elapsed > BG_DELAY_MS) {
                    bgAlpha = 1f - Math.min(1f, (elapsed - BG_DELAY_MS) / (float) FADE_MS);
                }
            }

            if (done && !hbns$finished) {
                hbns$finished = true;
                // 原版是靠 setOverlay(null) 移除遮罩的，onFinish 只负责收尾通知；
                // 只调 onFinish 遮罩会永远留在屏幕上（之前卡住的根因）。
                try {
                    net.minecraft.client.Minecraft.getInstance().setOverlay(null);
                } catch (Throwable ignored) {
                }
                if (onFinish != null) {
                    onFinish.accept(Optional.empty());
                }
                top.hibermod.hbns.mainloading.client.OverlayRenderer.dispose();
                if (!top.hibermod.hbns.mainloading.client.Fonts.ready()) {
                    hbns$startupFinished = true;
                }
                return;
            }


            // reload 完成后冻结步骤：不再逐条消失，统一交给整体 alpha 淡出
            float in = OverlayRenderer.fadeIn(this);
            OverlayRenderer.render(g, g.guiWidth(), g.guiHeight(), progress,
                    LoadTrace.merged(), "HiBer 北冰洋服务器 · NeoForge 21.1.266",
                    fgAlpha * in, bgAlpha * in, done);
        } catch (Throwable t) {
            LoggerFactory.getLogger("hbns-loading").error("[hbns-loading] loading overlay render failed", t);
        }
    }

    private float safeProgress() {
        try {
            return Math.max(0f, Math.min(1f, reload.getActualProgress()));
        } catch (Throwable t) {
            return -1f;
        }
    }
}










