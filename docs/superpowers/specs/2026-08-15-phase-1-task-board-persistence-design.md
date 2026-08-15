# Phase 1 子系统①：任务与看板工作流 + 本地持久化 设计

**Status:** 已批准设计（待用户 review）

**Date:** 2026-08-15

**Repository:** `kmp-seraphim-framework`

**Reference product:** `daily-board`

**Parent design:** [KMP Multi-Project Workbench Design](2026-08-14-kmp-multi-project-workbench-design.md)

## 1. 目标

本 spec 是 Phase 1 拆解后的第 1 个子系统：在 [daily-board](2026-08-14-kmp-multi-project-workbench-design.md) 中实现完整的任务与看板工作流（单看板 + 多列 Kanban、任务字段、归档/恢复）和本地持久化（SQLDelight 三端 + IndexedDB Web 端），并让 Android/iOS 应用真实接线、持久化跨进程可验证。

本 spec 不包含：任何平台的完整 Kanban UI、Desktop/Web 应用入口、CLI 独立导出（见 [## 13 移交后续子系统](#13-移交后续子系统) 的说明）。

## 2. Phase 1 拆解与子系统边界

Phase 1 按子系统拆解为 5 个 spec，各自走「设计 → 计划 → 实现」闭环：

| # | 子系统 | 交付物 | 本 spec 的关系 |
|---|---|---|---|
| ① | 任务与看板工作流 + 本地持久化 | 规则/命令/Store/端口、SQLDelight 与 IndexedDB 实现、Android+iOS 接线冒烟 | 本 spec |
| ② | Desktop 平台 | Compose Desktop 应用、jvm target、jvmMain 工厂、jvmTest 合约套件 | 依赖①的端口与 Store |
| ③ | Web/Wasm 平台 | TS UI、完整 web-adapter JSON 契约、浏览器测试、打包 | 依赖①的 wasm 编译能力 |
| ④ | CLI + 独立导出 | `seraphim new`、vendored platform-kit、`workbench.lock` | 独立于①-③，复用生成器 |
| ⑤ | Android/iOS 完整 Kanban UI | 两端真实看板界面 | 依赖①，在②③之后实现 |

## 3. 澄清决策

| 主题 | 决策 |
|---|---|
| 看板模型 | 单看板 + 多列 Kanban |
| 列管理 | 中间列可自由增删改排；首列（START）与末列（END）固定 |
| 完成语义 | 进入 END 列即完成；无独立 completed 布尔 |
| 归档 | 移出活动视图、保留可恢复；归档只保留任务本身，恢复回到 START 列末尾 |
| 任务字段 | id + title + 可空 notes + 可空 dueDate + createdAt |
| 持久化 | SQLDelight + SQLite，`.sqm` 迁移 + 启动版本校验 |
| Web 存储 | IndexedDB Adapter 本 spec 完整落地并验证（wasmJs target + Node 合约测试） |
| 条件发射 | web 未选时，wasm target 与 web 存储模块完全缺席（见 [## 9 Manifest、条件发射与认证夹具](#9-manifest条件发射与认证夹具)） |

## 4. 架构与模块拓扑

### 4.1 模块图

```text
products/daily-board/shared/
├── task-board/       规则、命令、Store、TaskBoardRepository 端口
│                      target：android + ios；web 选中时再加 wasmJs
│   └── wasmJsMain    WebTaskBoardAdapter（JSON 订阅面，最小版）
├── local-data-sql/   SQLDelight 实现（android + ios；jvm 随子系统②）
└── local-data-web/   IndexedDB 实现（仅 wasmJs；web 未选时整个模块不存在）
```

- 依赖方向：`local-data-sql → task-board`、`local-data-web → task-board`（实现依赖端口，端口与规则同住 task-board，与父设计 §10「task-board 持 rules/state/ports」一致）。
- Android app 依赖 `task-board + local-data-sql`；iOS app 只链接 `LocalDataSql.framework`（task-board 经 `export(project(":shared:task-board"))` + `transitiveExport = true` 并入该 framework——K/N 要求一个进程只嵌入一份 Kotlin 运行时，两个静态 framework 同链会在加载期 `injectToRuntime` 断言崩溃，Task 17 已按此修正）；未来的 Web app 依赖 `task-board + local-data-web`。
- **模块拆分理由**：SQLDelight Gradle 插件与 `wasmJs` target 无法在同一模块干净解耦；两个存储引擎的目标约束完全不同（SQL 三端 vs 浏览器单端）。父设计 §10 规定「不同目标约束」是成立新 Module 的正当理由。此拆法也保证：web 未选时，`local-data-web` 模块、`wasmJs` target、`wasmJsMain` 源目录全部缺席（不变量 3）。
- 新增依赖：`kotlinx-coroutines-core`（Flow，全 target 含 wasmJs）。不引入 kotlinx-datetime、kotlinx-serialization。
- 版本目录新增：coroutines、SQLDelight（plugin + android/native drivers）、Robolectric。具体版本按 [toolchain policy](../../../CONTEXT.md) 的「兼容交集」原则在计划阶段锁定并验证（Kotlin 2.4.10 / AGP 9.1.0 / Gradle 9.3.1）。
- SQLDelight 插件只被 `local-data-sql` 一个模块应用，无需根提升；跨模块共享插件仍遵守 [root plugin classloader hoisting ADR](../../adr/2026-08-15-root-plugin-classloader-hoisting.md)。

### 4.2 分层与数据流（有状态 Store + 事务化 Repository）

```text
Platform App ──命令──> TaskBoardStore (task-board)
                         │ ① 业务校验（规则层，失败即抛 BoardError，零存储写入）
                         │ ② 内存态变更 → 计算整批 delta
                         │ ③ repository.apply(deltas) 事务提交
                         │ ④ 提交成功才推进内存快照
                         │ ⑤ 发布 BoardSnapshot 到 StateFlow
Platform Adapter <──观察──┘
```

- **Store 是唯一真相**：内存态即规范态；持久层是哑存储，只原子应用整行 upsert/delete delta，不含业务逻辑。
- **提交后才推进快照**：`apply` 返回前 `StateFlow` 不更新（父设计 §14）。
- **并发**：Store 用 `Mutex` 串行化所有命令与 `open()`；repository 的 SQL 调用在串行上下文内同步执行（个人规模数据量），IndexedDB 的异步用 `kotlin.js.Promise.await()` 桥接为 suspend。

### 4.3 时间表示

- `createdAt` / `archivedAt`：`kotlin.time.Instant`，持久化为 epoch 毫秒。
- `dueDate`：按 UTC 日历日截断到当天 00:00 的 epoch-day（值类 `EpochDay(Long)` 包装）；本地日期显示由平台 adapter 负责。

## 5. 领域模型与命令

### 5.1 类型

```kotlin
// task-board commonMain
data class ColumnId(val value: Long)
data class TaskId(val value: Long)

enum class ColumnKind { START, MIDDLE, END }

data class Column(
    val id: ColumnId,
    val name: String,
    val kind: ColumnKind,   // 由位置推导，不单独落库
    val rank: Int,
)

data class TaskItem(
    val id: TaskId,
    val columnId: ColumnId,   // 已归档任务此字段无效（见不变量 4）
    val title: String,
    val notes: String?,
    val dueDate: EpochDay?,
    val rank: Int,
    val createdAt: Instant,
    val archivedAt: Instant?,
)
```

### 5.2 不变量

1. 看板恒有 ≥1 列；**首列 START、末列 END**，二者不可删除、不可移动、不可交换；MIDDLE 列只在二者之间增删。
2. 列名 trim 后非空、≤80 字符；列名不必唯一。
3. 任务 `title` trim 后非空、≤500 字符；`notes` ≤2000 字符；空串归一化为 null。
4. 活动任务必须属于现有列；**已归档任务不占用任何列**（`archivedAt != null` 时 `columnId` 视为无效）。
5. 恢复：回 START 列末尾，rank 重新分配，`archivedAt` 清空。
6. rank：列间与列内都是稠密非负整数 `0,1,2,…`；增删/移动后**目标范围局部重排**；排序键 `(rank, id)` 保证稳定全序。
7. 快照不可变：集合均为不可变 List，修改产生新对象。

### 5.3 命令集

| 命令 | 语义 |
|---|---|
| `open()` | 从 repository 加载；结构自愈见 [## 8.3](#83-自愈边界仅限结构不碰数据) |
| `createColumn(name)` | 新列插入 END 之前（倒数第二位） |
| `renameColumn(id, name)` | 任何列可改名（含首/末） |
| `deleteColumn(id)` | 仅 MIDDLE；**列内任务全部归档**（见 5.4） |
| `moveColumn(id, toIndex)` | 仅 MIDDLE；`toIndex ∈ 1..last-1` |
| `createTask(columnId, title, notes?, dueDate?)` | 入列末尾 |
| `updateTask(id, title?, notes?, dueDate?, clearDueDate=false)` | 部分更新；null = 不变，notes 传 "" = 清空，`clearDueDate=true` = 清空截止日期；归档任务不可更新 |
| `moveTask(id, toColumnId, toIndex)` | 移入 END 列 = 完成；移出 END = 重新打开；同列同位置 = no-op |
| `archiveTask(id)` | 仅限当前在 END 列的任务 |
| `restoreTask(id)` | 回 START 列末尾 |
| `snapshot()` | 当前 `BoardSnapshot` |
| `observe()` | 快照事件流（见 [## 7](#7-观察机制与平台-adapter-契约)） |

所有命令成功返回执行后的最新 `BoardSnapshot`；`updateTask` 三个字段全为 null 时视为 no-op，返回当前快照。

`BoardSnapshot`：`columns: List<Column>`（有序）+ `tasks: Map<ColumnId, List<TaskItem>>`（列内有序）+ `archivedTasks: List<TaskItem>`（按 archivedAt 倒序）。全量快照，不做增量事件。

### 5.4 删除列语义

删除 MIDDLE 列 → 列内任务全部归档（视为明确的「结束」动作，即使它们不在 END 列）。恢复时回 START。备选「先移入 END 再归档」被否决：多一次隐式移动，无实际差异。

## 6. 存储端口与实现

### 6.1 端口（task-board 定义，两个存储模块实现）

```kotlin
interface TaskBoardRepository {
    suspend fun open(): PersistedBoard        // 原始行 + id 提示
    suspend fun apply(deltas: List<BoardDelta>): Unit   // 原子；任一条失败 = 全批回滚
}

data class PersistedBoard(
    val columns: List<Column>,
    val tasks: List<TaskItem>,
    val nextColumnId: Long,   // max(column.id)+1，空表时 1
    val nextTaskId: Long,     // max(task.id)+1，空表时 1
)

sealed interface BoardDelta {
    data class UpsertColumn(val column: Column) : BoardDelta
    data class DeleteColumn(val id: ColumnId) : BoardDelta
    data class UpsertTask(val task: TaskItem) : BoardDelta
    data class DeleteTask(val id: TaskId) : BoardDelta   // 保留完整性，当前命令集不用
}
```

**delta 是整行 upsert/delete**：Store 一次算好规范行（含 rank 重编号、归档、列删除的连带任务归档），实现只做哑存储——四端实现零业务逻辑，这是合约测试与一致性验证的核心。id 在 Store 分配（`open()` 返回 `max(id)+1`，后续自增），两端都以显式 id 写入。

### 6.2 SQLDelight schema（`local-data-sql`，`1.sqm`）

```sql
CREATE TABLE board_column (
    id INTEGER NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    rank INTEGER NOT NULL
);
CREATE TABLE task (
    id INTEGER NOT NULL PRIMARY KEY,
    column_id INTEGER REFERENCES board_column(id),   -- NULL = 已归档
    title TEXT NOT NULL,
    notes TEXT,
    due_date INTEGER,               -- epoch-day
    rank INTEGER NOT NULL,          -- 归档任务写 0
    created_at INTEGER NOT NULL,    -- epoch millis
    archived_at INTEGER             -- NULL = 活动
);
CREATE INDEX task_by_column ON task(column_id, rank);
CREATE INDEX task_by_archived ON task(archived_at);
```

- `ColumnKind` 不落库，由加载后的排序位置推导（首/末/中间），杜绝存储与结构语义分叉。
- 打开时 `PRAGMA foreign_keys=ON` + `PRAGMA user_version` 校验；版本高于当前支持 → 明确抛错，不自作聪明（见 [## 8.3](#83-自愈边界仅限结构不碰数据)）。
- 迁移走 `.sqm`：本 spec 落 `1.sqm`；迁移机制以「手造 v0 库（user_version=0、无表）→ `Schema.migrate` 到 1」测试验证；首个真实迁移（`2.sqm`）留给下一次 schema 变更。
- 查询仅 3 条：全量 `selectColumns`、全量 `selectTasks`、行级 upsert/delete（个人规模，`open()` 一次全量加载）。

### 6.3 工厂函数（app 只碰工厂，不碰驱动）

```kotlin
// androidMain:  fun taskBoardRepository(context: Context): TaskBoardRepository
// iosMain:      fun taskBoardRepository(): TaskBoardRepository
// wasmJsMain:   fun taskBoardRepository(): TaskBoardRepository   // local-data-web
// jvmMain:      fun taskBoardRepository(directory: Path): TaskBoardRepository  // 随子系统②
```

SQL 三端共用一个 `SqlDelightTaskBoardRepository(schema, driver)` 实现（commonMain 基于生成查询写一次），平台源集只差 driver 构造（`AndroidSqliteDriver` / `NativeSqliteDriver`）。

iOS 接线修正（Task 17）：`LocalDataSql.framework` 以 `export(project(":shared:task-board"))` + `transitiveExport = true` 导出 task-board（app 只链接这一个静态 framework，规避 K/N 双运行时崩溃）；export 要求导出项目及其传递依赖为 API 依赖（task-board 的 coroutines-core、local-data-sql 的 task-board 均为 `api`）；sqldelight native driver 的 sqlite3 cinterop 在 iOS 无 linkerOpts 且静态 framework 不传播 linkerOpts，app 侧 `OTHER_LDFLAGS: -lsqlite3` 自链系统 sqlite3。Swift 侧经 `TaskBoardRepositoryFactories_iosKt.taskBoardRepository(name:)` 取工厂（文件 `TaskBoardRepositoryFactories.ios.kt` 的 ObjC 导出名；K/N 不生成默认参数重载，须显式传 name）。

### 6.4 IndexedDB 实现（`local-data-web`）

- 数据库 `daily-board`，两个 object store：`columns`、`tasks`（`keyPath: "id"`，显式 id 写入，与 SQLite 侧一致）。个人规模无二级索引，`open()` 全量 `getAll`。
- JS 互操作只用 `@JsFun` 访问全局 `indexedDB`（open / transaction / put / delete / getAll），无 npm 依赖；异步用 `kotlin.js.Promise.await()`。
- `apply(deltas)` 把整批 delta 放进**一个** `readwrite` transaction（IndexedDB 原生事务 = 原子提交，免费获得回滚语义）。
- 实现类经**可注入的数据库句柄**构造（工厂读全局 `indexedDB`），`wasmJsNodeTest` 注入手写内存版 IndexedDB fake（约 150 行，只实现用到的 API 子集，确定性、零 npm 依赖）。真实浏览器行为验证归子系统③。

### 6.5 端口合约测试（commonTest 写一次，各端各跑）

- 往返：apply 一批 delta → 重新 open → 行完全一致；
- 原子性：中途失败（fake 对第 2 条 put 抛错 / SQLite 非法语句）→ 断言第 1 条也回滚；
- upsert 幂等、删除不存在的行是 no-op、id 分配单调不重用；
- user_version 不符 → open 抛错。

**落地矩阵**：`androidHostTest`（Robolectric 提供 Context，真实 SQLite，跑完整 SQL 合约 + 迁移测试）+ `iosSimulatorArm64Test`（真实 `NativeSqliteDriver`，同套合约）+ `wasmJsNodeTest`（fake IndexedDB，完整 Web 合约）。

## 7. 观察机制与平台 Adapter 契约

### 7.1 共享侧

- Store 持 `MutableStateFlow<BoardSnapshot?>`；`snapshot()` 返回当前值，`observe(): StateFlow<BoardSnapshot?>` 暴露给 Kotlin 侧 adapter（`StateFlow` 无法携带异常，初始化失败经 `open()` 的 `StorageError` 传播，入口按 §8.4 进入错误态 UI）。
- `StateFlow` 天然去重 + 新订阅者立即收到当前值；`open()` 成功前值为 null，成功后才发出首个快照。
- 不暴露任何 Lifecycle / Disposable / 回调注册表（平台概念，父设计 §11）。

### 7.2 各平台契约（本 spec 定义；实现归属见 [## 2](#2-phase-1-拆解与子系统边界)）

- **Android / Desktop**：`observe().collectAsState()`，UI 直接渲染不可变快照（Android 接线在本 spec，UI 在子系统⑤；Desktop 全在子系统②）。
- **iOS**：`TaskBoardIosAdapter` 生命周期感知：`startObserving(onSnapshot: (BoardSnapshot) -> Unit)` 主线程分发，`stopObserving()` 取消。
- **Web**：`task-board/wasmJsMain` 暴露显式订阅 + 显式销毁 + JSON 快照，不泄漏 Flow 到 TS。Kotlin 2.4.10 的 wasmJs 只支持函数级 `@JsExport`（class 级注解被编译器拒绝），因此表面为顶层导出函数 + `JsReference<T>` 不透明句柄：`createWebTaskBoardAdapter(store)` / `subscribe(adapter, onSnapshot)` / `dispose(adapter, subscription)`（语义与计划的类形式等价：显式订阅、显式销毁、JSON 序列化快照）。
  JSON 序列化手写（快照类型 `toJson()`），不引入 kotlinx-serialization。完整 TS 契约与真实浏览器测试归子系统③，届时此最小版迁入/扩展为父设计 §10 的 `shared/web-adapter`。

### 7.3 线程模型

- 命令线程无关（`Mutex` 串行化），suspend API 由调用方选择上下文；
- 快照在调用方线程发布；Android 用 `collectAsState`、iOS 用 main dispatcher 桥、Web 由浏览器单线程天然满足。

## 8. 错误处理

### 8.1 Typed errors（规则层）

校验失败抛封闭 `BoardError` 层级（不抛裸 `IllegalArgumentException`）：

```kotlin
sealed class BoardError : RuntimeException() {
    data class InvalidTitle(val title: String) : BoardError()
    data class InvalidColumnName(val name: String) : BoardError()
    data class InvalidNotes(val notes: String) : BoardError()
    data class NotUpdatable(val id: TaskId) : BoardError()         // 归档任务不可更新/移动
    data class ColumnNotFound(val id: ColumnId) : BoardError()
    data class TaskNotFound(val id: TaskId) : BoardError()
    data class NotArchivable(val id: TaskId) : BoardError()      // 不在 END 列
    data class NotRestorable(val id: TaskId) : BoardError()
    data class ColumnNotDeletable(val id: ColumnId) : BoardError() // START/END
    data class ColumnNotMovable(val id: ColumnId) : BoardError()
    data class IndexOutOfRange(val index: Int, val size: Int) : BoardError()
    data class NotOpen : BoardError()
}
```

校验失败不触碰存储（不进事务），失败后 store 状态与快照保持原样——逐条测试验证。

### 8.2 存储失败（repository 层）

- `apply` 失败：SQLite 回滚 / IndexedDB abort → 异常冒泡给调用方；内存态不推进、快照不发布。
- `open()` 失败（损坏、user_version 不符）：store 保持未初始化，初始化失败经 `open()` 的 `StorageError` 传播（StateFlow 无法携带异常，见 §7.1）；平台显示明确错误页面而非崩溃，**不自动清库**（数据是用户财产）。
- 存储异常在边界包装为 `StorageError(cause)`，不把 SQLException / DOMException 泄漏给 UI。

### 8.3 自愈边界（仅限结构，不碰数据）

- 列完全为空：建 START/END 两列（新用户首启），默认名「开始」「结束」。
- 列存在但首末错乱（外部篡改）：按 rank 排序后标记首末，不改数据。
- 孤儿任务（`column_id` 指向不存在列）：**不静默修复**，`open()` 抛 `StorageError` 附诊断。

### 8.4 平台入口

app 创建点（Android `Application` / iOS `App`）构造 repository → store → `open()`，失败进入错误态 UI；`BoardError` 由各平台映射为可展示消息（shared 只给结构化类型）。

## 9. Manifest、条件发射与认证夹具

### 9.1 条件发射规则（不变量 3 的机械实现）

模板新增两枚条件 token，由渲染器按 `manifest.platforms.web` 求值：

- `__WEB_MODULES__`（settings.gradle.kts）→ web 选中时 `include(":shared:local-data-web")`，否则空；
- `__WASM_JS_TARGET__`（task-board/build.gradle.kts）→ web 选中时 `wasmJs { nodejs() }`，否则空。

渲染器过滤规则（web 未选时跳过）：`shared/local-data-web/` 模块目录、任意 `src/wasmJsMain/` 源目录。web 选中时 `local-data-web/build.gradle.kts` 无条件（模块只在选中时存在）。生成树校验器继续保证无残留 `__` token。

### 9.2 ManifestValidator 扩展

接受平台集 `{android, ios}` 与 `{android, ios, web}`；`desktop` 仍拒绝（子系统②开启时再扩展）；`data.strategy` 仍 `local-only`；schema 仍为 1（`platforms.web` 字段已存在于 schema，无 schema 变更）。

### 9.3 认证夹具

`project.yaml` 保持 `web: false`（Web UI 入口不存在，manifest 只描述当前真实拓扑）。新增版本化夹具 `certification/web-enabled.yaml`：

```yaml
schema: 1
product:
  id: daily-board
  package: com.seraphim.dailyboard
platforms:
  android: true
  ios: true
  desktop: false
  web: true
data:
  strategy: local-only
```

certify 脚本渲染该夹具到 `build/certification/daily-board-web`，运行 `:shared:task-board:wasmJsNodeTest` + `:shared:local-data-web:wasmJsNodeTest`，作为「Web 存储能力」组合的认证证据。维护产品认证（`project.yaml`）不含任何 wasm/Node 任务。

### 9.4 渲染器改动

`ProductRenderer` 的 token 表增加两枚条件 token，`Files.walk` 增加上述过滤谓词；渲染 web:true/false 两种 manifest 的生成树结构测试补齐。

## 10. 测试与认证

### 10.1 测试分层

| 层 | 位置 | 内容 |
|---|---|---|
| 规则/命令 | task-board commonTest | 全部命令正反例；校验失败零存储写入（recording fake 断言零 delta） |
| 端口合约 | local-data 各模块 commonTest | [## 6.5](#65-端口合约测试commontest-写一次各端各跑) |
| 迁移 | local-data-sql androidHostTest | 手造 v0 → migrate 到 1；create 后 version=1；高版本拒绝打开 |
| 平台冒烟 | androidHostTest / iosSimulatorArm64Test / wasmJsNodeTest | 真实驱动：打开 → 写 → 读回最小路径 |
| Web 观察契约 | task-board wasmJsNodeTest | subscribe → 命令 → 收到 JSON 快照 → dispose 后静默 |

### 10.2 rank 确定性

对 `moveTask` / `moveColumn` / `createTask` 做 200 次随机种子操作序列，每步断言 `(rank, id)` 全序成立、稠密连续、归档任务不参与列内排序。

### 10.3 认证增量

- 维护产品（`project.yaml`）：`:shared:local-data-sql` 的 androidHostTest + iosSimulatorArm64Test、`:shared:task-board:allTests`、`:apps:android:assembleDebug`、iOS framework 链接。
- Web 夹具：见 [## 9.3](#93-认证夹具)。
- CI 矩阵：iOS simulator + Xcode 在 macos-26（沿用现有 workflow）；androidHostTest / assembleDebug / wasmJsNodeTest（Gradle 内置 Node）在 ubuntu。workflow 具体拆分由实施计划定。

### 10.4 维护产品再渲染

模板变更后重新 Render `products/daily-board`（原子替换）并跑认证；生成树与模板同构。

## 11. 非目标

- 增量事件、乐观并发、审计日志（与 Phase 2 操作日志一起）。
- 软删除墓碑。
- 多语言、格式化、排序偏好。
- `ProjectManifest` 新字段（本 spec 无 schema 变更）。
- shared 里的 ViewModel / 导航 / 完整 UI。
- 真实浏览器测试（归子系统③）。
- Desktop target 与 jvmMain 工厂（归子系统②）。
- Android/iOS 完整 Kanban UI（归子系统⑤）。
- kotlinx-serialization / kotlinx-datetime 依赖。
- 迁移文件 `2.sqm`（机制已测，首个真实迁移随下一次 schema 变更）。

## 12. 被否决的备选

- **DB 即真相、无状态处理器**：每次变更后全量重查；四端各自实现查询逻辑，IndexedDB 端与 SQL 端行为分叉大，观察退化为轮询式通知，Phase 2 接入更绕。
- **第一天事件溯源**：与 Phase 2 outbox 同构但违背 YAGNI；父设计 §15 明确把 operation log 推迟到 Phase 2。
- **单模块 `local-data` 承载全部四端**：SQLDelight 插件无法与 wasmJs target 干净解耦，且目标约束混杂；已改为两模块（见 [## 4.1](#41-模块图)）。
- **删除列时任务先移入 END 再归档**：多一次隐式移动，无实际差异。
- **归档保留原列引用**：需处理列删除后的引用失效，复杂度无收益。
- **完整快照归档（冻结列结构）**：与「列可自由增删」冲突。
- **IndexedDB 实现写入本 spec 但不编译验证**：违反仓库「实现必须有可执行证据」纪律。

## 13. 移交后续子系统

- **② Desktop**：task-board 与 local-data-sql 加 `jvm("desktop")` target；`taskBoardRepository(directory: Path)` 工厂；jvmTest 跑同一套 SQL 合约；Compose Desktop app 接 `observe()`。
- **③ Web/Wasm**：TS UI、完整 JSON 契约（`shared/web-adapter`，本 spec 的 `WebTaskBoardAdapter` 届时迁入/扩展）、真实浏览器/Playwright 测试、打包；web:true 后的 `apps/web` 入口。
- **④ CLI**：`seraphim new`、vendored platform-kit、`workbench.lock`，复用生成器引擎。
- **⑤ Android/iOS Kanban UI**：完整看板界面；本 spec 只交付接线 + 持久化冒烟。

## 14. 验收标准

1. 全部命令的规则测试通过；校验失败时 storage fake 收到零 delta。
2. SQL 合约套件在 androidHostTest（Robolectric 真实 SQLite）与 iosSimulatorArm64Test 通过；IndexedDB 合约套件在 wasmJsNodeTest（fake）通过。
3. rank 随机 200 序列后 `(rank, id)` 全序且稠密。
4. 迁移：create 后 version=1；手造 v0 → migrate 到 1；高版本拒绝打开。
5. Web 观察契约：subscribe → 命令 → JSON 快照 → dispose 静默。
6. `project.yaml` 认证无任何 wasm target/模块/任务；web-enabled 夹具认证跑通 wasm 测试。
7. Android/iOS app 创建任务后进程内持久化、重开仍在（认证冒烟）。
8. 渲染器 web:true/false 双路径确定性 + 过滤规则测试通过；生成树无残留 token。
9. `./scripts/check.sh full` 通过；维护产品重新 Render 后与模板同构。
10. 共享代码不含任何平台 UI（既有约束保持）。
