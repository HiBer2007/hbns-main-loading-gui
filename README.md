# HBNS Main Loading GUI

HBNS V3 整合包的**主加载界面**替换模组（从 `hbns_loading_screen` 拆出）。

## 负责的界面

| 界面 | 接管方式 |
|---|---|
| 启动期主加载遮罩（`LoadingOverlay`） | mixin `LoadingOverlayMixin`，HEAD 取消原版渲染，由本模组绘制 |
| 启动早期（MC 还没有遮罩时） | mixin `MinecraftEarlyRenderMixin`，在渲染线程 tick 上直接绘制 |
| 第三方模组缺失的中文条目 | mixin `LanguageMixin` + `HbnsLanguage` 包装语言表 |

世界载入、进服、断线等界面在另一个模组 **hbns_ingame_loading_gui**。

## 为什么拆成独立模组

- 主加载界面要在**最早**的时机工作：本模组依赖面最小，混入配置独立（`hbns_main_loading_gui.mixins.json`），
  mixin 在类加载时生效，不依赖模组构造顺序。
- 早期窗口（GLFW 自绘）在另一个 jar `hbns-early-loading-screen`，作为 FML 的 `ImmediateWindowProvider`。

## 数据来源

- 进度：`StartupNotificationManager.getCurrentProgress()`（FML 真实进度条）
- 重载：`ReloadableResourceManager.createReload` 的真实完成事件
- RoadWeaver：反射读取其 `InitialGenerationProgressTracker.snapshot(true)`，转成带 `[RoadWeaver]` 前缀的子进度条

## 构建

```
gradlew build -x test --offline
```

产物：`build/libs/hbns_main_loading_gui-1.0.0.jar`

## 许可

All Rights Reserved（与 HBNS 其它加载界面模组一致）。
