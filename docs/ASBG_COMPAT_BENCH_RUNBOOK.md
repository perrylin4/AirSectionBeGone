# AirBeGone 九场景兼容性、内存与 MSPT 测试手册

## 1. 交付目标

执行者负责搭建、运行、排错、抓堆和分析，交付九行对照结果与原始证据。本文件是执行说明。历史报告中的数字不能填入本轮结果。

每个场景用同一份关闭状态的母档制作两个副本。**ON** 和 **OFF** 都安装同一个 AirBeGone JAR 和同一套模组，唯一区别是游戏服务器 JVM 的 `-Dairbegone.enabled=true|false`。开关在类初始化时读取，每次换组启动独立 JVM。这个对照测量“启用 AirBeGone 的收益”。OFF 仍加载 Mixin、类和实例字段，其结果不能标成“卸载 AirBeGone”或“纯原版服务端”。

最低交付量为 **9 份母档、18 个独立运行目录、18 次 write、18 次同组重启 reload、18 个来自 spark 的可分析 HPROF**。每组每次重复都从对应母档重新复制。功能覆盖、性能采样和抓堆可以分阶段；每个场景的两组都要走全套公共工作负载。对照组只切换与内存表示有关的断言。若另外用插桩 JVM 收集覆盖率，运行次数另计。

## 2. 地形与高度矩阵

用户已确认“320 高正常世界”指原版主世界上界 320（不含），从 -64 到 319，共 384 格。另两档的 1024、4064 都指总高度。本手册选用居中的高世界，便于验证负 Y 和最顶层。矩阵中的 normal 指原版主世界地形生成器。

| 场景 ID | 地形 | min_y | height / logical_height | 可放置方块 Y | 每块 sections | 第一轮顺序 |
|---|---|---:|---:|---|---:|---|
| void-320 | 纯虚空 | -64 | 384 / 384 | -64..319 | 24 | OFF → ON |
| normal-320 | 原版主世界 | -64 | 384 / 384 | -64..319 | 24 | ON → OFF |
| flat-320 | 超平坦 | -64 | 384 / 384 | -64..319 | 24 | OFF → ON |
| void-1024 | 纯虚空 | -512 | 1024 / 1024 | -512..511 | 64 | ON → OFF |
| normal-1024 | 原版地形，高维度 | -512 | 1024 / 1024 | -512..511 | 64 | OFF → ON |
| flat-1024 | 超平坦 | -512 | 1024 / 1024 | -512..511 | 64 | ON → OFF |
| void-4064 | 纯虚空 | -2032 | 4064 / 4064 | -2032..2031 | 254 | OFF → ON |
| normal-4064 | 原版地形，高维度 | -2032 | 4064 / 4064 | -2032..2031 | 254 | ON → OFF |
| flat-4064 | 超平坦 | -2032 | 4064 / 4064 | -2032..2031 | 254 | OFF → ON |

normal-1024/4064 只扩展维度的可建造范围，继续使用原版 overworld 噪声设置、群系源和结构。这会保留原版地形带，并在扩高范围内留下大量可用空间。报告记录生成器和噪声范围，以便区分“高维度”和“整段高度都填满噪声地形”。

## 3. 固定软件环境

