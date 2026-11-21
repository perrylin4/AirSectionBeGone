# AirBeGone 实现、兼容性与性能报告

AirBeGone是 Minecraft 1.21.11 专用服务端的 Fabric 内存优化模组。它共享完整区块中的空气方块容器、均匀群系容器和内容相同的高度图存储，并将已加载但缺少 POI 数据的高度段改为稀疏缓存。

## 结果摘要

2026-11-20 的九场景测试覆盖虚空、原版地形和超平坦。每对从同一母档复制，使用相同的优化模组，只切换 AirBeGone。标准高度全堆节省 **1.26%–1.75%**，1024 高节省 **3.40%–4.16%**，4064 高节省 **11.62%–13.34%**。

九场景的静置平均 MSPT 差值为 **−0.025 至 +0.043 ms**，稳态编辑差值为 **−0.041 至 +0.103 ms**。普通地形的三档高度中，集中首次写入的单个 tick 多耗时 **3.477–5.856 ms**。每个场景最终采用一对有效运行，稳态数据用于描述该轮观测，首次写入数据用于观察单个批量写入 tick 的成本。

## 九场景对照测试

### 软件环境和负载

| 项目 | 本轮配置 |
| --- | --- |
| 游戏与加载器 | Minecraft 1.21.11、Fabric Loader 0.19.3、Fabric API 0.141.6+1.21.11 |
| Java 与 JVM | Azul Zulu 25.0.4.1、G1 GC、`-Xms2G -Xmx2G -XX:ActiveProcessorCount=6`。AirBeGone 分发字节码目标为 Java 21 |
| 更名前的测试产物 | 正式重映射 `airsectionbegone-0.1.0.jar`，SHA-256 为 `742766bd14c1a6dbc91df805d61c71f331bf65ffa8abd4fba5190ecd06e5e68e` |
| 优化模组 | Lithium 0.21.4、C2ME 0.3.6.0.0、FerriteCore 8.2.0、ServerCore 1.5.15。C2ME 的 `gcFreeChunkSerializer` 开启 |
| Polymer | 本地 `lithium-compat` 的 Polymer Bundle 0.15.2+1.21.11，SHA-256 为 `3273e216870a9e83762a4be81bb12b104825e8b539d08186d1d04942314331b1` |
| 抓堆 | spark 1.10.170，通过服务器控制台执行 `spark heapdump`，使用 JProfiler 16.3 离线读取 |
| 世界与运行 | 种子 `20261115`，20 TPS，视距和模拟距离均为 2，主测区固定强加载 128 个区块。实际抓堆时每组均有 240 个 `LevelChunk`、240 个 `ImposterProtoChunk` 和 68 个真正的 `ProtoChunk` |

**OFF** 与 **ON** 都加载同一个 AirBeGone JAR、测试驱动和兼容性模组。每组从同一母档复制到独立目录，分别设定 `-Dairsectionbegone.enabled=false` 或 `true`，并在自己的目录完成写入和重启读取。OFF 测量保留 AirBeGone Mixin 和实例字段时的关闭状态。九场景一共取得并校验 20 份 HPROF，其中 18 份进入下表的 9 对有效对照。

| 高度标识 | 可放置方块 Y | 总高度 | 每套 section 数组长度 |
| --- | --- | ---: | ---: |
| `320` | −64 到 319 | 384 | 24 |
| `1024` | −512 到 511 | 1024 | 64 |
| `4064` | −2032 到 2031 | 4064 | 254 |

`void` 使用空层和 `the_void` 群系，`flat` 从世界底部生成一层基岩、两层泥土和一层草方块，`normal` 使用原版主世界生成器。两档高维度的普通地形沿用原版噪声设置，扩展出来的可建造高度包含大量空段。完整流程见 [九场景测试手册](ASBG_COMPAT_BENCH_RUNBOOK.md)。

### 全堆与各对象族

