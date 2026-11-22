package top.hibermod.hbns.mainloading.mixin;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.hibermod.hbns.mainloading.client.LoadTrace;
import top.hibermod.hbns.mainloading.client.OverlayRenderer;

/**
 * 渲染线程级「尽早绘制」。
 *
 * <p>主加载 GUI 之前要等 MC 自己建立 {@code LoadingOverlay} 才出现，而那个时刻已经在
 * 全部模组构造完之后了。这里改成挂在渲染线程的 tick 上：只要
 * <ul>
 *   <li>FML 已经有真实进度（{@link LoadTrace#merged()} 非空），且</li>
 *   <li>MC 还没有自己的遮罩 / 界面 / 世界（说明仍处于启动早期），</li>
 * </ul>
 * 就由我们直接把主加载界面画上去。mixin 在类加载时就生效，不依赖模组构造顺序，
 * 所以它从第一个渲染 tick 就开始工作。
 *
 * <p>一旦 MC 建立了自己的 {@code LoadingOverlay}（或任何界面/世界），这里自动让位，
 * 交给 {@code LoadingOverlayMixin} 等后续接管，不会重复绘制。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftEarlyRenderMixin {

    private static boolean hbns$loggedFirst;

    @Inject(method = "runTick", at = @At("TAIL"))
    private void hbns$earlyStartupDraw(boolean renderLevel, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        try {
            if (mc.getOverlay() != null || mc.screen != null || mc.level != null) {
                return;
            }
            List<LoadTrace.Step> steps = LoadTrace.merged();
            if (steps.isEmpty()) {
                return;
            }
            if (!hbns$loggedFirst) {
                hbns$loggedFirst = true;
                LoggerFactory.getLogger("hbns-loading").info(
                        "[hbns-loading] early startup draw started at {}ms (uptime={}ms, steps={})",
                        System.currentTimeMillis(),
                        java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime(),
                        steps.size());
            }
            GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            OverlayRenderer.beginScreen("hbns-early-startup");
            OverlayRenderer.render(g, g.guiWidth(), g.guiHeight(), -1f, steps,
                    "HiBer 北冰洋服务器 · NeoForge 21.1.266", 1f, 1f, false);
            g.flush();
        } catch (Throwable t) {
            LoggerFactory.getLogger("hbns-loading")
                    .warn("[hbns-loading] early startup draw failed: {}", t.toString());
        }
    }
}
