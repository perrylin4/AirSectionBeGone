# AirSectionBeGone

AirSectionBeGone 是一个仅面向专用服务端的 Fabric 1.21.11 内存优化模组。它保留每个原版 `LevelChunkSection` 对象，只把其中可安全去重的数据换成共享不可变容器：

- 方块容器支持纯 `AIR`、纯 `CAVE_AIR` 和纯 `VOID_AIR`；
- 群系容器支持任意“整个 section 只有同一个已注册群系”的情况；
- 方块和群系分别判断、分别共享，不再要求二者同时满足条件。

## 当前约束

- Minecraft `1.21.11`
- Java `21`
- Fabric Loader `0.20.1` 或更高版本
- Fabric API `0.141.6+1.21.11` 或更高版本
- Lithium `0.21.4`（强依赖，精确版本）
- 仅专用服务端；客户端和单人世界不在支持范围内

## 安全模型

- 只处理已经成为 `LevelChunk` 的完整区块，不介入 `ProtoChunk` 或异步世界生成。
- 只折叠运行时类型恰好为原版 `LevelChunkSection` 的对象，避免破坏其他模组的 section 子类。
- `LevelChunkSection` 的对象身份、Lithium `SectionData` 和位置监听器始终保留；本模组不会再使 Lithium 监听器失效或重新绑定。
- 非空气方块写入共享方块容器前执行写时复制，并同时复制 Lithium 会被写路径修改的计数数组。
- 方块重新变成单一原版空气后延迟 200 tick 折叠，每个世界每 tick 最多折叠 64 个 section，避免高频更新时反复复制。
- 群系写入沿用原版 `recreate()` 路径产生可变容器，写完后再检查是否可以共享。
- 任何绕过所属区块、直接修改共享 palette 的行为都会抛出 `UnsupportedOperationException`，避免静默污染多个区块和存档。
- 不改变区块磁盘格式或网络格式；移除模组后仍可由原版格式正常读取。

实现与兼容性边界详见 [架构说明](docs/ARCHITECTURE.md)，本次受控 A/B 测试见 [基准结果](docs/BENCHMARK.md)。

诊断或复测时可用 JVM 参数 `-Dairsectionbegone.enabled=false` 保持模组加载但关闭全部折叠逻辑。

## 构建

```bash
./gradlew build
```

可分发 JAR 位于 `build/libs/`。项目不生成或发布 sources JAR。

## 许可证

[MIT](LICENSE)
