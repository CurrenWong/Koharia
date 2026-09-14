# 连续滚动短页无法阅读修复（2026-09-12）

## 修复

- 连续章节安装不再只延迟 180ms 尝试一次。等待生命周期进入 STARTED，在当前 Readium 页面暂未就绪或脚本暂不可用时重试；同一资源的并发安装请求合并，避免反复取消。
- Readium 报告页面加载完成时再触发安装。退出页面、切换模式、目标章节变化后停止旧任务；脚本还会校验实际文档 origin/path，防止安装到其他章节。
- 图片交互脚本与预热脚本之间加入明确分号，修复实机发现的 `})()(function...)` TypeError。
- 保留连续模式禁止横向切章。连续内容未装载、当前可见章节不足一屏且没有任何纵向滚动空间时，明确的纵向手势可通过 Link 导航前往相邻章节。此保护不依赖恢复横向翻章，也不干预正常已安装的连续滚动。

## 验证

使用有效的生成 EPUB，包含短制作页、两章长正文和导航目录，通过项目 EpubReaderFragment、真实 Readium 导航器与 WebView 完成测试，而非仅用独立滚动控件。

- 短制作页自动接上长正文，预热脚本正常存在。
- 注入指向另一文档的安装脚本，确认被拒绝且现有内容不被覆盖。
- 实际纵向触摸滑动后 scrollTop 大于零。
- 故障注入：移除拼接结果、模拟文档尚未就绪，纵向滑动可离开短页；下一章自动装载连续内容。
- 通过目录 Link 跳到最后一章，确认安装的资源索引正确；重建 Activity 后内容仍可滚动。
- 原有四项连续/分页手势测试继续通过。

`spotlessApply`、`spotlessCheck`、`verifyEInkMotion`、`:app:compileDebugKotlin`、`:app:testDebugUnitTest` 通过。Android Studio 模拟器 emulator-5554 / app.koharia.dev.devicefixture 的完整阅读测试 1/1、手势测试 4/4 通过。

实机最初阻止了隔离包安装，没有执行实机回归。按用户指示改用模拟器；未覆盖实机 app.koharia.dev、未清除书库或阅读数据，模拟器测试 APK 保留。本次没有重新安装用户原书并声称实机已经修好。

日志：本机 `.codex-work/phone-scroll/final-check.log` 与 `final-device.log`。
修改未提交推送；工作区之前的修复保留。
