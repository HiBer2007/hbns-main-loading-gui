package top.hibermod.hbns.mainloading.mixin;

import net.minecraft.locale.Language;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.hibermod.hbns.mainloading.client.HbnsLanguage;
import top.hibermod.hbns.mainloading.client.LangInjector;

import java.lang.reflect.Field;

/**
 * 接管 {@link Language#inject(Language)}：把原版语言表包一层 {@link HbnsLanguage}。
 *
 * <p>这样做的原因是：原实现里的语料表往往不可变，直接改 Map 会失败；而包装查询是安全的，
 * 并且资源每次重载（{@code inject} 再次调用）都会自动重新包上，补丁不会丢。
 */
@Mixin(Language.class)
public abstract class LanguageMixin {

    private static Field hbns$instanceField;

    @Inject(method = "inject", at = @At("HEAD"), cancellable = true)
    private static void hbns$wrapLanguage(Language language, CallbackInfo ci) {
        if (language == null || language instanceof HbnsLanguage) {
            return;
        }
        try {
            if (hbns$instanceField == null) {
                Field f = Language.class.getDeclaredField("instance");
                f.setAccessible(true);
                hbns$instanceField = f;
            }
            boolean chinese = LangInjector.isChinese();
            hbns$instanceField.set(null, new HbnsLanguage(language));
            ci.cancel();
            LoggerFactory.getLogger("hbns-loading")
                    .info("[hbns-loading] language wrapper installed (chinese={})", chinese);
        } catch (Throwable t) {
            // 包装失败就退回原版行为，不影响游戏
            LoggerFactory.getLogger("hbns-loading")
                    .warn("[hbns-loading] language wrapper failed: {}", t.toString());
        }
    }
}
