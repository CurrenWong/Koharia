# Crashlytics 已确认问题修复（2026-09-12）

本次针对已定位的五项问题，不处理尚缺环境证据的 Google 服务通知和小组件异常。

- 元数据编辑页仅持久化漫画 ID，在页面中异步重新读取数据库实体，避免导航状态序列化 JsonObject。读取期间显示加载状态；实体已移除时提示并返回。两个现有入口均已更新。
- 图片尚未就绪时的加载错误监听器显式转发给外部 ReaderPageImageView，避免递归调用自身。
- Komga 元数据缓存只接受 JSON，单条上限 2 MiB。对未知长度响应最多窥读上限加一个字节，超限跳过缓存，保留原响应供正常网络读取；文件路径判断不受查询参数影响。读取旧缓存也有大小限制；共享锁限制并发缓存复制及同名临时文件竞争。超过上限的数据仍可在线访问，但不新增此层离线缓存；不代表下游解析任意大响应不会占用内存。
- 分享/复制与单页保存兼容只有 bitmap 的 PDF/文档页，通过临时 PNG 导出并释放位图；异常在异步任务内部捕获，分享失败沿用已有保存错误反馈。临时导出文件在 finally 删除。分享不再先删除整个共享图片缓存目录，避免破坏并发导出和此前分享的文件。
- 本地与 Komga EPUB 在创建导航器前验证 readingOrder 非空；空文件显示本地化错误并关闭 publication。保留可读 EPUB 的资源生命周期。

## 验证

- 新增编辑页 Java 序列化往返测试。
- 缓存测试覆盖已知/未知长度超限、调用者仍可完整读响应、非 JSON 跳过及带参数文件路径排除；既有元数据缓存测试继续执行。
- EPUB 测试覆盖空出版物关闭、正常出版物不提前关闭。
- 隔离设备测试覆盖损坏图片一次错误回调、真实 PDF 页面无 stream 时导出 PNG。

日志位于本机 `.codex-work/crashlytics/`。修复未发布，新版本实际回报尚待观察，未提前关闭剩余 Crashlytics 问题。
最终结果：spotlessApply、spotlessCheck、verifyEInkMotion、:app:compileDebugKotlin、:app:testDebugUnitTest 全部通过；emulator-5554 / app.koharia.dev.devicefixture 的 ReaderCrashRegressionTest 2/2 通过。
