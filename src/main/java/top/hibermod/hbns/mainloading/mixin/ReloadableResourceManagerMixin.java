package top.hibermod.hbns.mainloading.mixin;

import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.hibermod.hbns.mainloading.client.Fonts;
import top.hibermod.hbns.mainloading.client.LoadTrace;

/**
 * 资源重载打点。
 *
 * <p>这是**真实事件**：只有真的调用 {@code createReload} 才出现这一条，重载的 future
 * 完成时置为 DONE（不靠计时器猜）。首次重载完成后把 {@link Fonts} 标记为就绪，
 * 之后所有界面切回引擎字体绘制。
 */
@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {

    @Inject(method = "createReload", at = @At("HEAD"))
    private void hbns$beforeReload(CallbackInfoReturnable<ReloadInstance> cir) {
        LoadTrace.begin("重载资源包");
    }

    @Inject(method = "createReload", at = @At("RETURN"))
    private void hbns$afterReload(CallbackInfoReturnable<ReloadInstance> cir) {
        ReloadInstance instance = cir.getReturnValue();
        if (instance == null) {
            LoadTrace.remove("重载资源包");
            return;
        }
        instance.done().whenComplete((v, err) -> {
            LoadTrace.done("重载资源包");
            // 着色器编译发生在重载期间，重载结束即其结束（只有真编译过才会有这条）
            if (LoadTrace.has("编译着色器")) {
                LoadTrace.done("编译着色器");
            }
            Fonts.markReady();
        });
    }
}