箭头表示 OFF → ON，括号为相对 OFF 的节省比例。全堆取分析器里的可达对象总占用。sections、高度图和 `PoiManager` 分别对完整对象集合求联合 retained size，即整组对象一起移除时随之失去可达性的内存。各集合可能重叠，共享对象也可能有来自池的其他保留路径，因此按列独立解读。单位均为 MiB，`1 MiB = 1,048,576 B`，百分比按原始字节计算。

| 场景 | 全堆 | 全堆减少量 | 全部 sections | FULL 高度图 | 全部 `PoiManager` |
| --- | ---: | ---: | ---: | ---: | ---: |
| `void-320` | 174.980 → 171.919（1.75%） | 3.061 | 3.820 → 1.243（67.45%） | 0.359 → 0.052（85.62%） | 0.269 → 0.084（68.91%） |
| `normal-320` | 191.474 → 189.053（1.26%） | 2.421 | 11.876 → 9.721（18.14%） | 0.359 → 0.225（37.21%） | 0.271 → 0.086（68.32%） |
| `flat-320` | 176.875 → 173.859（1.71%） | 3.016 | 4.462 → 1.920（56.98%） | 0.359 → 0.052（85.62%） | 0.269 → 0.084（68.91%） |
| `void-1024` | 182.382 → 174.786（4.16%） | 7.596 | 10.008 → 3.183（68.20%） | 0.469 → 0.059（87.40%） | 0.456 → 0.084（81.69%） |
| `normal-1024` | 199.818 → 193.032（3.40%） | 6.786 | 18.207 → 11.890（34.70%） | 0.469 → 0.288（38.55%） | 0.459 → 0.086（81.27%） |
| `flat-1024` | 184.285 → 176.723（4.10%） | 7.562 | 10.650 → 3.859（63.76%） | 0.469 → 0.059（87.40%） | 0.456 → 0.084（81.69%） |
| `void-4064` | 216.766 → 187.859（13.34%） | 28.907 | 39.400 → 12.397（68.54%） | 0.469 → 0.059（87.40%） | 1.588 → 0.084（94.74%） |
| `normal-4064` | 237.917 → 210.270（11.62%） | 27.647 | 48.300 → 22.210（54.02%） | 0.469 → 0.289（38.26%） | 1.591 → 0.086（94.59%） |
| `flat-4064` | 218.671 → 189.811（13.20%） | 28.860 | 40.041 → 13.073（67.35%） | 0.469 → 0.059（87.40%） | 1.588 → 0.084（94.74%） |

**Section 容器解释了 4064 高度下的大部分差值。** 该档全堆减少 **27.647–28.907 MiB**，全部 sections 联合 retained size 减少 **26.089–27.003 MiB**。每组的 139,192 个 section 中，60,960 个属于 `LevelChunk`，60,960 个属于包装器自己的 backing 数组，17,272 个属于真正的 `ProtoChunk`。以虚空场景为例，后者对应的 68 个 `ProtoChunk` 在 ON 和 OFF 中均独占保留 **5.262 MiB**。仅在两侧分母中扣掉这组对象的独占部分，比例由 13.34% 变为 **13.67%**，这是一项离线归一化计算。

**POI 收益随高度增加。** 抓堆时主测维度一共有 309 个已登记 POI 列，Lithium 位图列数同为 309。OFF 的 POI 表从标准高度的 **7,416** 条升到 4064 高的 **78,486** 条，分别对应 `309 × 24` 和 `309 × 254`。ON 在各高度只保留实际存在的 `PoiSection` 条目，虚空和超平坦各 **2** 条，普通地形 **5** 条。以上条目数来自主测维度，表中的 retained size 取快照中所有 `PoiManager` 的联合值。

**高度图收益取决于内容重复度。** 每份堆有 960 张 FULL 高度图。测试中的虚空、超平坦各只保留 1 份共享存储，三个普通地形场景分别保留 496、493、496 份。共享存储和弱池也占空间，下面给出实际对象集合的 retained 值，便于核对全堆变化。各列独立取数。

