# Phase 1 子系统①：任务与看板工作流 + 本地持久化 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 在 daily-board 中实现单看板多列 Kanban 的任务/看板工作流与本地持久化（SQLDelight 三端 + IndexedDB Web 端），并接线 Android/iOS 应用。

**Architecture:** 共享 TaskBoardStore 持有规范内存状态（Mutex 串行化），命令经业务校验后以整行 upsert/delete delta 事务化写入 TaskBoardRepository 端口，提交成功才推进 StateFlow 快照。SQL 三端（android/ios，jvm 随子系统②）共用 SQLDelight 生成查询；Web 端用 typed external interface + @JsFun 封装 IndexedDB。web 未选时 wasm target 与 web 存储模块经条件 token/过滤规则完全缺席。

**Tech Stack:** Kotlin 2.4.10、AGP 9.1.0（KMP library 插件 + withHostTest）、SQLDelight 2.2.1、kotlinx-coroutines 1.11.0、Robolectric 4.16.1、Kotlin/Wasm（wasmJs nodejs 测试）。

**Spec:** [2026-08-15-phase-1-task-board-persistence-design.md](../specs/2026-08-15-phase-1-task-board-persistence-design.md)

## Plan Errata

- 计划命令中的 `:shared:<module>:androidHostTest` 任务名在 AGP 9.1 KMP 工具链下实际为 `testAndroidHostTest`（源集名仍为 `androidHostTest`）；所有任务按 `testAndroidHostTest` 执行。
- `BoardError.NotOpen` 是普通 class（Kotlin 不允许零参 data class），名称/消息/层级不变。
- 本机执行 Gradle 前需 `export ANDROID_HOME=/Users/lanceli/Library/Android/sdk`（会话环境未设置该变量）。
- `Instant.now()` 在锁定 Kotlin 2.4.10 下是 deprecation ERROR：一律用 `kotlin.time.Clock.System.now()` 替代（Task 5/7 的实现与测试代码同样适用）。
- Task 5 的 updateTask 实现中，notes 的语义修正为：参数非 null 时以校验结果为准（"" 归一化为 null = 清空），null 表示不变——实现写 `if (notes != null) validateNotes(notes) else current.notes`（计划原文的 `?.let ?: current.notes` 在清空场景吞掉 null，已被实现修正）。
- Task 8 的 RankPropertyTest 断言修正：`(rank,id)` 全序 ≠ id 升序（moveTask 重排会破坏 id 升序）；断言应为列内列表等于按 `(rank,id)` 排序的结果，同时生成器对同列移动的 toIndex 上界取 `size-1`（跨列取 `size`）。restoreTask 保持「回 START 列末尾（rank=活动任务数）」的 spec 语义不变。
- Task 9 评审发现的 delta 顺序缺陷：deleteColumn 的批次须把归档 UpsertTask 放在 DeleteColumn 之前（SQLite 即时 FK 约束要求引用行先置 NULL），已修正实现与计划；Task 10 的 PersistenceSmokeTest 增加删除含任务列的步骤锁定该行为。

- Task 9 的 settings.gradle.kts 变更**不加入** `__WEB_MODULES__` token：渲染器在 Task 12 才求值该 token，而 GeneratedTreeVerifier 拒绝渲染树残留 `__`，Task 9 加入会使 createProduct 失败并阻断尖刺。Task 12 实现条件发射时须同时把 `__WEB_MODULES__` 行加入模板 settings.gradle.kts（web 未选时渲染为空行）。
- Task 9 仓库实现用生成属性 `boardQueries` 而非计划原文的 `taskBoardDatabaseQueries`：SQLDelight 按 .sq 文件名（Board.sq）生成查询属性名。
- Android 驱动回调签名：SQLDelight 2.2.1 的 `AndroidSqliteDriver.Callback.onConfigure` 参数为 `androidx.sqlite.db.SupportSQLiteDatabase`（非 `android.database.sqlite.SQLiteDatabase`）。
- Robolectric 冒烟测试第二条用例须写 `kotlinx.coroutines.runBlocking<Unit>`：JUnit 4 校验测试方法必须返回 void，`runBlocking {}` 的尾表达式（deleteDatabase 返回 Boolean）会触发 InvalidTestClassError。
- 发现（非本任务引入，Task 1 遗留）：task-board commonMain 的 `Math.floorDiv`（java.lang.Math）在非 JVM target 无法编译；已改用纯 Kotlin 算术 `millis / 86_400_000L` + 余数为负时减一（`@JvmInline` 本身跨 target 可用），随 Task 9 修复提交。
- Task 10 的 androidHostTest 合约测试需 Robolectric 环境：commonTest 的 `RepositoryContractTest` 若在 androidHostTest 直接运行，`RuntimeEnvironment.getApplication()` 为 null（NPE）——改为经 `@RunWith(RobolectricTestRunner)` 的 `AndroidRepositoryContractTest` 子类运行同一套合约（`@RunWith` 是 `@Inherited`），并在 testAndroidHostTest 的 filter 中排除基类本身（避免 JUnit4 在无 Robolectric 环境下双跑）；基类相应改为 `open`。
- Task 10 平台 actual 的「每次调用重建/随机库名」会破坏 `deltas survive a reopen` 的测试内共享存储语义：Android actual 每次 `deleteDatabase("contract.db")`、iOS actual 每次随机名都会让重开看到空库——改为 commonTest 每测试 `@BeforeTest` 重置的缓存库名（首次调用生成随机名并缓存，测试内重开共享同一库、测试间互不污染；JUnit4 与 K/N 均按基类继承的 `@BeforeTest` 生效）。
- Task 10 MigrationTest 版本断言修正：SQLDelight 2.2.1 生成的 `TaskBoardDatabase.Schema.version` 为 2（.sq 当前 schema 计 1、1.sqm 迁移 +1），且 `AndroidSqliteDriver` 惰性建库（须 `open()` 才真正应用 schema 并写 user_version）——断言改为 `TaskBoardDatabase.Schema.version`，用例改为 `runTest` 并调用 `open()`。
- Task 10 iOS 跑通暴露两处既有代码问题（Task 9 遗留，androidHostTest 未覆盖）：① task-board commonMain `BoardModel.kt` 的 `@JvmInline` 缺显式 import（JVM 靠 `kotlin.jvm.*` 默认导入解析，native 无该默认导入）——补 `import kotlin.jvm.JvmInline`（native stdlib commonMain 有 expect 声明）；② iOS 工厂 `onConfiguration = { foreign_keys(true) }` 是旧版 API，SQLDelight 2.2.1 的 `NativeSqliteDriver.onConfiguration` 类型为 `(DatabaseConfiguration) -> DatabaseConfiguration`——改为 `{ it.copy(extendedConfig = it.extendedConfig.copy(foreignKeyConstraints = true)) }`（touchlab sqliter 的 `DatabaseConfiguration.Extended.foreignKeyConstraints`，默认 false）。

