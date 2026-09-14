# Komga 加载、进度同步和连续滚动反馈定位（2026-09-12）

本轮为定位与复现，未修改这些业务行为。工作区先前的 Crashlytics 修复保留。新增可控诊断测试，不使用用户服务器凭据或操作手动安装包。

## Komga 启动加载慢

已通过实际 KomgaApi 调用的拦截器测试确认：书架调用 getInProgressBookProgress(includeCompleted=true)，发送 `/api/v1/books?unpaged=true&deleted=false`，没有 read_status 筛选，不限当前书库和当前 25 个可见条目。返回的是整库 BookDto（含元数据），因此库越大、网络越慢，成本越高。关闭“阅读进度”显示会跳过这条路径，与用户关闭部分徽标后稍快的反馈一致；不能将所有“未读/大小”设置视作同一个开关。

KomgaLibraryScreenModel 初始化还会强制 reloadKomgaState；getBrowseLibraries 内实际读取完整筛选选项，包含书库顺序、书库、合集、流派、标签、出版社、作者共七类请求。正常分页列表另外加载。进度请求缓存仅 5 秒；forceRefresh 可绕过它。首次 init 与偏好 changes 的首次发射还有重复刷新入口，但具体网络次数受缓存、请求合并和时序影响，不能仅数 launch 次数当作实测请求量。

重要限制：对照 v0.4.2 与 v0.4.5，书架 ScreenModel 和漫画 ReaderViewModel 的这些关键逻辑没有差异，不能据此声称它们是 0.4.5 新引入的回退。未取得反馈者的库规模、服务器版本、请求耗时和旧版版本号，未进行同库同设备的旧/新版性能对照。此次已定位高成本路径，并未完整复现“0.4.5 比旧版慢”的耗时差。

## 多设备进度不同步

1. **首次比较失败后持续禁止上传（代码确认）**：ReaderViewModel 在发送 GET 前将 chapterId 加入 remoteProgressChecksStarted；GET 异常时直接返回，不从该集合移除，也不放行 remoteProgressWritesAllowed。同一次阅读会话再进入此检查会被去重挡住，之后 pushPageProgressIfAllowed 一直跳过。网络恢复本身不构成重试。影响漫画阅读路径；不能直接推广到 EPUB。
2. **上传瞬时失败没有持久补偿（代码确认）**：KomgaProgressSyncService.pushPageProgress 对 PATCH 的异常仅记日志；没有 LANraragi 那样的持久待同步队列。用户读完退出前最后一次上传失败时，另一设备得不到最新位置。首次 GET 成功但后续 PATCH 失败是另一条独立路径。
3. **同地址多账号选错连接（已复现）**：下层 updateBookProgress 不接收 sourceId，只按 bookUrl 从在线来源中选择第一个同 baseUrl 的 KomgaSource。测试配置 account-a/account-b，实际 PATCH 使用 account-a。上层已有 sourceId，但未向下传递。仅当同一安装配置多个同地址连接时成立；若两台设备各只有一个连接，则此项不能解释反馈。

建议修复时保持远端较新进度保护：失败应进入可重试状态而非直接放开覆盖；按 connectionId+bookId 持久化实际阅读事件，并使上传明确使用对应连接。真实反馈仍需判断是 GET 失败、PATCH 被拒、不同账号，还是纯显示缓存未刷新。

## 连续滚动与左右翻页

关闭点按翻页只改变点击区域的导航模式，没有独立的左右滑动开关。漫画分页 Pager 和 EPUB Readium 拖动均不由该点击设置控制。EPUB InputListener.onDrag 返回 false，将手势交回 Readium，并在拖动开始时取消项目翻页动画，因此它也不走点按的同一动画路径。

已核对 Readium 3.3.0 实际源码：

- R2WebView 在横向位移超过 touchSlop 时即可进入拖动状态，没有先要求横向位移大于纵向位移。
- scrollMode 下抬手时，只要垂直总位移绝对值小于 200 **物理像素**，并处在水平边界，就可调用 scrollLeft/scrollRight。
- R2BasicWebView 的滚动分支会请求上一/下一 XHTML 资源；现成配置 disablePageTurnsWhileScrolling 可阻止它，默认 false。
- 项目的 epubNavigatorConfiguration 没有设置该选项。

连续滚动的额外高风险路径：EpubContinuousScroll 在当前原生资源中插入相邻章节 iframe，滚到另一章时只更新 continuousScrollLocator 和 host 进度，不改变原生 navigator 所持的资源。横向滑动仍相对原生资源翻章；native locator 变化后 observeNavigator 会清掉连续滚动位置并重新安装滚动窗口。例如原生仍是第 2 章，但已在拼接内容内读到第 5 章，原生“下一章”仍可能选第 3 章。这解释大跨度回跳与来回滑不能原路返回的机制，但当前未在用户原书上完成整条实际复现，不能把所有滑杆/目录错位都归到这一个原因。

漫画 Webtoon 使用垂直 RecyclerView，并非上述 Readium 资源分页架构。在未缩放、列表中段的横向滑动测试中保持原第 10 项，没有复现跳章；不包含缩放、章首章尾、双击等其他状态。

## 测试与边界

- KomgaReportedIssuesTest：验证未分页整库请求、短期缓存/强刷，以及同地址连接的实际 PATCH 请求头。
- ContinuousSwipeReproTest：漫画 Webtoon 控件横滑对照；使用实际 Readium R2WebView 的滚动模式斜滑，再启用依赖已有开关对照。使用生成长文与记录资源切换的监听器，属于控件层复现，不冒充完整 EPUB 阅读器端到端测试。
- 普通 WebView + R2ViewPager 的初始替代实验未复现，后续根据源码改为实际 R2WebView；未保留错误结论。
- 设备测试目标 emulator-5554 / app.koharia.dev.devicefixture；手动包、用户库与另一个设备不变。测试 APK 保留。

日志：`.codex-work/reported-issues-unit.log`、`.codex-work/reported-issues-device.log`。未提交推送本轮测试。
最终结果：2 项网络层单元测试、2 项模拟器控件测试全部通过；EPUB 默认配置斜滑触发跳章，启用 disablePageTurnsWhileScrolling 后同样手势不跳章。格式检查通过。
