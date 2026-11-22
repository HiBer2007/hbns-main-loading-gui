package top.hibermod.hbns.mainloading;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * 主加载界面模组（从 hbns_loading_screen 拆出）。
 *
 * <p>只负责「启动期主加载 GUI」这一件事：
 * <ul>
 *   <li>{@code MinecraftEarlyRenderMixin}：挂在渲染线程 tick 尾巴上，
 *       只要 FML 已经有真实进度、而 MC 还没有自己的遮罩/界面，就由我们直接画，
 *       因此它从<b>第一个渲染 tick</b> 就开始工作，不用等任何模组构造完成；</li>
 *   <li>{@code LoadingOverlayMixin}：MC 建立自己的 {@code LoadingOverlay} 后接管它；</li>
 *   <li>{@code LanguageMixin}：补齐第三方模组缺失的中文条目。</li>
 * </ul>
 *
 * <p>拆成独立模组的原因：这个模组的依赖面最小（不依赖任何界面逻辑），
 * 构造顺序尽量靠前；其余加载界面（世界载入/进服/断线等）留在 hbns_loading_screen。
 */
@Mod(value = HbnsMainLoading.MOD_ID, dist = Dist.CLIENT)
public final class HbnsMainLoading {
    public static final String MOD_ID = "hbns_main_loading_gui";

    public HbnsMainLoading() {
        org.slf4j.LoggerFactory.getLogger("hbns-loading").info(
                "[hbns-loading] main loading mod constructed at {}ms (uptime={}ms)",
                System.currentTimeMillis(),
                java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
    }
}
