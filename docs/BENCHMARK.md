# 内存基准：四组生产服务器 heap dump

## 结果

本基准使用 2026-11-12 至 2026-11-13 同一大厅服务器、同一虚空建筑的四份 heap dump，比较未优化、仅 ASBG、仅 Polymer 延迟分配，以及两项同时开启的内存占用。

相对未优化快照，两项同时开启时，全部 sections 的**联合 retained size 从 1,419.686 MiB 降到 189.891 MiB，减少 1,229.796 MiB，下降 86.624%**。同一分析器模型下，全堆可达对象总占用从 1,815.723 MiB 降到 586.458 MiB，下降 67.701%。这是两项优化的叠加结果；在已开启 Polymer 优化的相同加载量下，ASBG 进一步减少 section retained size **565.518 MiB**。

未优化、仅 Polymer、两项同时开启三组具有相同的区块、section 和建筑计数；“仅 ASBG”组多加载 143 对完整区块及其包装器，不能视为严格等加载量对照。本基准只评价内存；heap dump 不提供 MSPT 或吞吐量测量。

## 统一口径

- 分析器为 JProfiler 16.3，全部内存分项使用完整对象集合 `heap.sizes.retainedBytes`。
- 联合 retained size 是整组对象共同独占保留的内存，包含只有经该组对象才能到达的数据；查询覆盖完整集合，不以直方图前几项代替全量。
- 每个对象集合独立计算联合 retained set，已经处理共享与嵌套对象的去重；不使用逐对象 retained size 累加或 shallow size 乘数量。
- section、容器、Lithium 数据、Polymer 集合之间存在包含关系，**表格各行不能相加**。
- 全堆总量为分析器中全部可达对象的去重占用，不是另外一组能与 retained 分项相加的数值。
- 四组均采用同一默认 HPROF 对象尺寸模型。retained set 的归属依据引用图，字节数还依赖分析器的对象尺寸模型；不混用按实际 JVM 布局另行推算的数值，也不将百分比解释为 RSS 降幅。
- `1 MiB = 1,048,576 B`；表格保留三位小数，差值和百分比使用未舍入的原始字节数计算。

分析为离线只读操作，原始 HPROF 未修改。其中的 shallow/deep 字段仅保留为查询证据，本文收益以联合 retained size 为依据。

## 文件和可比性

共同目录：`J:\Islands\1.21.11\lobby-server\server\config\spark`。

服务端为 Minecraft 1.21.11，场景是虚空中的大型建筑。dump 中维度类型为 `minY=-2032, height=4064`，方块 Y 范围为 `-2032..2031`，每套区块 section 数组长度为 254。这里的 2032 是上边界，实际总高度为 4064。

| 组 | 文件 | 优化状态 |
| --- | --- | --- |
| A | `heap-2026-11-12_22.01.31.hprof` | 两项均未开启 |
| B | `heap-2026-11-12_21.56.19-with-optimize.hprof` | 仅 ASBG |
| C | `heap-2026-11-13_00.57.17.hprof` | 仅 Polymer 延迟分配 |
| D | `heap-2026-11-13_01.04.27.hprof` | ASBG + Polymer 延迟分配 |

| 负载指标 | A | B | C | D |
| --- | ---: | ---: | ---: | ---: |
| `LevelChunk` | 5,041 | 5,184 | 5,041 | 5,041 |
| `ImposterProtoChunk` | 5,041 | 5,184 | 5,041 | 5,041 |
| section 数量 | 2,560,828 | 2,633,472 | 2,560,828 | 2,560,828 |
| 非空气 sections | 7,388 | 7,388 | 7,388 | 7,388 |
| 非空气方块计数总和 | 3,592,107 | 3,592,107 | 3,592,107 | 3,592,107 |
| 真正的 `ProtoChunk` | 0 | 0 | 0 | 0 |
| 普通 `PalettedContainer` | 5,121,656 | 7,388 | 5,121,656 | 7,388 |
| ASBG 共享容器 | 0 | 4 | 0 | 4 |
| Polymer `ShortOpenHashSet` | 5,121,656 | 5,266,944 | 2 | 2 |

C 与 D 的建筑计数均通过对全部 section 的 `nonEmptyBlockCount` 全量分组计算，均为 1,387 个不同计数组，分组数量合计为 2,560,828。不是根据文件名或启动目录推断负载相同。

`ImposterProtoChunk` 的父类 backing 数组也实际保留了独立 sections，因此计数包含完整区块和包装器两套数组。相同的建筑计数能支持快照间的比较，但四组仍是不同时间的单次快照，并非多轮重复实验。

## 联合 retained size 横向比较

除标明字节数的两格外，单位均为 MiB：