| 场景 | ON 共享高度存储数 | ON 共享高度存储（B） | 高度图弱池 OFF → ON（B） | ON section 注册表共享池（B） |
| --- | ---: | ---: | ---: | ---: |
| `void-320` | 1 | 368 | 872 → 792 | 736 |
| `normal-320` | 496 | 182,528 | 872 → 23,200 | 1,056 |
| `flat-320` | 1 | 368 | 872 → 776 | 704 |
| `void-1024` | 1 | 488 | 872 → 712 | 736 |
| `normal-1024` | 493 | 240,584 | 872 → 23,064 | 1,056 |
| `flat-1024` | 1 | 488 | 872 → 760 | 704 |
| `void-4064` | 1 | 488 | 872 → 1,072 | 736 |
| `normal-4064` | 496 | 242,048 | 872 → 23,248 | 1,056 |
| `flat-4064` | 1 | 488 | 872 → 1,128 | 704 |

### MSPT、区块加载与 GC

测试驱动读取服务端 tick 耗时，计时包含对应方块编辑。每组采集 **1,000 tick 静置**、**一个集中执行 32 次首次写入的 tick**、**1,000 tick 稳态编辑**。稳态阶段每 tick 32 次成功修改，共 **32,000 次**。本机驱动在无玩家连接的实例中执行编辑和发包编解码检查。抓堆及 ASM 行覆盖诊断均在计时窗口之外。

下表单位为 ms，箭头表示 OFF → ON，括号中的差值为 ON − OFF。静置和稳态每格先列均值，再列 P95。首次写入每组只有一个读数。

| 场景 | 静置（均值 / P95） | 首次批量写入 | 稳态（均值 / P95） |
| --- | ---: | ---: | ---: |
| `void-320` | 0.237 → 0.267（+0.030）<br>0.341 → 0.379 | 1.928 → 1.771（−0.157） | 0.296 → 0.312（+0.016）<br>0.472 → 0.505 |
| `normal-320` | 0.661 → 0.636（−0.025）<br>1.062 → 1.037 | 4.041 → 9.897（+5.856） | 0.593 → 0.555（−0.038）<br>0.850 → 0.841 |
| `flat-320` | 0.332 → 0.338（+0.006）<br>0.420 → 0.457 | 1.902 → 2.681（+0.779） | 0.363 → 0.430（+0.068）<br>0.628 → 0.653 |
| `void-1024` | 0.325 → 0.323（−0.002）<br>0.424 → 0.435 | 1.679 → 3.694（+2.015） | 0.403 → 0.419（+0.016）<br>0.591 → 0.651 |
| `normal-1024` | 0.673 → 0.716（+0.043）<br>1.030 → 1.105 | 3.897 → 7.375（+3.477） | 0.571 → 0.674（+0.103）<br>0.834 → 1.062 |
| `flat-1024` | 0.182 → 0.184（+0.002）<br>0.256 → 0.261 | 2.160 → 1.963（−0.198） | 0.268 → 0.292（+0.025）<br>0.507 → 0.549 |
| `void-4064` | 0.179 → 0.181（+0.002）<br>0.257 → 0.267 | 2.602 → 3.276（+0.674） | 0.310 → 0.284（−0.026）<br>0.501 → 0.589 |
| `normal-4064` | 0.672 → 0.652（−0.020）<br>1.068 → 1.038 | 3.151 → 8.739（+5.587） | 0.667 → 0.704（+0.037）<br>0.874 → 0.997 |
| `flat-4064` | 0.181 → 0.179（−0.002）<br>0.269 → 0.255 | 3.006 → 3.052（+0.047） | 0.307 → 0.265（−0.041）<br>0.540 → 0.546 |

首次写入会触发方块容器、Lithium 计数数组和高度图等数据的按需复制。上表测量的是包含这一批编辑的完整 tick，无法由单次采样分离每项复制耗时。静置和稳态的均值差有升有降，当前配对适合识别大幅变化，细小波动仍受单轮测量和服务器调度影响。

加载耗时从追加 64 张强加载票起算，到目标区块可 tick 为止。GC 暂停统计覆盖重启进程的整个生命周期，包含 spark 抓堆。

