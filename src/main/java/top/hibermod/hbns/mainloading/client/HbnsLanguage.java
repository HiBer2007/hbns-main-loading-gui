package top.hibermod.hbns.mainloading.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * 语言表包装层。
 *
 * <p>原版 {@link Language} 的实现里语料表常常是不可变 Map，直接 put 会失败，所以这里
 * 用**包装**的方式：查询时先看我们补的条目，没有再交给原实现。
 *
 * <p>通过 mixin 挂到 {@link Language#inject(Language)} 上，资源每次重载都会重新包一次，
 * 保证补丁不会因为重载而丢失。
 *
 * <p>注意：<b>是否中文在查询时实时判断</b>。包装发生的时机极早（语言管理器还没就绪），
 * 如果在构造时就定死「是不是中文」，会出现「补了但不生效」的问题。
 */
public final class HbnsLanguage extends Language {

    private final Language base;
    private final Map<String, String> extra = new HashMap<>(LangInjector.zhEntries());

    public HbnsLanguage(Language base) {
        this.base = base;
    }

    public Language base() {
        return base;
    }

    private boolean active() {
        return LangInjector.isChinese();
    }

    @Override
    public String getOrDefault(String key, String fallback) {
        if (active()) {
            String v = extra.get(key);
            if (v != null) {
                return v;
            }
        }
        return base.getOrDefault(key, fallback);
    }

    @Override
    public boolean has(String key) {
        return (active() && extra.containsKey(key)) || base.has(key);
    }

    @Override
    public boolean isDefaultRightToLeft() {
        return base.isDefaultRightToLeft();
    }

    @Override
    public FormattedCharSequence getVisualOrder(FormattedText text) {
        return base.getVisualOrder(text);
    }

    @Override
    public Map<String, String> getLanguageData() {
        Map<String, String> merged = new HashMap<>(base.getLanguageData());
        if (active()) {
            merged.putAll(extra);
        }
        return merged;
    }
}