| 完整对象集合 | A：未优化 | B：仅 ASBG | C：仅 Polymer | D：两项叠加 |
| --- | ---: | ---: | ---: | ---: |
| 全部 sections | 1,419.686 | 877.445 | 755.409 | **189.891** |
| 其中全部空 sections | 1,383.165 | 841.488 | 720.804 | **155.850** |
| 普通 `PalettedContainer` | 423.270 | 33.083 | 423.270 | **33.083** |
| Lithium `SectionData` 及独占数据 | 234.451 | 60.783 | 234.451 | **59.120** |
| Polymer 集合及独占数组 | 664.277 | 683.121 | **272 B** | **272 B** |
| 其中空 Polymer 集合 | 664.277 | 683.121 | **0** | **0** |

区块级交叉验证：全部 `LevelChunk` 的联合 retained size 分别为 `766.377 / 496.648 / 434.239 / 152.015 MiB`，全部 `ImposterProtoChunk` 分别为 `752.517 / 466.005 / 401.930 / 102.900 MiB`。这些集合与 section 集合存在包含关系，也不能跨行相加。

## 收益归因

A、C、D 具有相同的区块和 section 数量，联合 retained 数据为：

```text
A sections = 1,488,648,800 B
C sections =   792,103,856 B
D sections =   199,114,736 B

A → C：Polymer 贡献 696,544,944 B = 664.277023 MiB
C → D：ASBG 追加贡献 592,989,120 B = 565.518494 MiB
A → D：合计         1,289,534,064 B = 1,229.795517 MiB
```

这是相同加载量下的联合 retained set 差值。A→C 的降低恰好等于 A 全部空 Polymer 集合的联合 retained size；C→D 则保留了 Polymer 仅两个集合的状态，同时共享了空气/群系容器及 Lithium 空气段计数数组。

仅开启 ASBG 的 B 组相对 A 减少 section retained size 542.241 MiB（38.194%），全堆减少 540.076 MiB（29.744%）。由于 B 的加载量更多，评估 ASBG 在相同区块数量下的增量收益应使用 C→D 的 565.518 MiB。

默认模型下，空 section 的实测 retained size 为：

| A | B | C | D |
| ---: | ---: | ---: | ---: |
| 568 B | 336 B | 296 B | **64 B** |

D 中 2,553,440 个空段的联合 retained size 为 163,420,160 B，恰好等于每段 64 B。共享池由全局缓存持有，不会在每个 section 的 retained set 中重复计入；四个共享容器本身的联合 retained size 仅 320 B。

Polymer 原先为每个 section 创建 `polymer$blocks` 和 `polymer$lights` 两个位置集合。A 中 5,121,656 个集合里，5,121,654 个为空；C、D 均只剩两个实际使用的集合，联合 retained size 共 272 B。ASBG 自身不负责删除这些 Polymer 索引。

GC Roots 与入引用检查确认了以下保留路径，并确认集合各自独占其 backing 数组：

```text
Server thread / MinecraftServer
  → ServerLevel.loadedChunks → HashSet / HashMap
  → LevelChunk → sections[] → LevelChunkSection
  → polymer$blocks / polymer$lights → ShortOpenHashSet → short[]
```

## 全堆变化和剩余占用

| 指标 | A | B | C | D |
| --- | ---: | ---: | ---: | ---: |
| 全堆可达对象占用（MiB） | 1,815.723 | 1,275.648 | 1,151.576 | **586.458** |
| 相对 A 减少 | — | 29.744% | 36.578% | **67.701%** |
| 对象数量 | 47,156,042 | 22,060,380 | 36,914,522 | **11,362,296** |

D 的全堆总量为 614,945,768 B。相对 A，全堆减少 1,288,978,312 B（1,229.266 MiB），比 section 集合减少量少 555,752 B（约 0.530 MiB）。即除 sections 之外的其他对象合计增加了约 0.530 MiB，整体变化与分项 retained 变化吻合。

剩余 189.891 MiB 的 section 联合保留集合仍包括每段自己的 `LevelChunkSection` 和 Lithium `SectionData`、建筑的非空气方块容器及其 storage、少量实际使用的 Polymer 索引。这符合“保留 section 身份、共享其内部可去重内容”的设计。

## 数据与复核

- 数据包含四组完整对象集合的 count/retainedBytes、引用与 GC Roots 证据、建筑计数及差值。
- 复核时用同一 JProfiler 16.3 尺寸模型加载上述四份 HPROF，先核对区块数量、section 数量、数组长度和建筑计数，再分别查询表中各完整集合的联合 retained size。
- 全堆百分比分母使用各 dump 的全部可达对象总占用；分项百分比分母使用对应集合的 retained size。共享或嵌套集合不重复累加。

这些数字只描述本场景和上述分析器模型。实际收益取决于世界高度、空段比例、群系分布及其他模组；本基准不据此推算普通世界的内存降幅或运行时 MSPT。
