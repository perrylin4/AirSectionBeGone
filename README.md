# AirSectionBeGone

AirSectionBeGone 是一个仅面向专用服务端的 Fabric 1.21.11 内存优化模组。他优化了LevelChunkSection、PoiSction以及HeightMap的内存占用

这个mod 99% 是GPT6.1和6.2sol生成，架构由6 Astra和6.2Astra做。后续由本人进行长达17h的review确保没有显著问题。接下来会在生产环境(ISLANDS各子服)运行来确保没有显著问题。

它保留每个原版 `LevelChunkSection` 对象，只把其中可安全去重的数据换成共享不可变容器：

- 方块容器支持纯 `AIR`、纯 `CAVE_AIR` 和纯 `VOID_AIR`；
- 群系容器支持任意“整个 section 只有同一个已注册群系”的情况；
- 方块和群系分别判断、分别共享，不再要求二者同时满足条件。

POI 缓存只保存实际的 `PoiSection`，已加载但为空的 section 由区块列完成标记表示，避免高世界为每个空层保存哈希条目。该优化保留 Lithium 的查询位图，并跟随 C2ME 的 POI 卸载清理列状态。

完整区块会在读盘或生成转换完成时共享内容相同的高度图存储。每张高度图保留独立身份，改变高度时按需复制，保存和发包分别取得独立快照。

## 当前约束

- Minecraft `1.21.11`
- Java `21`
- Fabric Loader `0.19.3` 或更高版本
- Fabric API `0.141.6+1.21.11` 或更高版本
- Lithium `0.21.4`（强依赖，精确版本）
- 仅专用服务端；客户端和单人世界不在支持范围内

## 安全模型

- 处理已经成为 `LevelChunk` 的完整区块，以及包装该完整区块的 `ImposterProtoChunk` 自己持有的隐藏 section 数组；真正的生成期 `ProtoChunk` 仍不处理。
- 只折叠运行时类型恰好为原版 `LevelChunkSection` 的对象，避免破坏其他模组的 section 子类。
- `LevelChunkSection` 的对象身份、Lithium `SectionData` 和位置监听器始终保留；本模组不会再使 Lithium 监听器失效或重新绑定。
- 通过 `LevelChunk` 或直接通过 `LevelChunkSection#setBlockState` 写入共享方块容器前都会执行写时复制，并同时复制 Lithium 会被写路径修改的计数数组。
- 方块重新变成单一原版空气后延迟 200 tick 折叠，每个世界每 tick 最多检查 64 个已加载 section，检查失败也占用预算，避免高频更新时反复复制和扫描尖峰。
- 群系写入沿用原版 `recreate()` 路径产生可变容器，写完后再检查是否可以共享。
- 绕过所属区块直接改变共享容器中的值会抛出 `UnsupportedOperationException`；同值写入允许返回原值。`copy()` 与 `recreate()` 返回 palette 独立的普通可变容器。
- 不改变区块磁盘格式或网络格式；移除模组后仍可由原版格式正常读取。

九场景测试覆盖虚空、原版地形和超平坦。开启 ASBG 后，标准高度的全堆可达对象占用下降 **1.26%–1.75%**，1024 高下降 **3.40%–4.16%**，4064 高下降 **11.62%–13.34%**。各场景的内存、MSPT、兼容性证据和实现细节见 [实现、兼容性与性能报告](docs/BENCHMARK.md)。

诊断或复测时可用 JVM 参数 `-Dairsectionbegone.enabled=false` 关闭全部优化。容器共享、高度图去重和 POI 稀疏缓存都跟随该总开关。

## 构建

```bash
./gradlew build
```

测试源码统一放在 `src/test/java`。用 `./gradlew test` 运行单元测试。服务端集成测试使用 `src/test/resources/fabric.mod.json` 注册测试入口，检查 POI、共享容器和高度图。

```bash
./gradlew runPoiTestServer -PpoiIntegrationTest -PpoiTestStage=write
./gradlew runPoiTestServer -PpoiIntegrationTest -PpoiTestStage=reload
```

运行前需要在 `build/poi-integration/` 准备测试服的 `eula.txt` 和 `server.properties`；测试服应仅绑定本机。两次运行必须使用同一个测试目录；`write` 会重置测试用的 POI 点位和占用状态，可重复运行。测试完成后自动停服，并由 Gradle 检查结果文件。可用 `-PpoiTestRunDir=build/poi-integration/compat` 指定独立目录，在该目录的 `mods/` 中放入兼容性测试模组。使用 `-PpoiTestOptimize=false` 关闭测试服的 ASBG 总开关，验证原版 POI 条目数量。开启优化时还会运行共享副本隔离、折叠扫描预算和无效写入入队检查。测试模组不会进入分发 JAR。

## 许可证

[MIT](LICENSE)