## Global Constraints

- 工具链锁定：Kotlin 2.4.10 / AGP 9.1.0 / Gradle 9.3.1 / JDK 17 / compileSdk 37 / targetSdk 37 / minSdk 26 / Xcode 26.4.x。任何升级须整个元组互证。
- 新依赖版本：kotlinx-coroutines 1.11.0、SQLDelight 2.2.1（runtime + android-driver + native-driver）、Robolectric 4.16.1。
- 共享插件须在根 plugins 块 apply false 提升（ADR）；本计划新增插件只被单模块应用（sqldelight → 仅 local-data-sql），无需提升。
- 模板只发射已选平台：web 未选时 local-data-web 模块、wasmJs target、wasmJsMain 源目录必须完全缺席（不变量 3）。
- Manifest schema 仍为 1；project.yaml 保持 web: false。
- 模板是唯一真相：改模板后必须 rm -rf products/daily-board && ./gradlew createProduct 重新渲染维护产品（生成器拒绝非空目标）。
- 中间态约定：Task 1 删除旧 TaskBoard/TaskBoardIosAdapter 后，apps:android 编译与 iOS framework 链接在 Task 16/17 之前是断的；每个任务的验证命令只触碰相关 shared 模块，不 build app 模块，直到 Task 16/18。
- 每个任务提交前跑 git diff --check；涉及 docs 的提交跑 ./scripts/check.sh focused docs；最终任务跑 ./scripts/check.sh full。
- 文件以恰好一个换行符结尾；生成树不得残留 __ token。

## File Structure

模板（唯一真相，改后重新渲染 products/daily-board）：

- templates/daily-board-base/gradle/libs.versions.toml — 新增 coroutines/sqldelight/robolectric 版本与坐标
- templates/daily-board-base/settings.gradle.kts — include :shared:local-data-sql + __WEB_MODULES__ token
- templates/daily-board-base/shared/task-board/build.gradle.kts — coroutines 依赖 + __WASM_JS_TARGET__ token
- templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/BoardModel.kt — 值类型、实体、快照、纯函数
- templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/BoardError.kt — 封闭错误层级
- templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardRepository.kt — 端口 + delta + PersistedBoard
- templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardStore.kt — 状态、命令、校验、快照
- templates/daily-board-base/shared/task-board/src/commonTest/.../taskboard/ — RecordingRepository、InMemoryRepository、各命令测试、Rank 性质测试
- templates/daily-board-base/shared/task-board/src/wasmJsMain/.../taskboard/WebTaskBoardAdapter.kt + BoardSnapshotJson.kt — Web 订阅面（web 选中才编译）
- templates/daily-board-base/shared/task-board/src/wasmJsTest/.../taskboard/WebTaskBoardAdapterTest.kt
- templates/daily-board-base/shared/local-data-sql/ — 模块：build.gradle.kts、.sq、1.sqm、commonMain 实现、android/ios 工厂、androidHostTest（Robolectric 合约 + 迁移 + 冒烟）
- templates/daily-board-base/shared/local-data-web/ — 模块：build.gradle.kts、IDB external interfaces、WebTaskBoardRepository、wasmJsTest（FakeIndexedDb + 合约）
- templates/daily-board-base/apps/android/ — DailyBoardApplication、MainActivity 接线（Task 16）
- templates/daily-board-base/apps/ios/ — TaskBoardIosAdapter 重写、ContentView、project.yml 加 LocalDataSql framework（Task 17）

工作台工具链：

- tooling/generator/src/main/kotlin/.../manifest/ManifestValidator.kt — 接受 android+ios(+web)
- tooling/generator/src/main/kotlin/.../generator/ProductRenderer.kt — 条件 token + web 过滤
- tooling/generator/src/test/.../ManifestTest.kt、ProductRendererTest.kt — 新组合与双路径渲染测试
- certification/web-enabled.yaml — Web 认证夹具（新增）
- scripts/certify-generated-product.sh、.github/workflows/certify-phase-0.yml — 认证扩展
- docs/architecture.md、README.md — 当前态与验证命令更新
- products/daily-board/ — 每任务重渲染（生成物，随模板提交）

---

### Task 1: 领域模型、错误层级与 coroutines 依赖

**Files:**
- Modify: templates/daily-board-base/gradle/libs.versions.toml
- Modify: templates/daily-board-base/shared/task-board/build.gradle.kts
- Delete: templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoard.kt
- Delete: templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardTest.kt
- Delete: templates/daily-board-base/shared/task-board/src/iosMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardIosAdapter.kt（Task 17 重建）
- Create: templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/BoardModel.kt
- Create: templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/BoardError.kt
- Create: templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/BoardModelTest.kt