1. 从 AirBeGone 仓库根目录开始。先记录 Git HEAD、分支、工作树差异及参与构建的未跟踪源文件哈希。当前仓库有未提交修改，测试应使用实际工作树。新建独立批次目录，例如 `build/review/asbg-matrix/<批次>/`，分设 `templates/`、`seeds/`、`runs/`、`analysis/`、`coverage/`。旧测试、历史基准和生产存档不能充当可写运行目录。不要对包含历史 HPROF 的工作区执行 Gradle clean。
2. 使用 Gradle Wrapper。在仓库根执行 `.\gradlew.bat --offline --console=plain build`，保存日志及 `build/test-results/test`。记录测试用例名称和通过数。当前基线为 12 项。保存本轮正式 `build/libs/airbegone-0.1.0.jar` 的 SHA-256。
3. 可复用的已知组合为 Minecraft 1.21.11、Fabric Loader 0.19.3、Fabric API 0.141.6+1.21.11、Lithium 0.21.4、C2ME 0.3.6.0.0、FerriteCore 8.2.0、ServerCore 1.5.15、spark 1.10.170。沿用本机可用的 Java 25，记录 vendor、完整 build、GC、JVM 参数。目标分发字节码为 Java 21。每个场景同时装齐这些模组和下一项 Polymer。
4. **Polymer 必须取本地 lithium-compat 项目**。当前产物为 `D:\minecraft\mods-develop\polymer-lithium-0.21.4-compat\build\libs\polymer-bundled-0.15.2+1.21.11.jar`。编写手册时该文件 SHA-256 为 `3273E216870A9E83762A4BE81BB12B104825E8B539D08186D1D04942314331B1`，仓库 HEAD 短号为 `74f9bed5`。执行时再次核对本地产物、复制件和实际启动目录。若源码后来更新，记录新提交、工作树状态和哈希，整个批次用同一个新文件。同名的公开发行版、sources JAR、dev JAR 都不能替代。
5. 当前本机其他模组的候选位置是 `build/review/heightmap-bench/normal-shared/mods/`。Loom 可能从 Gradle classpath 加载 Lithium 和 Fabric API，独立 Fabric 服务端需要把相应正式 JAR 纳入实例。保留 Loader 展开的模组清单，排除同一模组重复加载。所有 ON/OFF 配对比较模组哈希、测试模组哈希、datapack 哈希和配置文件。
6. 主矩阵优先使用**正式 Fabric dedicated server + remap 后的 AirBeGone、Polymer、测试驱动 JAR**。IDE/Loom 的 named 环境适合开发夹具。正式环境特别要验证 Polymer 的 Lithium palette 包装实际命中，其本地 Mixin 使用 `remap=false, require=0`，单凭“Done”日志无法证明发包兼容。测试驱动若通过反射访问原版字段，先用针对本版的 accessor/invoker 或 Fabric 映射解析实际名称；将 named 环境中的 `getDeclaredField("data")` 等字符串直接搬入生产命名空间会误报。
7. 固定 C2ME 异步配置，建议沿用之前验证过的 `gcFreeChunkSerializer=true`；记录配置和启动时实际启用的模块。Lithium 的 section data、POI、palette 和高度图相关优化按本地兼容组合开启。保留 Polymer 延迟分配、ServerCore 配置和 spark 命令，两组一致。扩展诊断若单独关闭某一模块，应另开目录，不能并入主矩阵。
8. 本机回环监听、唯一 runDir、逐个启动，锁定玩家数、视距和模拟距离（建议均为 2，主要加载量用固定票控制）、种子 `20261115`、难度和游戏规则。固定 20 TPS、相同堆大小与 GC；可先用 4064 OFF 校准内存预算，再固定整个批次。统一时段、天气、随机刻和生物生成策略，记录所有显式调整。采样时关闭 spark 后台采样器、覆盖率探针和调试导出。提前按试跑时最大的 dump × 18 加分析器索引、存档副本和重试余量检查空间。每个 JVM 用启动命令、系统属性和 AirBeGone 启动日志三方核对开关。

## 4. 制作与验收九份母档

1. 在空目录中先写 `server.properties` 和数据包，再首次创建世界。使用 `level-name=map`。正常高度可用原版维度类型。高世界在 `map/datapacks/<包>/data/minecraft/dimension_type/overworld.json` 放入本版完整 overworld 类型，只调整 `min_y`、`height`、`logical_height`。本地 1.21.11 样例在 `build/review/spark-holder-maps/tall-void/map/datapacks/asbg_benchmark/`，含适用的 `pack.mcmeta`。在首次生成前确认包已启用。不要通过修改旧 `level.dat` 给既有区块换高度。
2. **void** 使用 flat 生成器。生成设置的 JSON 值为 `{"layers":[],"biome":"minecraft:the_void","features":false,"lakes":false,"structure_overrides":[]}`。关闭结构，测区不得有出生平台、奖励箱或其他地物。**flat** 同样禁用结构、地物和湖泊；群系 `minecraft:plains`，layers 从下到上为 1 层 bedrock、2 层 dirt、1 层 grass_block。**normal** 使用 `minecraft:normal`，原版结构开启。将设置序列化为 Java properties 时核对读回的 JSON；已有虚空与平坦模板的 properties 可供比照。
3. 统一主测区为 chunk x=200..215、z=200..207，共 128 个。控制台分两次执行 `forceload add 3200 3200 3327 3327`、`forceload add 3328 3200 3455 3327`。命令坐标单位是方块。等待 128 个坐标均到 FULL 且达到预期 ticking 状态，保存并预生成与之关联的外围区块。种子准备阶段使用 OFF。先移除这两组临时票，执行 `save-all flush`、正常停服，待进程退出和存档写入完毕后制作母档。记录票、地图文件清单与相对路径哈希。
4. 验收每个母档的运行时 `minY`、上界、dimension key、generator 类型、section 总数。上界统一以 `minY + height` 计算。检查多个**已加载完整区块**的 `getSections().length`，同时抽查磁盘和实际地形。虚空主测区在固定操作前应完全空、群系为 the_void；平坦在 `minY..minY+3` 实测四层材料；normal 留存至少数个地形列的高度、状态和群系，确认自然地形带存在。高虚空中抽查 `minY` 与 `maxExclusive-1` 可访问。记录证据后才开始两组实验。
5. 每个场景把同一份已关闭母档分别复制到 ON/OFF 的 `map/`，包括 datapacks、level.dat、region、POI、entities 等。复制后按相对路径核对内容哈希，额外测试 POI 目录从相同空状态开始。不要让两组写同一个目录或共享可写硬链接。

