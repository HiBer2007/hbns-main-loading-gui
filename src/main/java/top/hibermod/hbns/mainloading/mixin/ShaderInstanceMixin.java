package top.hibermod.hbns.mainloading.mixin;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.hibermod.hbns.mainloading.client.LoadTrace;

/**
 * 着色器编译打点。
 *
 * <p>只在**确实正在重载资源**期间才登记这一条：着色器是在资源重载里编译的，
 * 重载结束后由 {@link ReloadableResourceManagerMixin} 统一收尾。
 * 没有重载时（例如进存档）不会凭空冒出「编译着色器」。
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceMixin {

    @Inject(method = "<init>*", at = @At("TAIL"))
    private void hbns$onShaderCtor(CallbackInfo ci) {
        if (LoadTrace.has("重载资源包")) {
            LoadTrace.begin("编译着色器");
        }
    }
}