| 场景 | 加载耗时 OFF → ON（秒） | GC 暂停次数 OFF → ON | GC 累计暂停 OFF → ON（ms） |
| --- | ---: | ---: | ---: |
| `void-320` | 0.050 → 0.050 | 20 → 20 | 313.0 → 322.2 |
| `normal-320` | 0.105 → 0.104 | 21 → 21 | 366.1 → 354.7 |
| `flat-320` | 0.050 → 0.101 | 20 → 20 | 312.5 → 325.1 |
| `void-1024` | 0.049 → 0.100 | 20 → 20 | 310.8 → 313.4 |
| `normal-1024` | 0.107 → 0.107 | 21 → 21 | 341.9 → 350.1 |
| `flat-1024` | 0.101 → 0.349 | 20 → 20 | 313.0 → 306.7 |
| `void-4064` | 0.150 → 0.100 | 21 → 21 | 352.1 → 344.8 |
| `normal-4064` | 0.154 → 0.203 | 25 → 22 | 437.4 → 402.3 |
| `flat-4064` | 0.151 → 0.150 | 21 → 21 | 359.5 → 342.3 |

### 功能、兼容性与覆盖证据

| 用例族 | 本轮验证 |
| --- | --- |
| 方块与群系 | 验证三种均匀空气、混合与固体方块、非零位但内容均匀的 palette，以及负坐标和世界上下界。沿完整区块、底层 section、群系解析器和真实 `fillbiome` 验证写时复制与保存重载 |
| Section 生命周期 | 验证生成转 FULL、`ImposterProtoChunk` 隐藏数组、独立的 section 和 Lithium 数据身份、无效写入与重新折叠预算 |
| 高度图 | 验证 FULL 四张图、实际方块更新、缺失和异常打包数据，以及网络快照、保存快照、可变数组引用之间的隔离。独立诊断验证弱池回收 |
| POI | 在 200 个空列夹具及实际世界中验证预取、取消、合法高度、真实方块登记、占用与释放、空列与有记录列的 C2ME 卸载、过期数据替换和重启读取 |
| Polymer 与其他优化模组 | 在正式命名空间检查 LithiumHashPalette、服务端区块包的两次状态映射及解码结果，同时检查 Polymer 同步位置、虚拟光照位置的移除和重建。FerriteCore、ServerCore 随每组共同运行 |

构建记录中 12 项单元测试全部通过。18 个正式运行目录的 `write` 和 `reload` 阶段均有 PASS 记录。独立诊断另完成 8 次，ASM 探针命中 **387/435 行（88.97%）**，记录了 **27 个执行过的 Mixin 方法签名**。分母为已加载且能映射到 AirBeGone 源码的字节码行。诊断覆盖了关闭 Lithium `ai.poi`、关闭 C2ME 低分配序列化器，以及禁用必要 Lithium section 注入后触发预期启动保护的情况。

### 配对限制与原始证据

- 正式各对的区块对象计数和主测区工作量一致。`normal-1024` 运行时有 4 个 section 落入不同的非空计数分组。主测区摘要和非空段总数一致，离线逐一比较了 240 个 FULL 区块，保存的方块、群系和高度图均相同。
- 普通地形早期试跑出现随机刻导致的少量地物变化。重新固定游戏规则后完成配对，已匹配的部分早期场景沿用随机刻速率 3。下表列的是各对实际使用的规则。最早一轮 `flat-320/off-1` 的 `fillbiome quart cell` 断言失败，留在试跑记录中。
- 本轮源码快照与更名前的生产源码只有一处差异，`SharedHeightmapStorage#getRaw()` 后续补了 `@NonNull` 类型注解。以上数据对应当时构建的 JAR，文档整理未重新跑服。

本机批次目录为 `build/review/asbg-matrix/2026-11-20/`。每行的运行 ID 可在批次 `manifest.json` 与 `heap-index.csv` 中查到，后者同时记录原始 HPROF 文件名、文件大小和 SHA-256。