## 5. 测试驱动与可重复运行

现有 `src/test/java/com/perry/asbg/test/` 可复用断言。另建测试专用的 matrix 驱动和 Fabric 入口，两组用同一二进制；不把诊断 Mixins 打进正式 AirBeGone。下列限制必须在开始批跑前处理。

- `PoiIntegrationTest` 在 `SERVER_STARTED` 同步运行并在 finally 中立即停服，`probe-poi` 固定采用 `LevelHeightAccessor.create(-352,1728)`，断言写死 108 段和边界。改为注入当前世界的实际高度，按 `height/16` 计算，所有越界夹具改用动态边界。同时测实际 `level.getPoiManager()`。独立 PoiManager 便于精确计数；只跑它无法证明真实世界联动。
- `SectionIntegrationChecks` 在 OFF 时会立即返回，而且原来的固定 sections 在 normal 世界可能已有非空气地形。按矩阵共用脚本执行同样的写入、查询、保存与发包，先创建并核对已知初态的夹具。只有共享对象身份、内部计数器和预期异常等实现断言按 ON/OFF 分支。旧测试直接推进游戏时间、清空调度器的操作限于隔离功能夹具；采样世界靠真实 tick 推进。
- `HeightmapIntegrationChecks` 已有四种高度图、包与存档快照、错误长度、缺项、公开数组别名、写入与重启测试。把坐标映射到明确的夹具区，替换读取命名空间相关私有字段的方法。保留一个**未被读取原始数组**的区块作共享身份观察；读取身份用只读 accessor，公开 `getRawData()` 会主动解除共享。
- `build/review/heightmap-bench/java/probe/` 有逐 tick 采样与 spark 命令示例。旧 `bench.gradle` 引用已经迁走的 `sourceSets.poiTest`，其 `DisableSharingMixin` 和 `-Dprobe.share` 只控制高度图。复制可用逻辑，重新接入 matrix 入口，排除旧禁用 Mixin。生产实例只装一个测试入口，避免原 `PoiIntegrationTest` 抢先停服。
- 驱动采用按 tick 推进的状态机。记录 runId、阶段、动作编号、坐标、期望与实际、耗时、异常栈。等待 chunk 状态、IO、命令回执时让出服务器线程。超时记录待处理工作、加载状态、线程或日志证据，再结束该次运行。完成标记含本次唯一 runId 和阶段，父进程检查退出码、新鲜标记及完整日志。旧 `PASS` 文件不能通过验收。

推荐为驱动定义 `scenario`、`stage=write|reload`、`runId` 和清单路径，实际参数以执行者实现为准。先在 flat-320 完成一对全流程，再跑其他场景；驱动若因此修改，用最终版本重新跑先前数据。启动示意如下，JVM 参数在 `-jar` 之前，工作目录是该组实例目录。

```powershell
# 已在该组独立 runDir 中，且已准备两组相同的 mods、配置、世界副本
# $java、$launcher、$heap、$enabled、$scenario、$stage、$runId 由本批次清单提供
& $java "-Xms$heap" "-Xmx$heap" "-Dairbegone.enabled=$enabled" `
    "-Dasbg.matrix.scenario=$scenario" "-Dasbg.matrix.stage=$stage" `
    "-Dasbg.matrix.runId=$runId" -jar $launcher nogui
```

## 6. 每个场景、每组的执行顺序

