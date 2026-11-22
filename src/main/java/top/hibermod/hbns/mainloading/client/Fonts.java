package top.hibermod.hbns.mainloading.client;

/**
 * 字体就绪标志。
 *
 * <p>Minecraft 的资源（含字体图集）在**首次资源重载**之后才可用。首次重载期间连原版自己
 * 都不画文字，我们也只能用 AWT 光栅化的贴图顶上。
 *
 * <p>重载一旦完成，就切回**游戏引擎自己的绘制与字体**（{@code GuiGraphics.drawString}），
 * 之后的资源包重载、载入存档、加入服务器全部走引擎绘制——不再用 GL 自绘文字。
 */
public final class Fonts {

    private static volatile boolean ready;

    private Fonts() {
    }

    public static boolean ready() {
        return ready;
    }

    public static void markReady() {
        ready = true;
    }
}