| 场景 | OFF 重启 ID | ON 重启 ID | `randomTickSpeed` |
| --- | --- | --- | ---: |
| `void-320` | `void-320-off-3-reload-05323bea` | `void-320-on-3-reload-2b877c6c` | 3 |
| `normal-320` | `normal-320-off-4-reload-f018d980` | `normal-320-on-4-reload-a872b93a` | 0 |
| `flat-320` | `flat-320-off-3-reload-d0eb1d5d` | `flat-320-on-3-reload-9a248580` | 3 |
| `void-1024` | `void-1024-off-3-reload-4b3f3c62` | `void-1024-on-3-reload-f75037d9` | 3 |
| `normal-1024` | `normal-1024-off-3-reload-7d32cd19` | `normal-1024-on-3-reload-c4126ecb` | 0 |
| `flat-1024` | `flat-1024-off-3-reload-14809b62` | `flat-1024-on-3-reload-df262f3e` | 0 |
| `void-4064` | `void-4064-off-3-reload-a5fd89fe` | `void-4064-on-3-reload-166c9efb` | 0 |
| `normal-4064` | `normal-4064-off-3-reload-16d71629` | `normal-4064-on-3-reload-73675555` | 0 |
| `flat-4064` | `flat-4064-off-3-reload-bc92fc6b` | `flat-4064-on-3-reload-bc079d5d` | 0 |

复核时，进入 `runs/<场景>/<组>-<轮次>/`，查看 `analysis.json` 的原始 retained 字节、对象数和 tick 分位数，查看 `heap-queries.jsonl` 的查询范围，以及 `ticks.csv` 的单 tick 数据。根目录的 `checks.json` 和 `actions.jsonl` 记录功能断言，`coverage/source-coverage.tsv` 和 `coverage/executed-handlers.tsv` 记录覆盖。批次产物保存在本机的 Git 忽略目录中。

## 实现与兼容性

### 空气与均匀群系容器

AirBeGone 保留每个 `LevelChunkSection` 的对象身份，只替换其内部 `states` 和 `biomes` 引用。空气与群系分别判定。方块容器可以共享的三种内容为纯 `AIR`、纯 `CAVE_AIR` 和纯 `VOID_AIR`，群系容器可以共享任意已注册的均匀群系。零位 palette 可直接确认，其他候选需要核对最多 4096 个方块或 64 个群系单元。每个群系注册表有三个空气容器和按需创建的群系容器，上界为 `3 + 注册群系数`。 [ASBGSectionManager.java](../src/main/java/com/perry/asbg/section/ASBGSectionManager.java)

折叠入口覆盖 `LevelChunk` 和 `ImposterProtoChunk` 自己的 backing section 数组。包装器的 `getSections()` 返回底下完整区块的数组，AirBeGone 直接访问父类字段来折叠包装器那套独立数组。真正的 `ProtoChunk` 保持原版表示，生成期的并行写入沿用原路径。 [LevelChunkMixin.java](../src/main/java/com/perry/asbg/mixin/LevelChunkMixin.java) · [ImposterProtoChunkMixin.java](../src/main/java/com/perry/asbg/mixin/ImposterProtoChunkMixin.java)

真正会改动共享内容的方块写入会先复制容器，同时复制 Lithium 的 `countsByFlag` 和 `randomTickableBlocksByY` 数组。每段的 `SectionData` 和监听器始终保持自己的身份。整个 section 写回单一空气后，候选至少等 200 tick 再检查，每个世界每 tick 最多检查 64 个有效候选、最多弹出 256 个任务。同一位置最多一个任务，取消通过位置索引取出原节点并从有序队列删除。群系写入沿用原版 `recreate()` 取得可变容器，完成后重新检查是否均匀。 [写入保护](../src/main/java/com/perry/asbg/mixin/LevelChunkSectionStorageMixin.java) · [折叠队列](../src/main/java/com/perry/asbg/section/SectionRefoldScheduler.java) · [架构说明](ARCHITECTURE.md)

### 完整区块高度图