**Interfaces:**
- Produces: ColumnId(Long)、TaskId(Long)、EpochDay(Long)（epochMillis、fromInstant）、ColumnKind{START,MIDDLE,END}、Column(id,name,kind,rank)、TaskItem(id,columnId,title,notes,dueDate,rank,createdAt,archivedAt)、BoardSnapshot(columns,tasks,archivedTasks)、columnKindAt(index,size)、常量 MAX_COLUMN_NAME_LENGTH=80/MAX_TITLE_LENGTH=500/MAX_NOTES_LENGTH=2000/START_COLUMN_NAME="开始"/END_COLUMN_NAME="结束"、sealed BoardError（全部子类型）、StorageError。Task 2+ 依赖这些精确名字。

- [ ] **Step 1: 写失败测试**

templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/BoardModelTest.kt:

```kotlin
package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class BoardModelTest {
    @Test
    fun `single column is both start and end`() {
        assertEquals(ColumnKind.START, columnKindAt(0, 1))
    }

    @Test
    fun `first and last columns are fixed kinds`() {
        assertEquals(ColumnKind.START, columnKindAt(0, 3))
        assertEquals(ColumnKind.MIDDLE, columnKindAt(1, 3))
        assertEquals(ColumnKind.END, columnKindAt(2, 3))
    }

    @Test
    fun `epoch day truncates to the utc calendar day`() {
        val noon = Instant.fromEpochMilliseconds(86_400_000L * 3 + 12 * 3_600_000L)
        assertEquals(3L, EpochDay.fromInstant(noon).value)
        assertEquals(86_400_000L * 3, EpochDay.fromInstant(noon).epochMillis)
    }

    @Test
    fun `epoch day handles pre-epoch instants`() {
        val before = Instant.fromEpochMilliseconds(-86_400_000L + 1_000L)
        assertEquals(-1L, EpochDay.fromInstant(before).value)
    }

    @Test
    fun `all board errors carry their payload`() {
        assertEquals(ColumnId(4L), BoardError.ColumnNotFound(ColumnId(4L)).id)
        assertEquals(TaskId(2L), BoardError.NotArchivable(TaskId(2L)).id)
        assertEquals("x", BoardError.InvalidTitle("x").title)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*BoardModelTest*'
```

Expected: 编译失败——columnKindAt、EpochDay、BoardError 未定义。

- [ ] **Step 3: 实现**

templates/daily-board-base/gradle/libs.versions.toml 在 [versions] 加 coroutines = "1.11.0"，在 [libraries] 加：

```toml
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
```

templates/daily-board-base/shared/task-board/build.gradle.kts 的 sourceSets 改为：

```kotlin
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        named("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
```

templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/BoardModel.kt:

```kotlin
package __PACKAGE_NAME__.taskboard

import kotlin.time.Instant

@JvmInline
value class ColumnId(val value: Long)

@JvmInline
value class TaskId(val value: Long)

@JvmInline
value class EpochDay(val value: Long) {
    val epochMillis: Long get() = value * 86_400_000L

    companion object {
        fun fromInstant(instant: Instant): EpochDay {
            val millis = instant.toEpochMilliseconds()
            val day = millis / 86_400_000L
            val remainder = millis % 86_400_000L
            return EpochDay(if (remainder < 0) day - 1 else day)
        }
    }
}

enum class ColumnKind { START, MIDDLE, END }

data class Column(
    val id: ColumnId,
    val name: String,
    val kind: ColumnKind,
    val rank: Int,
)

data class TaskItem(
    val id: TaskId,
    val columnId: ColumnId,
    val title: String,
    val notes: String?,
    val dueDate: EpochDay?,
    val rank: Int,
    val createdAt: Instant,
    val archivedAt: Instant?,
)

data class BoardSnapshot(
    val columns: List<Column>,
    val tasks: Map<ColumnId, List<TaskItem>>,
    val archivedTasks: List<TaskItem>,
)

const val MAX_COLUMN_NAME_LENGTH = 80
const val MAX_TITLE_LENGTH = 500
const val MAX_NOTES_LENGTH = 2000
const val START_COLUMN_NAME = "开始"
const val END_COLUMN_NAME = "结束"

fun columnKindAt(index: Int, size: Int): ColumnKind = when {
    size <= 1 -> ColumnKind.START
    index == 0 -> ColumnKind.START
    index == size - 1 -> ColumnKind.END
    else -> ColumnKind.MIDDLE
}
```

templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/BoardError.kt:

```kotlin
package __PACKAGE_NAME__.taskboard

sealed class BoardError(message: String) : RuntimeException(message) {
    data class InvalidTitle(val title: String) : BoardError("Invalid task title: $title")
    data class InvalidColumnName(val name: String) : BoardError("Invalid column name: $name")
    data class InvalidNotes(val notes: String) : BoardError("Invalid notes")
    data class NotUpdatable(val id: TaskId) : BoardError("Task ${id.value} is archived and cannot be updated")
    data class ColumnNotFound(val id: ColumnId) : BoardError("Column not found: ${id.value}")
    data class TaskNotFound(val id: TaskId) : BoardError("Task not found: ${id.value}")
    data class NotArchivable(val id: TaskId) : BoardError("Task ${id.value} is not in the end column")
    data class NotRestorable(val id: TaskId) : BoardError("Task ${id.value} is not archived")
    data class ColumnNotDeletable(val id: ColumnId) : BoardError("Start/end column cannot be deleted: ${id.value}")
    data class ColumnNotMovable(val id: ColumnId) : BoardError("Start/end column cannot be moved: ${id.value}")
    data class IndexOutOfRange(val index: Int, val size: Int) : BoardError("Index $index out of range for size $size")
    data class NotOpen : BoardError("Store is not open")
}

class StorageError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
```