1. **write**。加载主测区，在单独划定的夹具区执行第 7 节操作。明确分配不相交坐标给 section、heightmap、POI、Polymer；用一个母档未生成的远端区块走一次实际新生成路径，另取已有区块走读盘路径。两组使用相同坐标、票及操作顺序。构造临时对象的专项断言不能替代真实区块写入。固定高低边界夹具；自然地形留出未经编辑的参照区。
2. write 结束前写入持久化哨兵、固定 POI 占用状态、方块和群系标记。记录各自精确坐标、状态及四张高度图读数，执行 `save-all flush`。等待保存完成，移除功能夹具的临时票，关闭独立 PoiManager，正常停服。把检查结果落盘，释放已加载区块、快照、玩家和监听器的探针引用。
3. **reload**。同组同一 runDir、相同开关重启，先**只读**核对上一步的哨兵、POI 占用和高度。验证后才做释放 POI 或新的写入。已有 reload 用例会释放 JOB 的票；重试一组时重新从母档执行 write → reload，避免二次 reload 把上次已释放的票当作丢档。
4. 根据固定坐标重建 128 个主测区票。计时和抓堆只在约定区域、票和玩家数稳定时进行。连接验证如需测试玩家，在功能阶段完成，然后按两组一致的方式断开。测区以外的功能夹具卸载完毕；主测区保留等量、非零的高度图解共享、非空 POI 和 Polymer 痕迹以便抓堆，事先在工作负载清单列明。内存快照点统一在 reload 的同一阶段。
5. 同组按第 8 节采样、抓堆、停服。留存日志、状态摘要、tick CSV、命令结果、HPROF、哈希和分析表。若功能断言失败，在报告中保留失败记录和最小复现；按阶段隔离失败并完成其余可以独立验证的场景。失败或加载量不匹配的组不得作为通过的内存收益数据。

## 7. 每个场景的动作与断言

对每个用例记录输入与断言次数。公共行为的验收比较方块状态 ID、群系 key、高度、POI 类型及占用、包解码内容、磁盘读回结果。跨 JVM 比较用稳定值和排序后的坐标清单；对象引用身份只在单次 JVM 内比较。世界生成和光照的后台任务达到明确状态后再断言。

