package top.hibermod.hbns.mainloading.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;

/**
 * 游戏内加载界面渲染器。
 *
 * <p>这是早期窗口渲染器（hbns-early-loading-screen 的 {@code EarlyRenderer}）的
 * <b>等价实现</b>：同一套配色、同一套「自底向上堆叠 + 分裂/淡出动画」、同一套
 * AWT 文字管线（AWT 光栅化 → RGBA 上传 → 按像素 blit）。区别只有一个：
 * 这里跑在 Minecraft 的渲染管线里，所以用 {@link GuiGraphics} 的 fill/blit 落笔，
 * 而不是自己开 GL 上下文。
 *
 * <p>坐标系是 MC 的 GUI 逻辑坐标；文字按 GUI 缩放倍数栅格化，绘制时换算回逻辑尺寸，
 * 保证屏幕像素 1:1，字号与早期窗口一致。
 */
public final class OverlayRenderer {

    private static final int BG_TOP = 0x080C18;
    private static final int BG_BOTTOM = 0x141C30;
    private static final int ACCENT = 0x5B8CFF;
    private static final int TRACK = 0x1B2440;
    private static final int OK = 0x4ADE80;
    private static final int TEXT = 0xE8ECF8;
    private static final int DIM = 0x93A0C0;

    private static final Map<String, ResourceLocation> TEX = new HashMap<>();
    private static final Map<String, int[]> TEX_SIZE = new HashMap<>();
    private static Font baseFont;
    private static Font awtFont;
    private static int lastPx = -1;

    /** 与早期窗口用同一份 Monocraft 像素字体；加载失败才退回系统字体。 */
    private static Font base() {
        if (baseFont == null) {
            try (var in = OverlayRenderer.class.getResourceAsStream("/Monocraft.ttf")) {
                if (in != null) {
                    baseFont = Font.createFont(Font.TRUETYPE_FONT, in);
                }
            } catch (Throwable ignored) {
            }
            if (baseFont == null) {
                baseFont = new Font(pickFont(), Font.PLAIN, 12);
            }
        }
        return baseFont;
    }
    private static int seq;

    private static final List<Anim> ANIMS = new ArrayList<>();
    private static long lastMs;

    private OverlayRenderer() {
    }

    private static final class Anim {
        String label;
        float shown;
        float target;
        boolean done;
        float y;
        float targetY;
        float alpha;
        boolean seen;
    }

