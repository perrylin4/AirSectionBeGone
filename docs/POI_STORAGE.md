# POI 稀疏缓存

`PoiSectionStorageMixin` 仅对 `PoiManager` 生效。`SectionStorage.storage` 保留原来的 map 对象（启用 Lithium ai.poi 时仍是 ListeningLong2ObjectOpenHashMap），加载区块列时只写入存在的 PoiSection，不保存 Optional.empty() 条目。PoiSection 即使当前没有点，也继续保留其有效性、占用及保存语义。

## 加载与读取

原版 loadedChunks 在同步解包前就会加入标记，不能用来判断数据已经就绪。本模组将该集合替换为 LongOpenHashSet 的子类 PoiColumnTracker，另存按列编码的 readyColumns。原版 pendingLoads、锁和调度顺序保持不变。

- 开始解包时清除该列的完成标记；有记录的 section 正常写入原 map，空记录不入表。
- 整列解包正常返回后，确保 Lithium 列位图存在，再发布 readyColumns 标记。异常返回不发布。
- 原版 get(long) 和 Lithium 的 lithium$getElementAt(long) 读到 null 时，仅在高度合法且整列就绪时返回共享 Optional.empty()；未加载仍返回 null。
- 热查询使用 ModifyReturnValue，不创建 CallbackInfoReturnable，不创建临时 SectionPos/ChunkPos 对象，也不扫描 section。
- 原版 getOrCreate、setDirty 和 writeChunk 保留；保存流程本来就跳过缺少的 section。没有新磁盘格式。

## Lithium 0.21.4

普通 PoiSection 的写入/移除继续触发原监听 map 的回调，维护存在记录的高度位图。纯空列省去了原版的全部空条目写入，因而需要在加载完成时显式建立一个空 BitSet，否则 Lithium 会在加载后仍找不到列索引。

空列解包沿用原版的 section 高度范围，每层遇到空值时从缓存移除对应位置。这也能清理重载前遗留的 section。Lithium 索引为每个空列使用独立的 `BitSet(0)`，首次写入 section 时才扩容。如果其他模组已将 `loadedChunks` 换成自定义集合，该 POI 管理器沿用原有存储行为。

Lithium 的 columns 是其 Mixin 添加的私有字段。缓存的反射 Field 只在列加载、卸载和整体清理边界使用，不在查询热路径使用；它不经过 Minecraft 名称映射。只有目标实际实现 Lithium 的 RegionBasedStorageSectionExtended 接口时才解析该字段，所以单独关闭 Lithium ai.poi 时仍能使用原版查询路径。Lithium 版本继续精确固定；升级前需要复查字段和新增查询入口。

## C2ME 与生命周期

C2ME 0.3.6.0.0 的 POI 卸载会先保存并移除 section，再调用原版 loadedChunks.remove(column)。PoiColumnTracker 在这次移除中清除完成标记和 Lithium 列索引，纯空列同样处理。没有按名称注入 C2ME 的私有卸载方法，也没有静态的世界引用或定时清理任务。

集合的直接 remove、迭代器 remove、批量移除与 clear 都处理完成标记。fastutil 迭代器处理 wrapped key 时可能回调 remove(long)，通过卸载版本计数避免重复通知。原版和 C2ME 对 POI 状态的线程访问约束保持不变，本模组不提供额外的任意线程并发读写保证。

新加载仍由原 pendingLoads 管理；从待加载表移除的旧 future 完成后，不会因本模组增加的回调重新发布状态。完成标记属于各自 PoiManager，随世界释放。

## 开关与验证

默认开启，跟随 `-Dairsectionbegone.enabled` 总开关；设为 false 会关闭本模组全部优化。测试参数 `-PpoiTestOptimize=false` 也传递这个总开关。

运行 C2ME 兼容性测试时传入 `-PpoiTestRequireC2me=true`。写入和重启读取两个阶段都会检查实际卸载入口，缺失时测试失败。

`src/test/java` 下的 `PoiColumnTrackerTest` 验证加载中和完成状态的区别，以及普通、迭代和批量卸载。同一源码集中的 `PoiIntegrationTest` 由 `runPoiTestServer -PpoiIntegrationTest` 启动。它使用 1728 高度（108 层）的专用 PoiManager，验证 201 个空列、异步预取及丢弃、范围检查、POI 增删、占用和释放、附近查询、实际 C2ME 卸载和保存后重启读取。关闭 POI 优化时，section 条目数应为实际加载列数 × 108（附近搜索可能额外加载邻列）。开启时仅保留 3 个实际 PoiSection。

该测试用于验证 POI 功能。

2026-11-15 功能验证：6 个单元测试通过；Lithium 0.21.4 单独、Lithium + C2ME 0.3.6.0.0 + FerriteCore 8.2.0 + ServerCore 1.5.15，以及关闭 Lithium ai.poi 的配置均通过写入与重启读取。另以关闭 ASBG POI 优化的四模组组合完成对照测试。相同的最初 200 个空列对应原版 map 的 21,600 条记录，稀疏 map 为 0 条；列完成标记和 Lithium 位图仍正常保留。测试环境为 Fabric Loader 0.19.3、Java 25，分发字节码目标为 Java 21；测试模组不进入分发 JAR。