| 编号 | 在测试驱动中实际执行的动作 | 必须留存的证据 |
|---|---|---|
| S1 折叠入口 | 分别从新 ProtoChunk 转 FULL、已有 FULL 读盘、ImposterProtoChunk 包装读写路径取得 section。检查 wrapped chunk 和 wrapper 自身的 backing 数组；真实 ProtoChunk 另算。 | FULL 和 wrapper 的对应可折叠容器 ON 时共享；section 自身及其 Lithium SectionData 身份各自独立。对真实 ProtoChunk 观察折叠前状态。OFF 使用正常容器。 |
| S2 内容和边界 | 建至少两份纯 AIR、纯 CAVE_AIR、纯 VOID_AIR、混合两种空气、单个非空气、扩大 palette 后恢复同值、扩大后只在最后一格不同的夹具。比较负坐标、区块边缘、最底和最顶合法 section。需要时先在独立可变 section 填充，再走实际构造或延迟折叠入口。 | 同类型的纯空气共享，三类代表彼此独立；混合空气和含实心块的 states 保留独立。单值和非零位扫描都被触发。群系独立判定。对 section 或 container 的自定义子类做隔离夹具，验证保留其对象与读写行为。 |
| S3 写时复制 | 对 A、B 的同种共享 states，分别经 `ServerLevel.setBlock`、`LevelChunk.setBlockState`、section 的有锁/无锁入口执行同值、空气种类切换、石头→空气。每次先重置到约定初态。另选原版随机刻方块触发 Lithium 计数更新。 | A 写入可读回，B 的值、计数和身份不受影响。对实际变化观察 Lithium 的 countsByFlag 和 randomTickCounts，共享与解共享后均对照逐块重算或 OFF 参考值。对同值写核对原返回语义及是否入队。 |
| S4 容器 API | 在隔离夹具分别测试 blocks/biomes 的读、网络写出、pack、copy、recreate、对可变副本 read，调用 set/getAndSet/getAndSetUnchecked 的同值及异值写、不可变实例 read/onResize。 | 返回旧值或抛出预期异常，失败前后原内容一致；副本可修改、原件和邻段独立。非法写仅在临时对象上做，OFF 按普通可变容器语义断言。原版对某些纯空段的落盘省略以 OFF 实测为基准。 |
| S5 群系 | 使用合法 Holder 构造两份 plains、一份 desert、混合群系。组合“非空气方块＋均匀群系”和“纯空气＋混合群系”。执行真实 `fillbiome`，跨 section/区块边界局部写，再整段恢复。单独触发 `fillBiomesFromNoise` 并检查完整区块与 ProtoChunk 分支。 | 用 4×4×4 个 quart 格的 key 检查命中范围。相同群系共享、不同群系隔离；B 不受 A 的修改影响。保存、重启、重新发包后再查。 |
| S6 重折叠调度 | 在可控夹具创建同 tick 的 64 个扫描后失败候选和 16 个成功候选，记录 section 排序。另测重复 schedule、到期前写回非空气取消、重复取消、卸载候选、同坐标不同世界及停服清理。用过期且未加载的候选覆盖 poll 上限。 | 按当前源码核对延迟 200 tick、每世界每 tick 最多 64 次有效检查和 256 次到期出队；失败扫描消耗检查预算。读取队列计数不能持有世界强引用到抓堆。OFF 执行相同世界编辑，内部预算断言归入 ON。 |
| H1 生成与读盘 | 构造并实际加载等内容的完整区块，逐个检查 WORLD_SURFACE、OCEAN_FLOOR、MOTION_BLOCKING、MOTION_BLOCKING_NO_LEAVES。包含平顶、不同地表、空列及最顶可建造格。 | 比较的是内部 BitStorage 身份；各 Heightmap 对象独立。区分原始高度偏移和 API 的 firstAvailable（实心顶块 Y+1）。记录 minY 和存储位宽，跨层高分别验收。 |
| H2 修改和别名 | 同地下写、地表上方放水、换树叶、换石头、拆顶块。取两份包数据及一份 `SerializableChunkData.copyOf`，篡改其中一份。另测入池前后 `getRawData` 的活数组、合法 setRawData、错误长度、存档缺一类高度图。 | 四类图用各自原版谓词或逐列扫描作独立参考。只有值变化的共享图解共享，其他区块未变；正常发包/保存后仍可共享。三份快照互不污染；调用者的输入数组后改不影响世界；公开 getter 的既有活引用仍有效。错误长度的预期告警限定到该用例，随后验证重算。 |
| H3 生命周期 | 改后恢复原内容并再次尝试入池，保存并真正卸载、读回。隔离测试同哈希异内容、不同布局、可变副本，跨临时区块持有/释放弱池代表。 | 当前实例已关闭共享机会时不重新入池；新加载实例按当前内容判断。弱引用专项单独记录 GC 条件与观测，不能从一次 GC 未回收断言泄漏。 |
| P1 空列与预取 | 对无既有 POI 的隔离区域读 200 个空列，两次查询同 section；检查合法高度、越界、未加载、已标记但未完成。第 201 列 prefetch/join，先查、调用 tick 解包再查；另一列在 pending 时走 C2ME 的实际卸载入口。 | 区分底层 null、已知空 Optional、实际 PoiSection。ON 无空 section 表项；OFF 在精确限定的 200 列夹具内为 200×N，N=24/64/254。预取完成和发布之间的状态正确，已卸载 future 不得复活列。确认 Lithium 直接查询和列位图结果。 |
| P2 实际 POI | 在实际世界放置职业站点、下界传送门 POI 的可控夹具，按实际注册与更新时机等待；执行查找、最近、占用、释放、重新占用、移除。对独立管理器再做旧 section 被空列覆盖、迭代移除、批量移除和清空。 | 核对 POI 类型、半径查询结果、可用票、已移除点、加载列计数和 Lithium 位图；保存后重启先读票再释放。触发真实 C2ME unload，检查 loaded、pending、ready 和 Lithium 索引的清理。保留一批主测区 POI 已知空列和至少一个活跃 PoiSection 到抓堆。 |
| M1 Polymer 索引 | 在主测区指定 section 放置一个测试专用 Polymer 方块，再放一个有同步光照行为的方块，触发登记、区块发送、移除、重放。用观察接口检查位置集合、迭代和 lights 标记；在同内容邻段检查隔离。 | 从未登记的 section 保持延迟分配，登记后索引只包含正确局部坐标。移除后内容和需要刷新光照标记正确，已分配过的空集合允许保留。不能只用普通石头推断 Polymer 接口已工作。 |
| M2 Polymer × Lithium 发包 | 在真实 section 内放入足够多种状态，沿 Lithium palette 的实际选择路径进入 `LithiumHashPalette`，保留一个应映射到不同客户端方块状态的 Polymer 方块。构造真实接收者的 PacketContext 并走区块发包与编码；优先让一个测试客户端实际接收、解码并记录。另测折叠后首次写和再次发包。 | 用运行时 palette 类和命中证据确认本地 `lithium_BlockPaletteMixin` 的服务端 `getSerializedSize`、`write` 都执行，实写长度与预测一致，解码后的展示状态正确，服务端原态未变。只做了服务端编码/解码时，在报告中明确实际覆盖边界。 |
| M3 跨模组运行路径 | 保持 C2ME、Lithium、FerriteCore、ServerCore 和本地 Polymer 同时在场。已生成块读盘、新区域生成、释放票、卸载、存盘、重启、再次加载；同时保留方块、群系、四类高度图、POI 和同步方块断言。 | 核对异步任务完成、保存后稳定摘要一致。对 `require=0` 的 AirBeGone 快照及 Lithium POI 注入给出行为或注入指令证据。导出过 Mixin 字节码只能证明注入，仍需行为断言。 |

