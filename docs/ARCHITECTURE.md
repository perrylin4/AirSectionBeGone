# 架构与兼容性边界

## 折叠条件

只有同时满足以下条件的 section 才会被替换：

1. section 的运行时类型恰好是原版 `LevelChunkSection`，避免丢失其他模组子类携带的状态；
2. 4096 个方块状态全部与 `Blocks.AIR.defaultBlockState()` 为同一对象；
3. 64 个群系单元全部是当前服务端群系注册表中的 `Biomes.THE_VOID`；
4. section 已属于服务端 `LevelChunk`。

检查和索引均通过区块自身的 section 数组及高度访问器完成，因此不假定 `min_y`、世界高度或 section 数量。

## 写时复制

`LevelChunk#setBlockState` 写入非空气方块前会把占位 section 深复制为普通 `LevelChunkSection`，替换数组槽位后再交回原版逻辑。`ChunkAccess#fillBiomesFromNoise` 作用于完整区块时也会先解冻占位对象，以覆盖 `/fillbiome` 等已知写路径。

最后一个非空气方块被移除，或群系填充结束后，符合条件的普通 section 会重新折叠。替换 section 身份前会使 Lithium 的位置监听器失效，使其在下一次查询时重新绑定到新对象。

未知模组若直接调用占位 section 或共享 `PalettedContainer` 的修改接口，会立即得到 `UnsupportedOperationException`。这是有意设计的兼容性断言；静默允许这类写入可能同时污染大量区块。

## Lithium

本项目针对 Lithium 0.21.4 的 section 计数与方块变化监听接口实现。不可变 section 共享：

- 方块标志计数数组；
- 随机刻 mini-section 计数数组。

监听回调仍按 section 对象和世界坐标分别维护，避免把不同区块位置的追踪器错误合并。依赖被精确锁定到 0.21.4；升级 Lithium 前必须重新审查其内部接口。

## C2ME

折叠只发生在 `LevelChunk`，不会触碰 C2ME 并行世界生成使用的 `ProtoChunk`。占位 section 的 `copy()` 返回普通、可变、深复制的 section，因此 C2ME/原版异步保存快照不会持有可修改的共享数据。

目标兼容版本：C2ME 0.3.6.0。上线前仍应对实际整合包执行生成、保存、重启与 `/fillbiome` 回归测试。

## Fantasy

动态世界通过实际 `ServerLevel` 的群系注册表和高度访问器处理。Fantasy 默认虚空维度可命中优化；使用自定义群系的 Fantasy 世界不会被误判为 `THE_VOID`。

目标兼容版本：Fantasy 0.7.0。

## FerriteCore

FerriteCore 8.2.0 默认不替换 `LevelChunkSection`，也不修改本项目共享的 palette 所有权。它检测到 Lithium 后会关闭与 Lithium 冲突的小型 `ThreadingDetector` 路径；本项目的不可变 palette 仍将 `acquire/release` 实现为无操作，并独立拒绝所有写入。

## 明确不处理

- 客户端与集成服务端；
- 非完整区块的世界生成阶段；
- 自定义空气方块、`CAVE_AIR`、`VOID_AIR` 或自定义虚空群系；
- 其他模组的 `LevelChunkSection` 子类；
- 通过反射或 Unsafe 修改私有字段的代码。

## 已执行的验证

2026-11-10 在 Java 21、Minecraft 1.21.11、Fabric Loader 0.20.1、Fabric API 0.141.6、Lithium 0.21.4 环境完成以下测试：

- Gradle `build`、生产 JAR remap 与专用服务端启动；
- 纯 `THE_VOID` 超平坦世界的生成、强制加载、保存、停服和重新读取；
- `AIR -> STONE -> AIR` 写时复制往返；
- `THE_VOID -> PLAINS -> THE_VOID` 的 `/fillbiome` 写时复制往返；
- 与 C2ME 0.3.6.0、Fantasy 0.7.0、FerriteCore 8.2.0 联合加载，并在 C2ME 异步保存开启时执行上述写入、flush 与停服保存。

一次受控测试中，堆内同时存在 1176 个独立占位 section，而共享对象保持为 2 个 `ImmutablePalettedContainer` 和 1 个 `LithiumSectionData.SectionData`。放置方块后占位数降至 1175，移除后恢复为 1176。
