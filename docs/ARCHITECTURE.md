# 架构与兼容性边界

## 核心原则

本模组不再替换 `LevelChunkSection`。每个区块槽位原有的 section 对象、Lithium `SectionData` 和方块变化监听器都保持原身份，只替换 section 内部的 `states` 或 `biomes` 引用。

这样既保留 Lithium 以对象身份和世界位置维护的追踪关系，又能去掉大量内容相同的 palette、storage 与辅助对象。

## 独立折叠条件

方块与群系独立检查；任何一侧不满足条件都不会妨碍另一侧共享。

方块容器只有在以下条件全部满足时折叠：

1. section 的运行时类型恰好是原版 `LevelChunkSection`；
2. `hasOnlyAir()` 为真；
3. 4096 个状态为同一个原版默认状态：`AIR`、`CAVE_AIR` 或 `VOID_AIR`。

群系容器只有在以下条件全部满足时折叠：

1. section 的运行时类型恰好是原版 `LevelChunkSection`；
2. 64 个群系单元指向同一个群系键；
3. 该群系存在于当前服务端的动态群系注册表中。

零位 palette 可以常数时间确认；非零位 palette 分别最多扫描 4096 个方块或 64 个群系单元。高度和 section 索引始终通过区块/世界访问器计算，不假定原版世界高度。

## 共享池上界

共享池按群系注册表对象隔离。每个注册表会立即建立 3 个空气方块容器，并按需为实际遇到的均匀群系建立容器。

若注册表共有 `B` 个群系，则共享 `ImmutablePalettedContainer` 的理论上界是 `B + 3`，而不是 section 数量。通常同一服务端的多个维度使用同一个动态注册表，因此也能跨维度复用。

## 方块写路径

完整路径如下：

1. `LevelChunkMixin` 在 `LevelChunk#setBlockState` 的 `HEAD` 调用 `prepareBlockWrite`。
2. 原版若发现 section 纯为空气且新状态仍是任意空气，会直接返回，不会触碰 palette。
3. 对真正可能写入的非空气状态，本模组取消该 section 的待折叠任务；若 `states` 是共享容器，则用 `copy()` 换成普通可变容器。
4. 同时复制当前 section 自己的 Lithium `countsByFlag` 与 `randomTickableBlocksByY` 数组，之后原版/Lithium 可以按原路径更新它们。
5. 原版完成方块、计数、高度图、光照和监听器通知。
6. `RETURN` 处若新状态是三种受支持空气之一，且 section 已经只含空气，则加入延迟折叠队列。
7. 连续 200 tick 未被非空气写入后，队列重新确认 4096 个状态确实同一种，再换回共享容器。每个世界每 tick 最多完成 64 次折叠，并最多弹出 256 个候选，避免单 tick 尖峰。

整个过程不会替换 `LevelChunkSection`，所以无需调用 Lithium 的 `onChunkSectionInvalidated`，也无需维护额外的占位 section 回调表。

## 群系写路径

`LevelChunkSection#fillBiomesFromNoise` 原本就会对当前只读容器调用 `recreate()`、写入一个新的可变容器，再替换 `biomes` 字段。因此共享群系容器不需要在写入前主动解冻。

`ChunkAccess#fillBiomesFromNoise` 返回后，本模组扫描完整 `LevelChunk` 的群系；每个重新变成均匀群系的 section 都会指向对应共享容器。方块容器不参与这次扫描。

## Lithium

每个 section 继续拥有独立的 `LithiumSectionData.SectionData` 与 `ChunkSectionChangeCallback`。共享的只有在不可变空气状态下不会变化的两组数组：

- 方块标志计数 `short[] countsByFlag`；
- 随机刻 mini-section 计数 `byte[] randomTickableBlocksByY`。

写时复制会先克隆这两组数组，再允许 Lithium 的原始写路径修改。因此监听器不会失效，追踪器也不会因为 section 身份变化而丢失或重绑。

项目仍精确依赖 Lithium 0.21.4；升级 Lithium 前必须重新审查这些内部接口。

## 不可变容器

`ImmutablePalettedContainer` 允许读取、序列化、`copy()` 和 `recreate()`，拒绝 `set`、`getAndSet`、`onResize` 与网络反序列化等原地修改操作。`copy()`/`recreate()` 返回普通可变 `PalettedContainer`。

直接绕过 `LevelChunk` 调用 section/palette 修改接口的模组不属于兼容写路径；异常是有意的失败保护，防止一次非法写入污染所有共享该容器的区块。

## 生命周期与并发边界

- 折叠只针对服务端 `LevelChunk`，不修改 `ProtoChunk`，因此不会介入原版或 C2ME 的并行生成阶段。
- 待折叠队列按 `ServerLevel` 隔离，在世界卸载时清除；服务端停止时同时清空共享池。
- 对其他模组的 `LevelChunkSection` 子类一律跳过。
- 动态维度通过实际 `ServerLevel` 的注册表、高度与 section 数组处理。
- 客户端、集成服务端、反射/Unsafe 私有字段写入以及绕过原版区块 API 的直接 palette 写入不在支持范围内。

## 已执行验证

2026-11-11 在 Minecraft 1.21.11、Fabric Loader 0.20.1、Fabric API 0.141.6、Lithium 0.21.4 环境完成：

- `compileJava` 与专用服务端真实启动；
- `AIR`、`CAVE_AIR`、`VOID_AIR` 各自执行“整 section 折叠 → 写入 `STONE` → 写回原空气 → 延迟重折叠”；
- `/fillbiome` 在 `PLAINS` 与 `DESERT` 等非虚空均匀群系间往返；
- 4064 高空世界的 `y=2000`、`y=-2000` 写入验证；
- 同一存档、固定 2 GiB 堆下的优化开/关 A/B 测试。

详细方法和数字见 [BENCHMARK.md](BENCHMARK.md)。