单元测试补充哈希碰撞、集合迭代和异常等边界。对无法在主配置出现的条件分支（如关闭 Lithium ai.poi、自定义已加载列集合、无注册表 key 的 Holder、启动时缺少必要 Lithium 注入），使用另外的隔离单测或负向夹具。记录预期失败、跳过原因和所在源码行，不能让负向启动配置混入 18 份正式快照。

### 可直接采用的坐标与判定示例

- 用 chunk (200,200)、(201,200) 作 A/B 参照，局部 (8,8) 对应方块 X/Z 分别为 (3208,3208)、(3224,3208)。在 `maxExclusive-32` 附近专设一段测纯空气；先检查该段全部 4096 格，再记录 A/B 的 states、biomes 引用。设定普通 AIR/CAVE_AIR/VOID_AIR 身份时以具体 BlockState 为准，三者的 `isAir()` 都可为真。若公共放置 API 因原段为空而将“空气换空气”短路，在隔离夹具通过独立的可变 section 构造三种输入，再通过真正的 FULL 转换或加载入口观察折叠。把公共 API 的短路行为另与 OFF 比对。
- 另选 chunk (202,200) 的局部 (8,8) 做高度图。准备无悬空地物的列，在 Y=h 放石头，取 h 为 `maxExclusive-48`。四类图的 firstAvailable 初值均为 h+1。在 h+1 放水，WORLD_SURFACE 和 MOTION_BLOCKING 为 h+2，OCEAN_FLOOR 为 h+1；换树叶时 MOTION_BLOCKING_NO_LEAVES 为 h+1；拆掉顶层后回到 h+1。先在地下做一次不会抬高该列的写入以覆盖“不解共享”分支。流体/树叶的延迟更新用相同的 tick 步调，避免读到邻居更新后的另一个状态。指定有顶块的持久化哨兵另选一列。
- S5 以 `SectionPos` 求完整 16 格高的目标 section，用闭区间方块边界调用 `fillbiome`；再选择横跨 x=3215/3216 的小区域，按原版 quart 对齐规则计算应改变的格数。比较两侧边界内外和完全未动的第三个区块。实际留存整段 64 个格子的修改前、后及重启后 key。
- 稳态编辑的 16 个区块可用 x=204..207、z=202..205。每块两列：一列在事先建好的实心顶下切换 STONE/AIR，一列在固定石锚点上切换 GLASS/AIR。地表目标初始为 AIR，地下目标初态也显式定为 AIR。偶数 tick 放入、奇数 tick 移走，按相同索引顺序执行，1000 tick 后回到初态。每列都只在自己的区块内部，功能测试的 A/B、POI、Polymer 位置另行保留。
- S6 记录候选的实际 `dueTime` 和 `sectionPos` 排序；每次删最后一个非空气块后才检查 refold。直接调 section setter 与经 LevelChunk 写回的入队路径分别取证。需要人为注入失效队列项的预算试验放在诊断夹具中，避免在 OFF 性能组主动调用 AirBeGone 内部 `schedule` 而制造原本不会有的队列。
- 对每个用例先发出 `case-start`，结束时发出 `case-end`，附 `scenario,group,stage,attempt,caseId,logicalDigest,assertions,status`。公共摘要按 section 坐标、局部坐标和资源 key 排序，排除对象地址、票 ID、时间戳和迭代顺序。在收集矩阵时检查每个场景 S1–S6、H1–H3、P1–P2、M1–M3 共 14 个用例族都有结果；同组某项仅在单元测试有证据，应标出缺少的实服路径。

## 8. 性能与 spark 抓堆

每组使用同一负载清单和游戏 tick 时间轴。建议主测区域选 16 个编辑区块，其余作为未改参照。测试驱动使用确定性坐标序列，事件计数以真实修改成功数为准。