    /**
     * 画一屏。
     *
     * @param progress 0..1，<0 表示不确定
     * @param alpha    整体透明度（淡出用）
     */
    public static void render(GuiGraphics g, int width, int height, float progress,
                              List<LoadTrace.Step> steps, String footer,
                              float alpha, float bgAlpha, boolean freeze) {
        if (alpha <= 0.003f && bgAlpha <= 0.003f) {
            return;
        }
        // 中文补充由 HbnsLanguage 在语言注入时接管，这里不再需要额外动作
        // 背景单独一套 alpha（比前景晚 0.3s 开始淡出）
        if (bgAlpha > 0.003f) {
            drawBackdrop(g, width, height, bgAlpha);
        } else {
            g.fill(0, 0, width, height, 0xFF000000);
        }

        int margin = Math.max(8, width / 20);
        int barW = Math.max(60, Math.min(width - margin * 2, (int) (width * 0.55)));
        int barH = Math.max(2, height / 120);
        int gap = Math.max(4, height / 40);
        int fontSize = Math.max(5, (int) (height / 48f * 4f / 3f));
        int x0 = (width - barW) / 2;
        int bottomMargin = Math.max(12, height / 12);
        // 每个块 = 文字高度 + 条高（文字与条之间）+ 条高（条本身）+ 少量行距。
        // 必须按文字真实高度算，否则字号变大后标签会压到下一根条上（之前就是这样叠在一起）。
        int textH = textHeight("Hg", fontSize, TEXT);
        int blockH = textH + barH * 2 + Math.max(3, gap / 2);
        int yBar0 = height - bottomMargin - barH;

        // ---- 堆叠动画（与 EarlyRenderer 同一套规则）----
        long now = System.currentTimeMillis();
        float dt = lastMs == 0 ? 0.016f : Math.min(0.1f, (now - lastMs) / 1000f);
        lastMs = now;

        int shown = Math.min(steps.size(), 8);
        for (Anim a : ANIMS) {
            a.seen = false;
        }
        if (!freeze) {
            for (int i = 0; i < shown; i++) {
                LoadTrace.Step s = steps.get(steps.size() - shown + i);
                Anim a = null;
                for (Anim x : ANIMS) {
                    if (x.label.equals(s.name())) {
                        a = x;
                        break;
                    }
                }
                if (a == null) {
                    a = new Anim();
                    a.label = s.name();
                    a.y = yBar0;
                    a.alpha = 0f;
                    a.shown = s.progress();
                    ANIMS.add(a);
                }
                a.seen = true;
                a.target = s.progress();
                a.done = s.state() == LoadTrace.State.DONE;
            }
            // 位置按「首次出现顺序」固定，步骤增减时不会互相穿插重叠
            int slot = 0;
            for (Anim a : ANIMS) {
                if (a.seen) {
                    a.targetY = yBar0 - slot * blockH;
                    slot++;
                }
            }
            for (Anim a : ANIMS) {
                if (!a.seen) {
                    a.alpha -= dt * 6f;
                } else {
                    float k = 1f - (float) Math.exp(-12f * dt);
                    a.alpha += (1f - a.alpha) * k;
                    a.y += (a.targetY - a.y) * Math.min(1f, k * 1.6f);
                    if (a.shown >= 0f && a.target >= 0f) {
                        a.shown += (a.target - a.shown) * Math.min(1f, k * 1.2f);
                    } else {
                        a.shown = a.target;
                    }
                }
            }
            ANIMS.removeIf(a -> a.alpha <= 0.01f && !a.seen);
        }
        for (Anim a : ANIMS) {
            if (a.alpha <= 0.01f) {
                continue;
            }
            float aa = a.alpha * alpha;
            int y = Math.round(a.y);
            g.fill(x0, y, x0 + barW, y + barH, withAlpha(TRACK, aa));
            if (a.shown < 0f) {
                double cycle = (now % 1600) / 1600.0;
                int seg = Math.max(barH * 4, barW / 4);
                int off = (int) ((barW + seg) * cycle) - seg;
                int ax = Math.max(x0, x0 + off);
                int bx = Math.min(x0 + barW, x0 + off + seg);
                if (bx > ax) {
                    g.fill(ax, y, bx, y + barH, withAlpha(ACCENT, aa));
                }
            } else {
                int fill = (int) (barW * Math.max(0f, Math.min(1f, a.shown)));
                if (fill > 0) {
                    g.fill(x0, y, x0 + fill, y + barH,
                            withAlpha(a.done ? OK : ACCENT, aa));
                }
            }
            int th = textHeight(a.label, fontSize, TEXT);
            // 文字底边离条顶只留「一个条的厚度」，随尺寸解算
            drawText(g, a.label, x0, y - barH - th, fontSize, TEXT, aa);
        }

        int fpx = Math.max(5, (int) (height / 60f * 4f / 3f));
        drawText(g, footer, Math.max(2, margin - 5),
                Math.min(height - fpx, height - fpx - margin / 2 + 5), fpx, DIM, alpha);

        // 总进度百分比：放右下角（与页脚同一水平带），不挤在进度条旁
        if (progress >= 0f) {
            int pct = (int) (Math.min(1f, progress) * 100);
            String s = pct + "%";
            int fpx2 = Math.max(5, (int) (height / 60f * 4f / 3f));
            int th = textHeight(s, fpx2, TEXT);
            int tw = textWidth(s, fpx2, TEXT);
            int pctY = Math.min(height - th, height - fpx2 - margin / 2 + 5);
            lastPercentageY = pctY;
            drawText(g, s, Math.max(2, width - margin - tw), pctY, fpx2, TEXT, alpha);
        }
    }

    private static void drawBackdrop(GuiGraphics g, int width, int height, float alpha) {
        int bands = 32;
        for (int i = 0; i < bands; i++) {
            float t = i / (float) (bands - 1);
            int c = lerp(BG_TOP, BG_BOTTOM, t);
            int y0 = height * i / bands;
            int y1 = height * (i + 1) / bands;
            g.fill(0, y0, width, y1, withAlpha(c, alpha));
        }
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (((int) (ar + (br - ar) * t)) << 16)
                | (((int) (ag + (bg - ag) * t)) << 8)
                | ((int) (ab + (bb - ab) * t));
    }

