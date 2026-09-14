# 仿真翻页结束时缩放（2026-09-14）

## 原因及修复

ComicPageFlipController 已等待目标页面就绪，并再等待两帧才进行 PixelCopy。但它以源页可见图片边界建立 GL Surface，又独立按目标页边界裁剪目标截图。GL 把两张纹理铺到同一 Surface 上，目标截图因此被拉伸成源页的比例；移除 Surface 后恢复目标页真实比例，出现缩放跳变。

适应宽度下，长页填满可视高度、短页上下留空时即可满足触发条件。其他适应模式中两页可见边界不同时也存在同一问题。这不是已证实的图片解码尺寸计算滞后。

修复让目标截图与源截图使用同一个窗口矩形。截图包含该区域内的真实留白，目标图片不再被拉伸；区域外由已经切到目标页的真实 Pager 显示。保留就绪等待、截图大小上限、前后翻纹理分工和已有黑帧保护。

## 回归范围

ComicPageFlipGeometryDeviceTest 使用真实 Pager、PixelCopy 和 ComicPageFlipController/GL 动画，构造全高与半高页面，验证前翻及后翻。检查源/目标纹理大小一致、目标纹理像素保持窗口位置，以及动画完成后目标页正确。测试内容是几何图案，不是用户原漫画的 SSIV 全链路复现。

手工复核：漫画设置适应宽度、仿真动画，在两张宽高比例明显不同的页面间前翻/后翻，观察结束瞬间是否仍缩放；再检查相同比例的页面、横屏和双页模式。

测试使用 emulator-5554 的 app.koharia.dev.devicefixture，保留已安装应用及数据。修改未提交推送。

## 结果

- spotlessApply、spotlessCheck、:app:compileDebugKotlin 通过。
- emulator-5554 隔离测试 1/1 通过，包含真实 GL 前翻及后翻，未走动画失败兜底。
- 前翻源/目标截图均为 1080×2400；后翻均为 1080×1200，对应窗口 y=600..1800。像素位置检查通过。
- 静态测试页需要主动 invalidate 保护窗口以驱动后续帧提交；此前两次运行在此阶段超时，未计为通过。该处理仅在测试内。
- 构建初次并发造成增量缓存冲突，停止 Gradle 后顺序执行成功，未删除用户数据或源码。

日志：.codex-work/comic-geometry-final-check.log、comic-geometry-final-device.log；XML 保留在 app/build/lanraragi/device-results/eu.kanade.tachiyomi.ui.reader.viewer.pager.ComicPageFlipGeometryDeviceTest。