1. 核对 128 个目标坐标已 FULL 且可 tick；等待 C2ME 存取任务与强加载造成的外围状态稳定。每 20 tick 记录一次全维度与目标维度摘要，连续 5 次稳定后再预热 400 tick。报告目标区、外围 FULL 和真实 ProtoChunk 的实际数；128 张票不等于 JVM 恰有 128 个完整区块。
2. 在 20 TPS 下采 **1000 tick 静置**。随后单独测**第一次写共享 section/高度图**。事前准备固定锚点和可共享的平顶夹具，经实际重载使其具备可共享条件；用只读内部观察确认 ON 确实共享。记录触及多少新 section、新高度图以及这一批 tick 的耗时。不要把提前取原始数组或预编辑造成的解共享误当首次写。
3. 采 **1000 tick 稳态编辑**，每 tick 32 次预计改变状态的写入，分布在 16 个区块。地下与地表各占一部分；在原空位和约定方块之间轮换，下一轮恢复。固定无流体锚点限制拆顶后向下扫描深度；流体与树叶语义另在功能阶段测。保存逐 tick 的阶段、耗时 ns、尝试次数、成功次数和最终内容摘要。期望 32,000 次成功写入。若任一组出现 no-op，检查夹具并成对重跑。
4. 从服务端真实 tick 耗时取样，测量包含本 tick 的指定编辑。沿用旧探针时核对本次版本写入 tick 时间环形缓冲区的顺序、下标和读取时点。MSPT 排除 20 TPS 等待睡眠。按阶段报告样本数、均值、P50/P95/P99、最大值，以及加载时间和 GC 暂停。抓堆、覆盖率、详细断言、日志写盘和区块预生成应处于计时窗口之外。接近噪声或正负反转时可交换顺序从母档复跑，报告单轮与复跑结果。
5. 完成编辑，恢复约定终态，等待折叠期限和队列处理完毕，执行 `save-all flush`，再等待异步保存稳定。两组保持同样票及测试数据。丢弃驱动持有的世界、chunk 列表、包、序列化快照和临时大数组，仅留下必要状态或标量摘要。记录地图逻辑摘要、实际加载集、POI/Polymer 工作量、在场玩家数、JVM uptime。
6. 从**当前游戏服务器的控制台**执行 `spark heapdump`。记录命令发送时间，等待该次命令后的完成消息与实际路径。默认在本实例 `config/spark/`。核对新增非空文件、文件头、最终大小稳定、能被分析器完整读取，再正常停服。两组采用相同 spark 参数和 GC 策略；如选择显式 GC，写明次数、等待条件及弱池影响并在每组一致执行。不要用 HPROF 文件长度或系统 RSS 替代堆分析。
7. 保留原始 HPROF，登记绝对路径、字节数、SHA-256、runId 和场景。磁盘不足时先完成当前配对的归档和校验，再继续；重复抓堆必须写新记录，保留与阶段对应的时间戳。只有一侧成功的场景标成未完成。

## 9. 内存分析与配对验收