- [ ] **Step 4: 跑测试确认通过**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*BoardModelTest*'
```

Expected: 5 tests PASS。

- [ ] **Step 5: 提交**

```bash
git add templates/daily-board-base/gradle/libs.versions.toml templates/daily-board-base/shared/task-board
git rm -q templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoard.kt templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardTest.kt templates/daily-board-base/shared/task-board/src/iosMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardIosAdapter.kt 2>/dev/null || true
git add products/daily-board
git diff --check
git commit -m "feat: define task-board domain model and error hierarchy"
```

### Task 2: 端口、Store 骨架（open/自愈/observe/snapshot）

**Files:**
- Create: templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardRepository.kt
- Create: templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardStore.kt
- Create: templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/RecordingRepository.kt
- Create: templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/PersistedRepository.kt
- Create: templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/StoreOpenTest.kt

**Interfaces:**
- Consumes: Task 1 全部类型。
- Produces: `interface TaskBoardRepository { suspend fun open(): PersistedBoard; suspend fun apply(deltas: List<BoardDelta>): Unit }`、`PersistedBoard(columns, tasks, nextColumnId, nextTaskId)`、`sealed BoardDelta{UpsertColumn,DeleteColumn,UpsertTask,DeleteTask}`、`TaskBoardStore(repository)` + `open()`/`snapshot()`/`observe(): StateFlow<BoardSnapshot?>`。Task 3+ 依赖这些签名。

- [ ] **Step 1: 写失败测试**

StoreOpenTest.kt（含 6 个用例，内容见 Step 3 的实现文件同列——先写测试再写实现）：

```kotlin
package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class StoreOpenTest {
    @Test
    fun `open on an empty repository creates start and end columns`() = runTest {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)

        store.open()

        val snapshot = store.snapshot()
        assertEquals(2, snapshot.columns.size)
        assertEquals(ColumnKind.START, snapshot.columns.first().kind)
        assertEquals(ColumnKind.END, snapshot.columns.last().kind)
        assertEquals("开始", snapshot.columns.first().name)
        assertEquals("结束", snapshot.columns.last().name)
        assertEquals(2, repository.appliedBatches.single().size)
    }

    @Test
    fun `snapshot before open fails with NotOpen`() = runTest {
        val store = TaskBoardStore(RecordingRepository())
        assertFailsWith<BoardError.NotOpen> { store.snapshot() }
    }

    @Test
    fun `observe is null before open and emits after open`() = runTest {
        val store = TaskBoardStore(RecordingRepository())
        assertNull(store.observe().value)
        store.open()
        assertEquals(2, store.observe().value?.columns?.size)
    }

    @Test
    fun `open is idempotent`() = runTest {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)
        store.open()
        store.open()
        assertEquals(1, repository.appliedBatches.size)
    }

    @Test
    fun `open loads persisted rows and derives column kinds from rank order`() = runTest {
        val repository = PersistedRepository(
            columns = listOf(
                Column(ColumnId(3L), "Done", ColumnKind.START, 2),
                Column(ColumnId(1L), "Todo", ColumnKind.END, 1),
            ),
            tasks = emptyList(),
            nextColumnId = 4L,
            nextTaskId = 1L,
        )
        val store = TaskBoardStore(repository)
        store.open()
        val snapshot = store.snapshot()
        assertEquals(listOf(1L, 3L), snapshot.columns.map { it.id.value })
        assertEquals(ColumnKind.START, snapshot.columns.first().kind)
        assertEquals(ColumnKind.END, snapshot.columns.last().kind)
    }

    @Test
    fun `open rejects orphan tasks`() = runTest {
        val repository = PersistedRepository(
            columns = listOf(
                Column(ColumnId(1L), "Todo", ColumnKind.START, 0),
                Column(ColumnId(2L), "Done", ColumnKind.END, 1),
            ),
            tasks = listOf(
                TaskItem(TaskId(1L), ColumnId(99L), "lost", null, null, 0, Instant.fromEpochMilliseconds(0L), null),
            ),
            nextColumnId = 3L,
            nextTaskId = 2L,
        )
        val store = TaskBoardStore(repository)
        val error = assertFailsWith<StorageError> { store.open() }
        assertTrue(error.message!!.contains("Orphan task"))
        assertNull(store.observe().value)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*StoreOpenTest*'
```

Expected: 编译失败——TaskBoardStore、TaskBoardRepository、RecordingRepository、PersistedRepository 未定义。

- [ ] **Step 3: 实现**

TaskBoardRepository.kt:

```kotlin
package __PACKAGE_NAME__.taskboard

interface TaskBoardRepository {
    suspend fun open(): PersistedBoard
    suspend fun apply(deltas: List<BoardDelta>): Unit
}

data class PersistedBoard(
    val columns: List<Column>,
    val tasks: List<TaskItem>,
    val nextColumnId: Long,
    val nextTaskId: Long,
)

sealed interface BoardDelta {
    data class UpsertColumn(val column: Column) : BoardDelta
    data class DeleteColumn(val id: ColumnId) : BoardDelta
    data class UpsertTask(val task: TaskItem) : BoardDelta
    data class DeleteTask(val id: TaskId) : BoardDelta
}
```

TaskBoardStore.kt（本任务先只含 open/snapshot/observe 与私有辅助；命令在 Task 3+ 增量加入）：

```kotlin
package __PACKAGE_NAME__.taskboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskBoardStore(private val repository: TaskBoardRepository) {
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow<BoardSnapshot?>(null)
    private var open = false
    private var nextColumnId = 1L
    private var nextTaskId = 1L
    private var columns = mutableListOf<Column>()
    private var tasks = mutableListOf<TaskItem>()

    fun observe(): StateFlow<BoardSnapshot?> = _snapshot

    suspend fun open() = mutex.withLock {
        if (open) return@withLock
        val persisted = repository.open()
        nextColumnId = persisted.nextColumnId
        nextTaskId = persisted.nextTaskId
        columns = persisted.columns
            .sortedBy { it.rank }
            .mapIndexed { index, column -> column.copy(kind = columnKindAt(index, persisted.columns.size)) }
            .toMutableList()
        if (columns.isEmpty()) {
            val start = Column(ColumnId(nextColumnId++), START_COLUMN_NAME, ColumnKind.START, 0)
            val end = Column(ColumnId(nextColumnId++), END_COLUMN_NAME, ColumnKind.END, 1)
            repository.apply(listOf(BoardDelta.UpsertColumn(start), BoardDelta.UpsertColumn(end)))
            columns.addAll(listOf(start, end))
        }
        val columnIds = columns.map { it.id }.toSet()
        val orphan = persisted.tasks.firstOrNull { it.archivedAt == null && it.columnId !in columnIds }
        if (orphan != null) throw StorageError("Orphan task ${orphan.id.value} references a missing column")
        tasks = persisted.tasks.toMutableList()
        open = true
        publish()
    }

    suspend fun snapshot(): BoardSnapshot = mutex.withLock {
        requireOpen()
        _snapshot.value!!
    }

    private fun requireOpen() {
        if (!open) throw BoardError.NotOpen()
    }

    private fun publish(): BoardSnapshot = buildSnapshot().also { _snapshot.value = it }

    private fun buildSnapshot(): BoardSnapshot {
        val active = tasks.filter { it.archivedAt == null }.groupBy { it.columnId }
        val ordered = columns.associateWith { column ->
            active[column.id].orEmpty().sortedWith(compareBy({ it.rank }, { it.id.value }))
        }.mapKeys { it.key.id }
        return BoardSnapshot(
            columns = columns.toList(),
            tasks = ordered,
            archivedTasks = tasks.filter { it.archivedAt != null }.sortedByDescending { it.archivedAt },
        )
    }
}
```

RecordingRepository.kt:

```kotlin
package __PACKAGE_NAME__.taskboard

class RecordingRepository : TaskBoardRepository {
    val appliedBatches = mutableListOf<List<BoardDelta>>()

    override suspend fun open(): PersistedBoard = PersistedBoard(emptyList(), emptyList(), 1L, 1L)

    override suspend fun apply(deltas: List<BoardDelta>) {
        appliedBatches += deltas
    }
}
```

PersistedRepository.kt:

```kotlin
package __PACKAGE_NAME__.taskboard

class PersistedRepository(
    private val columns: List<Column>,
    private val tasks: List<TaskItem>,
    private val nextColumnId: Long,
    private val nextTaskId: Long,
) : TaskBoardRepository {
    val appliedBatches = mutableListOf<List<BoardDelta>>()

    override suspend fun open(): PersistedBoard = PersistedBoard(columns, tasks, nextColumnId, nextTaskId)

    override suspend fun apply(deltas: List<BoardDelta>) {
        appliedBatches += deltas
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*StoreOpenTest*'
```

Expected: 6 tests PASS。

- [ ] **Step 5: 提交**

```bash
git add templates/daily-board-base/shared/task-board products/daily-board
git diff --check
git commit -m "feat: add task-board repository port and store skeleton"
```

### Task 3: 列命令（createColumn / renameColumn / moveColumn）

**Files:**
- Modify: templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardStore.kt
- Create: templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/ColumnCommandsTest.kt

**Interfaces:**
- Consumes: Task 2 的 Store/端口/测试替身。
- Produces: `suspend fun createColumn(name: String): BoardSnapshot`、`renameColumn(id: ColumnId, name: String): BoardSnapshot`、`moveColumn(id: ColumnId, toIndex: Int): BoardSnapshot`。toIndex 语义：移除该列后列表中的目标索引，合法区间 1..size-2。

- [ ] **Step 1: 写失败测试**

```kotlin
package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ColumnCommandsTest {
    private suspend fun openedStore(): Pair<TaskBoardStore, RecordingRepository> {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)
        store.open()
        return store to repository
    }

    @Test
    fun `createColumn inserts before the end column`() = runTest {
        val (store, _) = openedStore()
        store.createColumn("Doing")
        val snapshot = store.snapshot()
        assertEquals(listOf("开始", "Doing", "结束"), snapshot.columns.map { it.name })
        assertEquals(ColumnKind.MIDDLE, snapshot.columns[1].kind)
    }

    @Test
    fun `createColumn keeps ranks dense`() = runTest {
        val (store, _) = openedStore()
        store.createColumn("A")
        store.createColumn("B")
        assertEquals(listOf(0, 1, 2, 3), store.snapshot().columns.map { it.rank })
    }

    @Test
    fun `createColumn rejects blank and overlong names without writing`() = runTest {
        val (store, repository) = openedStore()
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.InvalidColumnName> { store.createColumn("   ") }
        assertFailsWith<BoardError.InvalidColumnName> { store.createColumn("x".repeat(MAX_COLUMN_NAME_LENGTH + 1)) }
        assertEquals(baseline, repository.appliedBatches.size)
    }

    @Test
    fun `renameColumn works on fixed columns and trims`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.renameColumn(startId, "  Inbox  ")
        assertEquals("Inbox", store.snapshot().columns.first().name)
    }

    @Test
    fun `renameColumn fails for unknown columns without writing`() = runTest {
        val (store, repository) = openedStore()
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.ColumnNotFound> { store.renameColumn(ColumnId(999L), "X") }
        assertEquals(baseline, repository.appliedBatches.size)
    }

    @Test
    fun `moveColumn reorders middle columns and renumbers ranks`() = runTest {
        val (store, _) = openedStore()
        store.createColumn("A")
        val b = store.createColumn("B").columns[2].id
        store.moveColumn(b, 1)
        val snapshot = store.snapshot()
        assertEquals(listOf("开始", "B", "A", "结束"), snapshot.columns.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), snapshot.columns.map { it.rank })
    }

    @Test
    fun `start and end columns cannot move`() = runTest {
        val (store, _) = openedStore()
        store.createColumn("A")
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        assertFailsWith<BoardError.ColumnNotMovable> { store.moveColumn(startId, 1) }
        assertFailsWith<BoardError.ColumnNotMovable> { store.moveColumn(endId, 1) }
    }

    @Test
    fun `moveColumn rejects out-of-range targets`() = runTest {
        val (store, _) = openedStore()
        val a = store.createColumn("A").columns[1].id
        assertFailsWith<BoardError.IndexOutOfRange> { store.moveColumn(a, 0) }
        assertFailsWith<BoardError.IndexOutOfRange> { store.moveColumn(a, 2) }
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*ColumnCommandsTest*'
```

Expected: 编译失败——createColumn/renameColumn/moveColumn 未定义。

- [ ] **Step 3: 实现（TaskBoardStore 增量）**

```kotlin
    suspend fun createColumn(name: String): BoardSnapshot = mutex.withLock {
        requireOpen()
        val normalized = validateColumnName(name)
        val insertionIndex = columns.size - 1
        val column = Column(ColumnId(nextColumnId++), normalized, ColumnKind.MIDDLE, rank = insertionIndex)
        val newColumns = renumberColumns(columns.toMutableList().apply { add(insertionIndex, column) })
        repository.apply(columnDeltas(columns, newColumns))
        columns = newColumns.toMutableList()
        publish()
    }

    suspend fun renameColumn(id: ColumnId, name: String): BoardSnapshot = mutex.withLock {
        requireOpen()
        val normalized = validateColumnName(name)
        val index = columns.indexOfFirst { it.id == id }
        if (index < 0) throw BoardError.ColumnNotFound(id)
        val updated = columns[index].copy(name = normalized)
        repository.apply(listOf(BoardDelta.UpsertColumn(updated)))
        columns[index] = updated
        publish()
    }

    suspend fun moveColumn(id: ColumnId, toIndex: Int): BoardSnapshot = mutex.withLock {
        requireOpen()
        val fromIndex = columns.indexOfFirst { it.id == id }
        if (fromIndex < 0) throw BoardError.ColumnNotFound(id)
        if (columns[fromIndex].kind != ColumnKind.MIDDLE) throw BoardError.ColumnNotMovable(id)
        if (toIndex < 1 || toIndex > columns.size - 2) throw BoardError.IndexOutOfRange(toIndex, columns.size)
        val reordered = columns.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        val newColumns = renumberColumns(reordered)
        repository.apply(columnDeltas(columns, newColumns))
        columns = newColumns.toMutableList()
        publish()
    }

    private fun validateColumnName(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty() || name.length > MAX_COLUMN_NAME_LENGTH) throw BoardError.InvalidColumnName(raw)
        return name
    }

    private fun renumberColumns(list: List<Column>): List<Column> =
        list.mapIndexed { index, column -> column.copy(rank = index, kind = columnKindAt(index, list.size)) }

    private fun columnDeltas(old: List<Column>, new: List<Column>): List<BoardDelta> =
        new.filterNot { column -> old.contains(column) }.map { BoardDelta.UpsertColumn(it) }
```

- [ ] **Step 4: 跑测试确认通过**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*ColumnCommandsTest*'
```

Expected: 8 tests PASS。

- [ ] **Step 5: 提交**

```bash
git add templates/daily-board-base/shared/task-board products/daily-board
git diff --check
git commit -m "feat: add create, rename and move column commands"
```

### Task 4: deleteColumn（MIDDLE 专属 + 任务归档级联）

**Files:**
- Modify: templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardStore.kt
- Create: templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/DeleteColumnTest.kt

**Interfaces:**
- Consumes: Task 3 的列命令；Task 2 的测试替身。
- Produces: `suspend fun deleteColumn(id: ColumnId): BoardSnapshot`。

- [ ] **Step 1: 写失败测试**

```kotlin
package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class DeleteColumnTest {
    private suspend fun openedStore(): Pair<TaskBoardStore, RecordingRepository> {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)
        store.open()
        return store to repository
    }

    @Test
    fun `deleteColumn archives its tasks and removes the column`() = runTest {
        val (store, _) = openedStore()
        val doing = store.createColumn("Doing").columns[1].id
        store.createTask(doing, "A")
        store.createTask(doing, "B")

        store.deleteColumn(doing)

        val snapshot = store.snapshot()
        assertEquals(listOf("开始", "结束"), snapshot.columns.map { it.name })
        assertEquals(emptyList(), snapshot.tasks.values.flatten())
        assertEquals(listOf("A", "B"), snapshot.archivedTasks.sortedBy { it.createdAt }.map { it.title })
        assertNotNull(snapshot.archivedTasks.first().archivedAt)
    }

    @Test
    fun `deleteColumn emits delete and archive deltas in one batch`() = runTest {
        val (store, repository) = openedStore()
        val doing = store.createColumn("Doing").columns[1].id
        store.createTask(doing, "A")

        val baseline = repository.appliedBatches.size
        store.deleteColumn(doing)

        val batch = repository.appliedBatches.drop(baseline).single()
        assertEquals(1, batch.filterIsInstance<BoardDelta.DeleteColumn>().size)
        assertEquals(1, batch.filterIsInstance<BoardDelta.UpsertTask>().size)
    }

    @Test
    fun `start and end columns cannot be deleted`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        assertFailsWith<BoardError.ColumnNotDeletable> { store.deleteColumn(startId) }
        assertFailsWith<BoardError.ColumnNotDeletable> { store.deleteColumn(endId) }
    }

    @Test
    fun `deleting the only middle column leaves ranks dense`() = runTest {
        val (store, _) = openedStore()
        val a = store.createColumn("A").columns[1].id
        store.createColumn("B")
        store.deleteColumn(a)
        val snapshot = store.snapshot()
        assertEquals(listOf(0, 1, 2), snapshot.columns.map { it.rank })
        assertEquals(ColumnKind.MIDDLE, snapshot.columns[1].kind)
    }

    @Test
    fun `archived tasks survive without their column`() = runTest {
        val (store, _) = openedStore()
        val doing = store.createColumn("Doing").columns[1].id
        store.createTask(doing, "A")
        store.deleteColumn(doing)
        // 恢复应回到 START 列（Task 7 的命令，此处仅断言归档任务存在且无活动列引用）
        val snapshot = store.snapshot()
        assertNull(snapshot.tasks.values.flatten().firstOrNull { it.title == "A" })
        assertEquals("A", snapshot.archivedTasks.single().title)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*DeleteColumnTest*'
```

Expected: 编译失败——deleteColumn 未定义（createTask 也缺失，Task 5 才加；本任务先实现 createTask 的最小可用形态以满足测试——见 Step 3 说明）。

注意：测试用到 `createTask`，而 Task 5 才正式交付 createTask。为保持任务独立可测，本任务 Step 3 一并实现 createTask 的完整版（Task 5 只补 updateTask 与 createTask 的补充测试）。这符合「任务的交付物可独立被评审」：deleteColumn 的级联语义天然需要任务创建入口。

- [ ] **Step 3: 实现（TaskBoardStore 增量：deleteColumn + createTask + validateTitle/validateNotes）**

```kotlin
    suspend fun deleteColumn(id: ColumnId): BoardSnapshot = mutex.withLock {
        requireOpen()
        val index = columns.indexOfFirst { it.id == id }
        if (index < 0) throw BoardError.ColumnNotFound(id)
        if (columns[index].kind != ColumnKind.MIDDLE) throw BoardError.ColumnNotDeletable(id)
        val now = Instant.now()
        val archived = tasks.filter { it.columnId == id }.map { it.copy(archivedAt = now, rank = 0) }
        val newColumns = renumberColumns(columns.filterNot { it.id == id })
        val newTasks = tasks.filterNot { it.columnId == id } + archived
        // 归档 UpsertTask 先于 DeleteColumn：SQLite 即时 FK 约束要求引用行先置 NULL
        repository.apply(
            archived.map { BoardDelta.UpsertTask(it) } +
                listOf(BoardDelta.DeleteColumn(id)) +
                columnDeltas(columns, newColumns),
        )
        columns = newColumns.toMutableList()
        tasks = newTasks.toMutableList()
        publish()
    }

    suspend fun createTask(
        columnId: ColumnId,
        title: String,
        notes: String? = null,
        dueDate: EpochDay? = null,
    ): BoardSnapshot = mutex.withLock {
        requireOpen()
        val normalizedTitle = validateTitle(title)
        val normalizedNotes = validateNotes(notes)
        if (columns.none { it.id == columnId }) throw BoardError.ColumnNotFound(columnId)
        val rank = tasks.count { it.archivedAt == null && it.columnId == columnId }
        val task = TaskItem(
            id = TaskId(nextTaskId++),
            columnId = columnId,
            title = normalizedTitle,
            notes = normalizedNotes,
            dueDate = dueDate,
            rank = rank,
            createdAt = Instant.now(),
            archivedAt = null,
        )
        repository.apply(listOf(BoardDelta.UpsertTask(task)))
        tasks.add(task)
        publish()
    }

    private fun validateTitle(raw: String): String {
        val title = raw.trim()
        if (title.isEmpty() || title.length > MAX_TITLE_LENGTH) throw BoardError.InvalidTitle(raw)
        return title
    }

    private fun validateNotes(raw: String?): String? {
        if (raw == null) return null
        val notes = raw.trim()
        if (notes.length > MAX_NOTES_LENGTH) throw BoardError.InvalidNotes(raw)
        return notes.ifEmpty { null }
    }
```

TaskBoardStore 顶部 import 增补 `import kotlin.time.Instant`。

- [ ] **Step 4: 跑测试确认通过**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*DeleteColumnTest*'
```

Expected: 5 tests PASS。

- [ ] **Step 5: 提交**

```bash
git add templates/daily-board-base/shared/task-board products/daily-board
git diff --check
git commit -m "feat: delete middle columns and archive their tasks"
```

### Task 5: 任务命令（createTask / updateTask）

**Files:**
- Modify: templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardStore.kt
- Create: templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/TaskCommandsTest.kt

**Interfaces:**
- Consumes: Task 4 已实现 createTask（本任务补其测试与 updateTask）。
- Produces: `suspend fun updateTask(id, title? = null, notes? = null, dueDate? = null, clearDueDate = false): BoardSnapshot`。null = 不变；notes 传 "" = 清空；clearDueDate=true = 清空截止日期；全 null 且 clearDueDate=false = no-op（零 delta）。

- [ ] **Step 1: 写失败测试**

```kotlin
package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class TaskCommandsTest {
    private suspend fun openedStore(): Pair<TaskBoardStore, RecordingRepository> {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)
        store.open()
        return store to repository
    }

    @Test
    fun `createTask appends at the end of its column with a dense rank`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.createTask(startId, "  A  ")
        store.createTask(startId, "B")
        val snapshot = store.snapshot()
        assertEquals(listOf("A", "B"), snapshot.tasks.getValue(startId).map { it.title })
        assertEquals(listOf(0, 1), snapshot.tasks.getValue(startId).map { it.rank })
    }

    @Test
    fun `createTask normalizes notes and stores due dates`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val day = EpochDay(20_000L)
        store.createTask(startId, "A", notes = "  note  ", dueDate = day)
        val task = store.snapshot().tasks.getValue(startId).single()
        assertEquals("note", task.notes)
        assertEquals(day, task.dueDate)
        store.createTask(startId, "B", notes = "   ")
        assertNull(store.snapshot().tasks.getValue(startId).last().notes)
    }

    @Test
    fun `createTask rejects invalid input without writing`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.InvalidTitle> { store.createTask(startId, "  ") }
        assertFailsWith<BoardError.InvalidTitle> { store.createTask(startId, "x".repeat(MAX_TITLE_LENGTH + 1)) }
        assertFailsWith<BoardError.InvalidNotes> { store.createTask(startId, "A", notes = "x".repeat(MAX_NOTES_LENGTH + 1)) }
        assertFailsWith<BoardError.ColumnNotFound> { store.createTask(ColumnId(999L), "A") }
        assertEquals(baseline, repository.appliedBatches.size)
    }

    @Test
    fun `updateTask changes only provided fields`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val id = store.createTask(startId, "A", notes = "n", dueDate = EpochDay(10L)).tasks.getValue(startId).single().id
        store.updateTask(id, title = "A2")
        var task = store.snapshot().tasks.getValue(startId).single()
        assertEquals("A2", task.title)
        assertEquals("n", task.notes)
        assertEquals(EpochDay(10L), task.dueDate)
        store.updateTask(id, notes = "")
        task = store.snapshot().tasks.getValue(startId).single()
        assertNull(task.notes)
    }

    @Test
    fun `updateTask clears due date only when asked`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val id = store.createTask(startId, "A", dueDate = EpochDay(10L)).tasks.getValue(startId).single().id
        store.updateTask(id, title = "A1")
        assertEquals(EpochDay(10L), store.snapshot().tasks.getValue(startId).single().dueDate)
        store.updateTask(id, clearDueDate = true)
        assertNull(store.snapshot().tasks.getValue(startId).single().dueDate)
        store.updateTask(id, dueDate = EpochDay(11L))
        assertEquals(EpochDay(11L), store.snapshot().tasks.getValue(startId).single().dueDate)
    }

    @Test
    fun `updateTask with no changes is a no-op without writing`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.createTask(startId, "A")
        val baseline = repository.appliedBatches.size
        store.updateTask(store.snapshot().tasks.getValue(startId).single().id)
        assertEquals(baseline, repository.appliedBatches.size)
    }

    @Test
    fun `updateTask fails for unknown and archived tasks`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.createTask(startId, "A")
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.TaskNotFound> { store.updateTask(TaskId(999L), title = "X") }
        assertEquals(baseline, repository.appliedBatches.size)
        val archived = store.snapshot().tasks.getValue(startId).single().copy(archivedAt = Instant.now())
        val snapshot = store.snapshot()
        val archivedStore = TaskBoardStore(
            PersistedRepository(
                columns = snapshot.columns,
                tasks = listOf(archived),
                nextColumnId = 2L,
                nextTaskId = 2L,
            ),
        )
        archivedStore.open()
        assertFailsWith<BoardError.NotUpdatable> { archivedStore.updateTask(archived.id, title = "X") }
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*TaskCommandsTest*'
```

Expected: 编译失败——updateTask 未定义。

- [ ] **Step 3: 实现（TaskBoardStore 增量：updateTask）**

```kotlin
    suspend fun updateTask(
        id: TaskId,
        title: String? = null,
        notes: String? = null,
        dueDate: EpochDay? = null,
        clearDueDate: Boolean = false,
    ): BoardSnapshot = mutex.withLock {
        requireOpen()
        val index = tasks.indexOfFirst { it.id == id }
        if (index < 0) throw BoardError.TaskNotFound(id)
        val current = tasks[index]
        if (current.archivedAt != null) throw BoardError.NotUpdatable(id)
        if (title == null && notes == null && dueDate == null && !clearDueDate) return@withLock publish()
        val newTitle = title?.let { validateTitle(it) } ?: current.title
        val newNotes = notes?.let { validateNotes(it) } ?: current.notes
        val newDueDate = if (clearDueDate) null else dueDate ?: current.dueDate
        val updated = current.copy(title = newTitle, notes = newNotes, dueDate = newDueDate)
        repository.apply(listOf(BoardDelta.UpsertTask(updated)))
        tasks[index] = updated
        publish()
    }
```

- [ ] **Step 4: 跑测试确认通过**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*TaskCommandsTest*'
```

Expected: 7 tests PASS。

- [ ] **Step 5: 提交**

```bash
git add templates/daily-board-base/shared/task-board products/daily-board
git diff --check
git commit -m "feat: add createTask and updateTask commands"
```

### Task 6: moveTask（跨列/列内移动 + END 完成语义 + rank 重排）

**Files:**
- Modify: templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardStore.kt
- Create: templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/MoveTaskTest.kt

**Interfaces:**
- Produces: `suspend fun moveTask(id: TaskId, toColumnId: ColumnId, toIndex: Int): BoardSnapshot`。toIndex = 任务移除后目标列列表中的目标索引（0..目标列任务数，等于任务数 = 追加末尾）；同列同位置 = no-op。

- [ ] **Step 1: 写失败测试**

```kotlin
package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class MoveTaskTest {
    private suspend fun openedStore(): Pair<TaskBoardStore, RecordingRepository> {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)
        store.open()
        return store to repository
    }

    @Test
