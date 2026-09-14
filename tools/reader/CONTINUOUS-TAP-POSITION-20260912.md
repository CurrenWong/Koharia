# 连续滚动点击后跳到底部（2026-09-12）

## 现场记录

在 Android Studio 的 emulator-5554 上只读诊断用户安装的 app.koharia.dev。用户点击嵌入扉页的文字/空白后，父 WebView 仍是封面、连续内容 owner index 仍为 0，但 scrollTop 从约 61.7 变成 13907.4，等于 scrollHeight 14781 减去视口高度约 874。现场扉页是 HTML 文字，不是图片。自动点击未稳定复现，以上跳动来自用户手动复现。

嵌入章节执行了服务端注入的 readium-reflowable.js，并创建自己的 window.readium。Android JavaScript bridge 对所有 frame 可见，子章节的生命周期和点击坐标会进入父阅读器。现场伴随 locator 从扉页回到封面高进度、再到末章，以及重复安装连续内容。重复 refresh 还会销毁、重建已加载的 iframe，进一步扰动焦点和滚动位置。

## 修复

- 使用 DOMParser 解析嵌入章节，只移除与主文档相同的 Readium 注入脚本。资源服务器与 Readium 静态资源可能不同源，因此通过主文档实际脚本地址匹配，不能假设与书籍同源。
- 保留书籍自带脚本、样式和资源相对地址，传递主阅读器的排版设置。
- 嵌入页面的普通点击转换为父视口坐标，再交给现有 Readium 点击处理；保留文本选择、控件操作、图片预览拦截和章节链接跳转。
- 相同配置的 refresh 不重建内容；设置变化时更新已有 frame，而非卸载重载。

## 验证

真实 EpubReaderFragment / Readium / WebView 测试书包含短制作页、扉页、两章长正文和目录。隔离包 app.koharia.dev.devicefixture，在 emulator-5554 上执行。

- 主文档保留 Readium，子文档不再启动 Readium；书籍自带脚本仍运行。
- 实际触摸扉页文字、空白和图片后，scrollTop 偏差不超过 3 CSS px，owner 和 iframe 身份保持不变。
- 文字及空白点击各传入宿主一次，坐标在视口内；图片点击走图片预览接口。
- 相对章节链接、纵向滑动、未就绪短页的纵向兜底、目录跳转、Activity 重建通过。
- 完整阅读集成测试 1/1、连续/分页手势回归 4/4 通过（合计 5 项，0 失败）。
- spotlessApply、spotlessCheck、verifyEInkMotion、compileDebugKotlin、app 单元测试通过。

日志：本机 .codex-work/image-scroll/final-checks.log 和 final-device.log。设备测试 XML 另存于 app/build/lanraragi/device-results。

本次未覆盖用户手动安装的 Koharia，未清除或卸载任何包；隔离包保留。原书在用户包中捕获了故障，修复后使用生成 EPUB 回归，尚未在用户原书上重新验证。修改未提交、未推送。

## 原书人工复核

安装包含修复的版本后，开启 EPUB 连续滚动，在封面下方点击原扉页文字和中间空白；应只执行配置的点击操作、不跳到底部。再检查图片预览关闭后位置、上下跨章滚动、目录跳转及退出重进后的进度。

## 注释反馈补查

用户随后反馈点击注释不能显示。只读检查用户包中的两个图标注释：链接均为仅含 fragment 的相对地址，目标存在于嵌入章节（index 13），不在 owner 封面文档中；子 frame 仍运行旧版 Readium 初始化。这会使原生 Readium 按 owner 文档解析注释编号，取不到注释。

上一轮已实现的点击转发会先以子章节 baseURI 补全 href，再由主 Readium 处理，因此无需再新增一套注释解析器。本轮增加专项测试，确认同章、跨章、多看兼容格式和图片图标注释均向宿主提交一次正确内容，不跳章、不移动滚动位置，图标不误开图片预览。此测试验证到注释弹窗宿主回调，未声称已经在用户原书中完成修复版弹窗的人工验证。

专项测试使用生成 EPUB 和隔离测试包。格式检查与 app 编译通过；日志为 .codex-work/image-scroll/footnote-checks.log、footnote-repro.log、footnote-final-device-2.log。最终两项集成测试通过（0 失败）。早期测试仅检查 WebView 几何可见范围，误选实际不可见的控件，触摸被系统拒绝；增加 isShown 条件后注释与原有触摸测试均通过。