使用同一版本、同一对象尺寸模型、同一引用处理策略的分析器。九场景使用的 JProfiler 16.3 工作流在本机 `build/review/asbg-matrix/analyze_matrix.py`。改用新批次时核对路径和 Fabric 命名空间，并先用已知 FULL chunk 的引用路径验证筛选器。正式 retained 口径参见 [九场景报告的内存分析](BENCHMARK.md#全堆与各对象族)。

| 每份都要导出的指标 | 取数和防错条件 |
|---|---|
| 加载量和工作量 | 按维度给出 exact LevelChunk、exact ImposterProtoChunk、exact ProtoChunk、section 数组长度分布、非空气 section 与方块计数、四种高度图数、已加载与非空 POI 列/section、Polymer 同步位置数。wrapper 和其 wrapped LevelChunk 分列；对 backing 数组和 section 对象按 identity 去重。保留主测区已加载坐标排序摘要。 |
| 全堆 | 全部 GC 可达对象的去重字节数、对象数、尺寸模型。单列 JVM heap used、RSS 或 dump 文件大小如有，仅供诊断。 |
| section 和容器 | 所有目标 section 的联合 retained size；分列完整区块、wrapper 私有 backing、真实 ProtoChunk 的范围。追踪 states/biomes、可变与共享容器、内部 palette/storage/锁、Lithium SectionData/两类计数数组。用对象集去重；包含静态共享缓存的归因需把对应池和代表加入联合集合。 |
| 高度图 | FULL 的 Heightmap、私有 BitStorage、SharedHeightmapStorage、各自 long[]、弱共享池/条目。通过拥有者引用限定类型。记录唯一内容数、共享代表数及已解共享数；弱池的开销计入 ON。 |
| POI、Polymer、调度 | 按真实 PoiManager 和其 map/ready/loaded/Lithium columns/BitSet 路径筛选，另列隔离测试 manager 是否已释放。Polymer 的 ShortOpenHashSet 与 short[] 按 section 字段来路统计；全 JVM 同名类还用于其他业务。计入 AirBeGone 注册表共享数据、队列、候选等额外保留对象。 |

各对象族均对**完整对象集合**求联合 retained size，保存查询、对象数、结果原始字节和一条代表性引用链。不要把各实例 retainedBytes 相加，不要把父集合和子集合相加，也不要按直方图 shallow size × 数量估算。若要给出 AirBeGone 相关总量，在分析器中对选定对象 ID 的并集重新计算 retained；分析器无法计算时只给独立分项和全堆。ON 的静态池可能从另一条根路径保留共享对象，单看 chunk 自身的 retained 会夸大节省。逻辑内容比较避免调用会改变运行时状态的 getter，可在离线 dump 中读取数组内容。

**合格配对**要求同一母档、同模组与配置、同测区坐标、相同成功编辑数和逻辑内容，运行时高度、FULL 数量及持久化哨兵一致。对 wrapper、真实 ProtoChunk、POI 已载列和非空气统计逐项比对。异步遗留对象或外围加载量不同，先找根路径、统一等待/票和工作量，再从母档重试。仍有差异时单列“受加载量影响”，可提供匹配坐标的分项，不能用粗略每区块平均冒充等负载整堆结果。

以未舍入字节计算 `节省 MiB = (OFF − ON) / 1048576`、`节省比例 = (OFF − ON) / OFF × 100%`，分母为零记 N/A。每个百分比注明分母为全堆、section 联合 retained 或其他具体集合。MSPT 单列 `ON − OFF`，正值表示变慢。保留负收益，不跨场景简单相加或平均百分比。

## 10. 覆盖率、失败记录和交付格式

- 先列出 `src/main/java/com/perry/asbg` 全部生产类、Mixins、事件入口、每个重要分支和对应 S/H/P/M 用例。逐场景记录命中、预期值、实测值、证据文件。覆盖率可另跑同版本诊断副本，用 JaCoCo 或等价工具产出行/分支报告；对未插桩的正式基准不声称测出了行覆盖率。
- Mixin handler 合入 Minecraft 类，普通只收集 `com.perry.asbg.*` 的覆盖率会遗漏它们。诊断运行需验证插桩顺序、导出实际转换后的目标类与行号映射，或使用测试专用命中计数与行为断言补证。分别列出“可精确计算的行/分支覆盖”“已证明执行的 Mixin handler”“仅静态审查”“未覆盖及原因”。不要把类成功加载、方法被注册或同名源码行自动算为命中。实际只能在关闭组或故障配置命中的分支注明所属组。
- 每个失败附 runId、版本哈希、坐标和初态、命令/调用路径、第一处错误与后续异常。预期的异常限于指定夹具。对启动时可选注入未命中、异步线程错误、chunk 保存失败或 Polymer 展示状态不符逐项归因。若修复了 AirBeGone、测试驱动或依赖，分批留档；最终对照表两侧必须来自同一修订与测试规程。
- 交付 `manifest.json`（含母档、JAR、配置、种子、JVM、runId）、`actions.jsonl`、`checks.json`、`ticks.csv`、`heap-index.csv`、逐堆分析 JSON、覆盖矩阵、完整日志和九场景 Markdown 报告。将实验原始证据写入对应批次目录，将当前实现的结果汇总到 [性能报告](BENCHMARK.md)。
- **最终表每行一个场景**。列出实际 min/max、ON/OFF 运行 ID、FULL/wrapper/Proto 数、公共断言及内部断言状态、离线可读的两份 dump 链接、全堆 OFF/ON MiB 与差值/百分比、section/heightmap/POI 联合 retained 分项、静置/首次写/稳态 MSPT、异常或噪声说明。补一张专表说明每个环境最主要的内存来源与性能代价。
- 结束前机械核对九个指定 ID 各有且仅有一对被选入主表的有效运行，18 份正式 HPROF 能打开且哈希与索引相符，18 个 write→reload 结果链完整，所有声明 PASS 的功能都有两组相应证据。未满足项保留空值和阻碍原因，不用以前的 dump、其他地形或推算值补齐。