    // ==================== 文字（AWT → 纹理） ====================

    private static int textHeight(String text, int px, int rgb) {
        int[] s = ensure(text, px, rgb);
        return s == null ? px : Math.max(1, s[1]);
    }

    private static int textWidth(String text, int px, int rgb) {
        int[] s = ensure(text, px, rgb);
        return s == null ? 0 : Math.max(1, s[0]);
    }

    /**
     * 画字。
     *
     * <p>首次资源重载完成之前字体图集还没建好（原版此时也不画字），只能用 AWT 贴图顶；
     * 重载完成后 {@link Fonts#ready()} 为真，就**改用游戏引擎自己的字体**
     * （{@code GuiGraphics.drawString}），不再自绘——加载流程结束后的默认路径。
     */
    private static void drawText(GuiGraphics g, String text, int x, int y, int px, int rgb, float alpha) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (Fonts.ready()) {
            int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
            float scale = Math.max(0.5f, px / 9f);
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1f);
            g.drawString(Minecraft.getInstance().font, text, 0, 0,
                    (a << 24) | (rgb & 0xFFFFFF), false);
            g.pose().popPose();
            return;
        }
        int[] s = ensure(text, px, rgb);
        if (s == null) {
            return;
        }
        ResourceLocation rl = TEX.get(key(text, px, rgb));
        if (rl == null) {
            return;
        }
        int rawW = s[2];
        int rawH = s[3];
        float sc = s[4];
        // GUI blit 的颜色由 GuiGraphics 自己的顶点色决定，RenderSystem.setShaderColor
        // 对它无效（这正是之前「进度条淡了、文字不淡」的原因）。
        g.setColor(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha)));
        // 把坐标系缩到物理像素，纹理 1:1 落屏——与早期窗口的 quad 绘制等价
        g.pose().pushPose();
        g.pose().scale(1f / sc, 1f / sc, 1f);
        g.blit(rl, Math.round(x * sc), Math.round(y * sc), rawW, rawH, 0, 0, rawW, rawH, rawW, rawH);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static String key(String text, int px, int rgb) {
        return px + "|" + Integer.toHexString(rgb) + "|" + text;
    }

    /** 返回 {逻辑宽, 逻辑高, raster宽, raster高, guiScale}。 */
    private static int[] ensure(String text, int px, int rgb) {
        int guiScale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        String k = key(text, px, rgb) + "|" + guiScale;
        int[] cached = TEX_SIZE.get(k);
        if (cached != null) {
            return cached;
        }
        try {
            int rasterPx = px * guiScale;
            if (awtFont == null || lastPx != rasterPx) {
                awtFont = new Font(pickFont(), Font.PLAIN, rasterPx);
                lastPx = rasterPx;
            }
            // 用 GlyphVector 轮廓自绘：填充 + 按 字号/16 的线宽描边，
            // 笔画粗细随字号等比缩放——否则小字号糊成一团、大字号又太细。
            java.awt.font.FontRenderContext frc =
                    new java.awt.font.FontRenderContext(null, true, true);
            java.awt.font.GlyphVector gv = awtFont.createGlyphVector(frc, text);
            float stroke = Math.max(0f, rasterPx / 16f - 1f);
            java.awt.geom.Rectangle2D pb = gv.getPixelBounds(frc, 0, 0);
            int w = Math.max(1, (int) Math.ceil(pb.getWidth()) + 6);
            int h = Math.max(1, (int) Math.ceil(pb.getHeight()) + 6);
            java.awt.Shape outline = gv.getOutline(3 - (float) pb.getX(), 3 - (float) pb.getY());

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gg = img.createGraphics();
            gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            gg.setColor(new Color(rgb | 0xFF000000, true));
            gg.fill(outline);
            if (stroke > 0.05f) {
                gg.setStroke(new java.awt.BasicStroke(stroke,
                        java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                gg.draw(outline);
            }
            gg.dispose();

            int[] pxAr = new int[w * h];
            img.getRGB(0, 0, w, h, pxAr, 0, w);
            NativeImage ni = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            for (int yy = 0; yy < h; yy++) {
                for (int xx = 0; xx < w; xx++) {
                    int argb = pxAr[yy * w + xx];
                    int a = (argb >>> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int gch = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    ni.setPixelRGBA(xx, yy, (a << 24) | (b << 16) | (gch << 8) | r);
                }
            }
            DynamicTexture tex = new DynamicTexture(ni);
            tex.setFilter(false, false);
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(
                    "hbns_loading_screen", "t/" + (seq++));
            Minecraft.getInstance().getTextureManager().register(rl, tex);
            TEX.put(key(text, px, rgb), rl);
            int[] info = new int[]{Math.max(1, w / guiScale), Math.max(1, h / guiScale), w, h, guiScale};
            TEX_SIZE.put(k, info);
            return info;
        } catch (Throwable t) {
            return null;
        }
    }
    // ==================== 左下角滚动消息（与早期窗口同一数据源） ====================

    private static java.lang.reflect.Method mGetMessages;
    private static java.lang.reflect.Method mAge;
    private static java.lang.reflect.Method mMessage;
    private static java.lang.reflect.Method mGetText;
    private static boolean msgReflectTried;

    private static void initMessageReflection() {
        if (msgReflectTried) {
            return;
        }
        msgReflectTried = true;
        try {
            Class<?> snm = Class.forName("net.neoforged.fml.loading.progress.StartupNotificationManager");
            mGetMessages = snm.getMethod("getMessages");
            Class<?> ageMsg = Class.forName("net.neoforged.fml.loading.progress.StartupNotificationManager$AgeMessage");
            mAge = ageMsg.getMethod("age");
            mMessage = ageMsg.getMethod("message");
            Class<?> msgCls = Class.forName("net.neoforged.fml.loading.progress.Message");
            mGetText = msgCls.getMethod("getText");
        } catch (Throwable ignored) {
        }
    }

    /** 返回 {文本, alpha} 列表，顺序「旧 → 新」，与官方 RenderElement 一致。 */
    private static List<String[]> collectStartupMessages() {
        initMessageReflection();
        List<String[]> out = new java.util.ArrayList<>();
        if (mGetMessages == null) {
            return out;
        }
        try {
            Object raw = mGetMessages.invoke(null);
            if (!(raw instanceof List<?> list) || list.isEmpty()) {
                return out;
            }
            int n = list.size();
            for (int i = n - 1; i >= 0; i--) {
                Object ageMsg = list.get(i);
                int age = (Integer) mAge.invoke(ageMsg);
                Object msg = mMessage.invoke(ageMsg);
                String text = String.valueOf(mGetText.invoke(msg));
                if (text == null || text.isEmpty()) {
                    continue;
                }
                float fade = (4000f - age - (i - 4) * 1000f) / 5000f;
                fade = Math.max(0f, Math.min(1f, fade));
                if (fade < 0.01f) {
                    continue;
                }
                out.add(new String[]{text, Float.toString(fade)});
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /**
     * 左下角滚动消息。字号随窗口高度缩放（不写死），叠在页脚上方。
     *
     * @param bottomReserve 底部为页脚保留的高度
     */
    private static void drawStartupMessages(GuiGraphics g, int width, int height, int bottomReserve) {
        List<String[]> msgs = collectStartupMessages();
        if (msgs.isEmpty()) {
            return;
        }
        // 字号 = 窗口高度的固定比例，随分辨率自动缩放
        int px = Math.max(6, (int) (height / 62f));
        int lineH = px + Math.max(2, px / 3);
        int x = Math.max(8, width / 70);
        int maxW = Math.max(80, width - x * 2);
        int y = height - bottomReserve - msgs.size() * lineH;
        for (String[] m : msgs) {
            float alpha = Float.parseFloat(m[1]);
            if (alpha <= 0.01f) {
                continue;
            }
            drawText(g, fit(text(m[0], px, maxW), px, TEXT), x, y, px, TEXT, alpha);
            y += lineH;
        }
        // 占位引用，避免未使用告警
        if (g == null) {
            return;
        }
    }

    /** 按像素宽度截断（超出补省略号）。 */
    private static String fit(String text, int px, int maxW) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (textWidth(text, px, TEXT) <= maxW) {
            return text;
        }
        String ell = "…";
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (textWidth(text.substring(0, mid) + ell, px, TEXT) <= maxW) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return text.substring(0, Math.max(0, lo)) + ell;
    }

    private static String text(String s, int px, int maxW) {
        return s;
    }

    // ==================== 界面级状态隔离 ====================

    private static Object lastScreenKey;

    /**
     * 每次渲染开始时调用。
     *
     * <p>作用是把动画状态（堆叠位置、透明度、上一帧时间）**绑定到当前界面实例**：
     * 界面被强制切换时（例如保存世界后立刻回主菜单），旧界面残留的元素不会带到下一个界面，
     * 这解决了「上一个界面的动画残留到下一次显示、且再也销毁不掉」的问题。
     */
    public static void beginScreen(Object screen) {
        long now = System.currentTimeMillis();
        if (screen != lastScreenKey) {
            lastScreenKey = screen;
            ANIMS.clear();
            lastMs = now;
            TEX.clear();
            TEX_SIZE.clear();
        }
    }
    /** 右下角总百分比文字的 y 坐标（其它界面要对齐到它上方时用）。 */
    public static int percentageY(int height) {
        int fpx2 = Math.max(5, (int) (height / 60f * 4f / 3f));
        int margin = Math.max(16, height / 8);
        return height - fpx2 - margin / 2 + 5;
    }
    // ==================== 供各界面复用的基础绘制原语 ====================

    private static int lastPercentageY = -1;
    private static Object fadeKey;
    private static long fadeStart;

    /** 界面切换时的淡入系数（0→1，300ms），让界面出现不生硬。 */
    /** 最近一次渲染时右下角百分比文字的 y（没画过则为 -1）。 */
    public static int lastPercentageY() {
        return lastPercentageY;
    }

    public static float fadeIn(Object screen) {
        long now = System.currentTimeMillis();
        if (screen != fadeKey) {
            fadeKey = screen;
            fadeStart = now;
        }
        return Math.min(1f, (now - fadeStart) / 300f);
    }

    /** 只画背景渐变（自定义布局的界面用）。 */
    public static void backdrop(GuiGraphics g, int width, int height, float alpha) {
        if (alpha > 0.003f) {
            drawBackdrop(g, width, height, alpha);
        }
    }

    /** 在指定位置画一行字（自定义布局的界面用）。 */
    public static void text(GuiGraphics g, String s, int x, int y, int px, int rgb, float alpha) {
        drawText(g, s, x, y, px, rgb, alpha);
    }

    /** 在指定位置画一条进度条（自定义布局的界面用）。 */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float progress,
                           boolean done, float alpha) {
        g.fill(x, y, x + w, y + h, withAlpha(TRACK, alpha));
        if (progress < 0f) {
            double cycle = (System.currentTimeMillis() % 1600) / 1600.0;
            int seg = Math.max(h * 4, w / 4);
            int off = (int) ((w + seg) * cycle) - seg;
            int ax = Math.max(x, x + off);
            int bx = Math.min(x + w, x + off + seg);
            if (bx > ax) {
                g.fill(ax, y, bx, y + h, withAlpha(ACCENT, alpha));
            }
            return;
        }
        int fill = (int) (w * Math.max(0f, Math.min(1f, progress)));
        if (fill > 0) {
            g.fill(x, y, x + fill, y + h, withAlpha(done ? OK : ACCENT, alpha));
        }
    }

    /** 淡出完成后调用：释放全部动态文字纹理，清空动画状态。 */
    public static void dispose() {
        try {
            var tm = Minecraft.getInstance().getTextureManager();
            for (ResourceLocation rl : TEX.values()) {
                tm.release(rl);
            }
        } catch (Throwable ignored) {
        }
        TEX.clear();
        TEX_SIZE.clear();
        ANIMS.clear();
        lastMs = 0L;
    }

    private static String pickFont() {
        String[] candidates = {"Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI", "Dialog"};
        var available = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        for (String c : candidates) {
            for (String a : available) {
                if (a.equalsIgnoreCase(c)) {
                    return a;
                }
            }
        }
        return Font.SANS_SERIF;
    }
}





















