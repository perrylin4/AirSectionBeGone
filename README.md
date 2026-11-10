# AirSectionBeGone

AirSectionBeGone 是一个仅面向专用服务端的 Fabric 1.21.11 内存优化模组。它将完整区块中“所有方块均为原版 `AIR`，且所有群系均为原版 `THE_VOID`”的 `LevelChunkSection` 折叠为轻量、不可变的写时复制占位对象。

## 当前约束

- Minecraft `1.21.11`
- Java `21`
- Fabric Loader `0.20.1` 或更高版本
- Fabric API `0.141.6+1.21.11` 或更高版本
- Lithium `0.21.4`（强依赖，精确版本）
- 仅专用服务端；客户端和单人世界不在支持范围内

## 安全模型

- 只处理已经成为 `LevelChunk` 的完整区块，不介入 `ProtoChunk` 或异步世界生成。
- 只接受原版 `Blocks.AIR.defaultBlockState()` 与注册表中的 `Biomes.THE_VOID`，不使用宽松的 `isAir()` 判断。
- 每个 section 槽位保留独立对象身份，但共享不可变方块/群系 palette 以及 Lithium 的只读零计数数据。
- 原版已知写入口会先生成可变副本并替换回所属区块，再执行修改。
- 任何绕过所属区块、直接修改占位 section 或共享 palette 的行为都会抛出 `UnsupportedOperationException`，以失败优先的方式避免静默污染多个区块和存档。
- 不改变区块磁盘格式或网络格式；移除模组后仍可由原版格式正常读取。

实现与兼容性边界详见 [架构说明](docs/ARCHITECTURE.md)。

## 构建

```bash
./gradlew build
```

可分发 JAR 位于 `build/libs/`。项目不生成或发布 sources JAR。

## 许可证

[MIT](LICENSE)