每张 `Heightmap` 保留独立对象。读盘复原完成后，或生成区块转换成 FULL 后，AirBeGone 将内容相同的 `SimpleBitStorage` 换成共享不可变存储。弱池先用哈希定位候选，再比较位宽、长度与完整 `long[]`，所以哈希碰撞仍需比较内容。高度图卸载后，池中的代表可以被 GC 回收。 [HeightmapDeduplication.java](../src/main/java/com/perry/asbg/heightmap/HeightmapDeduplication.java) · [SharedHeightmapStorage.java](../src/main/java/com/perry/asbg/heightmap/SharedHeightmapStorage.java)

当写入确实要改变高度时，`Heightmap#setHeight` 处的 `@ModifyReceiver` 将本次写入的接收者换成本图的私有副本，Lithium 合并高度图更新也经过该入口。整表写入和公开的可变数组访问同样会先取得私有存储。保存和区块发包各取独立快照，允许 C2ME 在活动区块后续修改时仍使用已取得的保存数据。 [写保护](../src/main/java/com/perry/asbg/mixin/HeightmapMixin.java) · [保存快照](../src/main/java/com/perry/asbg/mixin/SerializableChunkDataMixin.java) · [网络快照](../src/main/java/com/perry/asbg/mixin/ClientboundLevelChunkPacketDataMixin.java)

### POI 稀疏缓存

POI 以区块列 `(x,z)` 为单位读盘，以三维 section 键 `(x,y,z)` 存放记录。每个 `PoiManager` 安装一个 `PoiColumnTracker` 来替换原来的 `loadedChunks`，另以打包后的 XZ 键记录整列解包完成。解包时有 `PoiSection` 的段照常写入，缺少记录的段省掉 `Optional.empty()` 条目。已解包列中合法但缺条目的高度，把查到的 `null` 解释为 `Optional.empty()`。仍在预取或解包的列保留原来的未完成语义。床、工作站等新增记录沿原版 `getOrCreate` 写入，移除最后一个 `PoiRecord` 后现存的 `PoiSection` 仍保留。 [PoiSectionStorageMixin.java](../src/main/java/com/perry/asbg/mixin/PoiSectionStorageMixin.java) · [PoiColumnTracker.java](../src/main/java/com/perry/asbg/poi/PoiColumnTracker.java)

Lithium 继续通过原监听 map 维护高度位图，并用 `nextSetBit` 跳过空段。纯空列在解包完成时由 AirBeGone 补一个空位图。原版 `get` 和 Lithium 合并进 `SectionStorage` 的 `lithium$getElementAt` 都走同一套空值恢复。C2ME 卸载时从 `loadedChunks` 移除列，tracker 随之清掉解包完成标记和 Lithium 位图。 [LithiumPoiColumns.java](../src/main/java/com/perry/asbg/poi/LithiumPoiColumns.java) · [POI 加载与卸载说明](POI_STORAGE.md)

## 构建与复测

项目面向 Minecraft 1.21.11 专用服务端，分发要求 Java 21 或以上、Fabric Loader 0.19.3 或以上、Fabric API 0.141.6+1.21.11 或以上，以及精确版本 Lithium 0.21.4。诊断时用 JVM 参数 `-Dairbegone.enabled=false` 关闭容器共享、高度图去重和 POI 稀疏缓存。 [模组元数据](../src/main/resources/fabric.mod.json)

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat runPoiTestServer -PpoiIntegrationTest -PpoiTestStage=write
.\gradlew.bat runPoiTestServer -PpoiIntegrationTest -PpoiTestStage=reload
```

集成测试的两个阶段使用同一测试目录，默认是 `build/poi-integration/`，启动前需要准备 `eula.txt` 和 `server.properties`。可用 `-PpoiTestRunDir` 指定目录，用 `-PpoiTestOptimize=false` 跑关闭对照，用 `-PpoiTestRequireC2me=true` 检查实际 C2ME 卸载路径。九场景正式复测的母档制作、两阶段驱动、计时窗口和 HPROF 取数规则见 [九场景测试手册](ASBG_COMPAT_BENCH_RUNBOOK.md)。
