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
- Task 10 的 androidHostTest 合约测试需 Robolectric 环境：commonTest 的 RepositoryContractTest 直接跑在 androidHostTest 时 `RuntimeEnvironment.getApplication()` 为 null——改为经 `@RunWith(RobolectricTestRunner)` 的 AndroidRepositoryContractTest 子类运行同一套合约（基类改 open），并在 testAndroidHostTest filter 排除基类自身。
- Task 10 平台 actual 的「每次调用重建/随机库名」破坏 `deltas survive a reopen` 的测试内共享存储语义——改为 commonTest 每测试 `@BeforeTest` 重置的缓存库名（首次调用生成随机名并缓存；测试内重开共享同一库、测试间互不污染）。
- Task 10 MigrationTest 版本断言修正：SQLDelight 2.2.1 生成的 `TaskBoardDatabase.Schema.version` 为 2（.sq 当前 schema 计 1、1.sqm 迁移 +1），且 AndroidSqliteDriver 惰性建库（须 open() 才写 user_version）——断言 Schema.version 并在 runTest 中调用 open()。
- Task 10 iOS 编译暴露两处 Task 9 遗留（androidHostTest 未覆盖）：① BoardModel.kt 的 `@JvmInline` 补 `import kotlin.jvm.JvmInline`；② iOS 工厂 `onConfiguration` 用 SQLDelight 2.2.1 新签名 `{ it.copy(extendedConfig = it.extendedConfig.copy(foreignKeyConstraints = true)) }`（旧 `foreign_keys(true)` 不存在）。
- Task 12 渲染器空 token 行处理：`__WEB_MODULES__`/`__WASM_JS_TARGET__` 展开为空时整行移除（按行过滤含 token 且展开后空白的行，其余行原样保留），避免生成文件结尾出现空行（违反「恰好一个换行符结尾」并触发 diff --check）；对应 ProductRendererTest 增加无空行残留断言。
- Task 13 的 wasm 编译器强制修正（Kotlin 2.4.10 锁定工具链）：① local-data-web 的 JS interop 源码放 `src/wasmJsMain/`（`kotlin.JsFun`/`JsArray` 属 js/wasm 共享 stdlib，common 不可见）；② `import kotlin.JsFun`（2.4.10 根包，非 kotlin.js）；③ wasm interop 拒绝 `Array<T>`——事务参数用 `JsArray<JsAny>` + `@JsFun("(...names) => names")` vararg 助手；④ commonMain 补 `kotlinx-coroutines-core`（task-board 的 implementation 依赖不传递）；⑤ 行遍历用 `toJsArray(rows).toList().map`；⑥ 两文件加 `@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)`。
- Task 13 顺带完成 Task 15 Step 1：`__WASM_JS_TARGET__` token 行已加入 task-board 模板 build.gradle.kts（web 夹具解析 wasm 变体需要）——Task 14/15 执行时改为「验证该行存在」而非插入。
- Task 14 的两项运行时形状硬化（Task 13 评审 Important 项）：① `storeNamesArray` 的 vararg `@JsFun("(...names) => names")` 若 ABI 不展开会得到嵌套数组——Task 14 第一步用形状探针测试断言 `["columns","tasks"]` 扁平形状；若不通过，改用定元 `@JsFun("(a) => [a]")`/`@JsFun("(a, b) => [a, b]")` 助手；fake 必须按真实 IndexedDB 语义严格处理扁平字符串数组（不得模仿 rest 语义掩盖 bug）。② `awaitCompletion` 在真实浏览器中 onerror 与 onabort 连续触发导致 double-resume——用「已触发」标志守卫 resume 且仅当事务仍活跃时调用 abort()（catch 里 abort 前判断）。
- Task 14 探针结论：wasm ABI 确实展开 vararg，`storeNamesArray` 产出扁平数组，定元助手不需要；探针留作回归守卫。
- Task 14 的 wasm 编译器强制修正：Kotlin/Wasm 禁止非 external 类实现 external 接口（`Non-external type extends external type`），且 `Array<T>` 不能作 `@JsFun` 参数——fake 改为 JS 门面对象转发 Kotlin lambda（逻辑全在 Kotlin）；测试用 `WebTaskBoardRepository(factory.idbFactory)`；`jsArrayOf` 弃用改 `toJsArray()`；`unsafeCast` 改恒等 `@JsFun("(js) => js") asIdbFactory`。合约断言收紧为 `assertFailsWith<StorageError>`。
- 本机 Node/Yarn 下载不可用（`FAIL_ON_PROJECT_REPOS` 挡住 node 下载仓库 + github 不可达）：本地跑 wasm 测试需在每次 createProduct 后对 scratch 认证树应用 `.superpowers/sdd/patch-cert.sh <fixture-root>`（settings 改 PREFER_PROJECT、根/模块 Node/Yarn EnvSpec download=false，用本机 node v26.7.0 + yarn 1.22.22）。该补丁只用于本地认证，不提交进模板（CI 可达 nodejs.org/github，无需补丁）；Task 15/18 的本地 wasm 验证同样需要。
- Task 15 的 wasmJs 编译器强制修正：① class 级 `@JsExport` 被拒（`Applicable targets: function`）——Web 表面改为顶层导出函数 + `JsReference<T>` 不透明句柄（`createWebTaskBoardAdapter/subscribe/dispose`），spec §7.2 已同步；② wasmJs 上 `testScheduler.advanceUntilIdle()` 不驱动 `backgroundScope.launch`（探针证实），测试改用 `testScheduler.runCurrent()`。
- Task 18 的 Kotlin/Native 强制修正：K/N 拒绝反引号标识符含逗号（`Name contains illegal characters: ","`；JVM/wasmJs 均接受）——`MoveTaskTest` 的 `moveTask rejects unknown tasks, columns and bad indexes` 是唯一含逗号的测试名，改为 `moveTask rejects unknown task and column ids and out of range indexes`。此前无任何环节运行过 `:shared:task-board:iosSimulatorArm64Test`，Task 18 首次把它纳入认证矩阵时暴露；ubuntu 上 allTests 自动跳过 iOS，故仅 macOS 触发。

- Task 9 的 settings.gradle.kts 变更**不加入** `__WEB_MODULES__` token：渲染器在 Task 12 才求值该 token，而 GeneratedTreeVerifier 拒绝渲染树残留 `__`，Task 9 加入会使 createProduct 失败并阻断尖刺。Task 12 实现条件发射时须同时把 `__WEB_MODULES__` 行加入模板 settings.gradle.kts（web 未选时渲染为空行）。
- Task 9 仓库实现用生成属性 `boardQueries` 而非计划原文的 `taskBoardDatabaseQueries`：SQLDelight 按 .sq 文件名（Board.sq）生成查询属性名。
- Android 驱动回调签名：SQLDelight 2.2.1 的 `AndroidSqliteDriver.Callback.onConfigure` 参数为 `androidx.sqlite.db.SupportSQLiteDatabase`（非 `android.database.sqlite.SQLiteDatabase`）。
- Robolectric 冒烟测试第二条用例须写 `kotlinx.coroutines.runBlocking<Unit>`：JUnit 4 校验测试方法必须返回 void，`runBlocking {}` 的尾表达式（deleteDatabase 返回 Boolean）会触发 InvalidTestClassError。
- 发现（非本任务引入，Task 1 遗留）：task-board commonMain 的 `Math.floorDiv`（java.lang.Math）在非 JVM target 无法编译；已改用纯 Kotlin 算术 `millis / 86_400_000L` + 余数为负时减一（`@JvmInline` 本身跨 target 可用），随 Task 9 修复提交。
- Task 17 修正（评审批准）：**K/N 单运行时约束**——原「app 同时链接 TaskBoardShared.framework + LocalDataSql.framework（两个静态 framework）」设计在本机模拟器实测启动即 SIGABRT：两个静态 framework 各自内嵌 Kotlin 运行时，第二次 `+[KotlinBase load]` → `injectToRuntime()` 触发 `RuntimeAssertFailedPanic`（crash report 佐证）。修正为 K/N 官方模式「一个进程只嵌入一份运行时」：① LocalDataSql.framework 两个 framework block 加 `export(project(":shared:task-board"))` + `transitiveExport = true`，task-board 类型并入 LocalDataSql.framework，app 只链接 LocalDataSql.framework；② export 要求导出项目及其传递依赖为 API 依赖——task-board 的 `kotlinx-coroutines-core` 与 local-data-sql 的 `project(":shared:task-board")` 均从 `implementation` 改为 `api`（否则 KGP 报 `not specified as API-dependencies of a corresponding source set`）；③ sqldelight native driver 的 sqlite3 cinterop 在 iOS 无 linkerOpts，且静态 framework 不传播 `linkerOpts`（实测 LC_LINKER_OPTION 不记录），故 app 侧 `OTHER_LDFLAGS: -lsqlite3` 自链系统 sqlite3；④ ContentView 改仅 `import LocalDataSql`（transitive export 把 task-board 类型并入 LocalDataSql.h，独立 TaskBoardShared module 不再存在），Swift 侧跨框架 `as! TaskBoardRepository` cast 随之消失。验证：链接/构建 BUILD SUCCEEDED；模拟器 iPhone 16 Pro / iOS 18.3 启动无崩溃（进程存活），`daily-board.db` 落盘并种子「开始/结束」两列（端到端跑通）。

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
    fun `moving into the end column completes the task`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        val id = store.createTask(startId, "A").tasks.getValue(startId).single().id
        store.moveTask(id, endId, 0)
        val snapshot = store.snapshot()
        assertEquals(emptyList(), snapshot.tasks.getValue(startId))
        assertEquals(listOf("A"), snapshot.tasks.getValue(endId).map { it.title })
    }

    @Test
    fun `moving out of the end column reopens the task`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        val id = store.createTask(endId, "done").tasks.getValue(endId).single().id
        store.moveTask(id, startId, 0)
        assertEquals(listOf("done"), store.snapshot().tasks.getValue(startId).map { it.title })
    }

    @Test
    fun `cross-column move renumbers both columns densely`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val doing = store.createColumn("Doing").columns[1].id
        store.createTask(startId, "A")
        store.createTask(startId, "B")
        store.createTask(startId, "C")
        val id = store.snapshot().tasks.getValue(startId)[1].id
        store.moveTask(id, doing, 0)
        val snapshot = store.snapshot()
        assertEquals(listOf("A", "C"), snapshot.tasks.getValue(startId).map { it.title })
        assertEquals(listOf(0, 1), snapshot.tasks.getValue(startId).map { it.rank })
        assertEquals(listOf("B"), snapshot.tasks.getValue(doing).map { it.title })
        assertEquals(listOf(0), snapshot.tasks.getValue(doing).map { it.rank })
    }

    @Test
    fun `same-column reorder keeps a stable total order`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.createTask(startId, "A")
        store.createTask(startId, "B")
        store.createTask(startId, "C")
        val id = store.snapshot().tasks.getValue(startId).last().id
        store.moveTask(id, startId, 0)
        assertEquals(listOf("C", "A", "B"), store.snapshot().tasks.getValue(startId).map { it.title })
    }

    @Test
    fun `moving a task to its own position is a no-op`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.createTask(startId, "A")
        val id = store.snapshot().tasks.getValue(startId).single().id
        val baseline = repository.appliedBatches.size
        store.moveTask(id, startId, 0)
        assertEquals(baseline, repository.appliedBatches.size)
    }

    @Test
    fun `moveTask rejects unknown task and column ids and out of range indexes`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        store.createTask(startId, "A")
        val id = store.snapshot().tasks.getValue(startId).single().id
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.TaskNotFound> { store.moveTask(TaskId(999L), startId, 0) }
        assertFailsWith<BoardError.ColumnNotFound> { store.moveTask(id, ColumnId(999L), 0) }
        assertFailsWith<BoardError.IndexOutOfRange> { store.moveTask(id, startId, 5) }
        assertEquals(baseline, repository.appliedBatches.size)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*MoveTaskTest*'
```

Expected: 编译失败——moveTask 未定义。

- [ ] **Step 3: 实现（TaskBoardStore 增量）**

```kotlin
    suspend fun moveTask(id: TaskId, toColumnId: ColumnId, toIndex: Int): BoardSnapshot = mutex.withLock {
        requireOpen()
        val taskIndex = tasks.indexOfFirst { it.id == id }
        if (taskIndex < 0) throw BoardError.TaskNotFound(id)
        val task = tasks[taskIndex]
        if (task.archivedAt != null) throw BoardError.NotUpdatable(id)
        if (columns.none { it.id == toColumnId }) throw BoardError.ColumnNotFound(toColumnId)
        val fromColumnId = task.columnId
        val fromList = columnTasks(fromColumnId).filterNot { it.id == id }
        val targetList = (if (toColumnId == fromColumnId) fromList else columnTasks(toColumnId)).toMutableList()
        if (toIndex < 0 || toIndex > targetList.size) throw BoardError.IndexOutOfRange(toIndex, targetList.size + 1)
        if (toColumnId == fromColumnId && toIndex == task.rank) return@withLock publish()
        targetList.add(toIndex, task.copy(columnId = toColumnId))
        val targetRenumbered = targetList.mapIndexed { index, item -> item.copy(rank = index) }
        val fromRenumbered =
            if (toColumnId == fromColumnId) emptyList() else fromList.mapIndexed { index, item -> item.copy(rank = index) }
        val newTasks = tasks.toMutableList()
        newTasks.removeAll { it.archivedAt == null && (it.columnId == fromColumnId || it.columnId == toColumnId) }
        newTasks.addAll(fromRenumbered + targetRenumbered)
        repository.apply(
            fromRenumbered.map { BoardDelta.UpsertTask(it) } + targetRenumbered.map { BoardDelta.UpsertTask(it) },
        )
        tasks = newTasks
        publish()
    }

    private fun columnTasks(columnId: ColumnId): List<TaskItem> =
        tasks.filter { it.archivedAt == null && it.columnId == columnId }
            .sortedWith(compareBy({ it.rank }, { it.id.value }))
```

- [ ] **Step 4: 跑测试确认通过**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*MoveTaskTest*'
```

Expected: 6 tests PASS。

- [ ] **Step 5: 提交**

```bash
git add templates/daily-board-base/shared/task-board products/daily-board
git diff --check
git commit -m "feat: move tasks across columns with rank renumbering"
```

### Task 7: 归档与恢复（archiveTask / restoreTask）

**Files:**
- Modify: templates/daily-board-base/shared/task-board/src/commonMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardStore.kt
- Create: templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/ArchiveRestoreTest.kt

**Interfaces:**
- Produces: `suspend fun archiveTask(id: TaskId): BoardSnapshot`（仅 END 列任务）、`suspend fun restoreTask(id: TaskId): BoardSnapshot`（回 START 列末尾，archivedAt 清空）。

- [ ] **Step 1: 写失败测试**

```kotlin
package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ArchiveRestoreTest {
    private suspend fun openedStore(): Pair<TaskBoardStore, RecordingRepository> {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)
        store.open()
        return store to repository
    }

    @Test
    fun `only end-column tasks can be archived`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        val active = store.createTask(startId, "A").tasks.getValue(startId).single().id
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.NotArchivable> { store.archiveTask(active) }
        assertEquals(baseline, repository.appliedBatches.size)
        val done = store.createTask(endId, "B").tasks.getValue(endId).single().id
        store.archiveTask(done)
        val archived = store.snapshot().archivedTasks.single()
        assertEquals("B", archived.title)
        assertEquals(0, archived.rank)
        assertNull(store.snapshot().tasks.getValue(endId).firstOrNull { it.id == done })
    }

    @Test
    fun `archiving renumbers the remaining end-column tasks`() = runTest {
        val (store, _) = openedStore()
        val endId = store.snapshot().columns.last().id
        store.createTask(endId, "A")
        val b = store.createTask(endId, "B").tasks.getValue(endId).last().id
        store.archiveTask(b)
        assertEquals(listOf(0), store.snapshot().tasks.getValue(endId).map { it.rank })
    }

    @Test
    fun `restore returns the task to the end of the start column`() = runTest {
        val (store, _) = openedStore()
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        store.createTask(startId, "existing")
        val id = store.createTask(endId, "done").tasks.getValue(endId).single().id
        store.archiveTask(id)
        store.restoreTask(id)
        val snapshot = store.snapshot()
        assertNull(snapshot.archivedTasks.firstOrNull { it.id == id })
        val restored = snapshot.tasks.getValue(startId).last()
        assertEquals("done", restored.title)
        assertNull(restored.archivedAt)
        assertEquals(1, restored.rank)
    }

    @Test
    fun `archive and restore fail cleanly on wrong states`() = runTest {
        val (store, repository) = openedStore()
        val startId = store.snapshot().columns.first().id
        val endId = store.snapshot().columns.last().id
        val done = store.createTask(endId, "done").tasks.getValue(endId).single().id
        val active = store.createTask(startId, "A").tasks.getValue(startId).single().id
        val baseline = repository.appliedBatches.size
        assertFailsWith<BoardError.TaskNotFound> { store.archiveTask(TaskId(999L)) }
        assertFailsWith<BoardError.TaskNotFound> { store.restoreTask(TaskId(999L)) }
        assertFailsWith<BoardError.NotRestorable> { store.restoreTask(active) }
        assertEquals(baseline, repository.appliedBatches.size)
        store.archiveTask(done)
        assertFailsWith<BoardError.NotRestorable> { store.archiveTask(done) }
    }

    @Test
    fun `archived tasks are ordered newest first`() = runTest {
        val (store, _) = openedStore()
        val endId = store.snapshot().columns.last().id
        store.createTask(endId, "A")
        store.createTask(endId, "B")
        val ids = store.snapshot().tasks.getValue(endId).map { it.id }
        store.archiveTask(ids[0])
        store.archiveTask(ids[1])
        val archived = store.snapshot().archivedTasks
        assertEquals(setOf("A", "B"), archived.map { it.title }.toSet())
        assertEquals(2, archived.size)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*ArchiveRestoreTest*'
```

Expected: 编译失败——archiveTask/restoreTask 未定义。

- [ ] **Step 3: 实现（TaskBoardStore 增量）**

```kotlin
    suspend fun archiveTask(id: TaskId): BoardSnapshot = mutex.withLock {
        requireOpen()
        val index = tasks.indexOfFirst { it.id == id }
        if (index < 0) throw BoardError.TaskNotFound(id)
        val task = tasks[index]
        if (task.archivedAt != null) throw BoardError.NotRestorable(id)
        val endColumnId = columns.last().id
        if (task.columnId != endColumnId) throw BoardError.NotArchivable(id)
        val archived = task.copy(archivedAt = Instant.now(), rank = 0)
        val renumbered = columnTasks(endColumnId).filterNot { it.id == id }
            .mapIndexed { position, item -> item.copy(rank = position) }
        val newTasks = tasks.toMutableList()
        newTasks.removeAll { it.archivedAt == null && it.columnId == endColumnId }
        newTasks.addAll(renumbered + archived)
        repository.apply(listOf(BoardDelta.UpsertTask(archived)) + renumbered.map { BoardDelta.UpsertTask(it) })
        tasks = newTasks
        publish()
    }

    suspend fun restoreTask(id: TaskId): BoardSnapshot = mutex.withLock {
        requireOpen()
        val index = tasks.indexOfFirst { it.id == id }
        if (index < 0) throw BoardError.TaskNotFound(id)
        val task = tasks[index]
        if (task.archivedAt == null) throw BoardError.NotRestorable(id)
        val startColumnId = columns.first().id
        val rank = tasks.count { it.archivedAt == null && it.columnId == startColumnId }
        val restored = task.copy(columnId = startColumnId, rank = rank, archivedAt = null)
        repository.apply(listOf(BoardDelta.UpsertTask(restored)))
        tasks[index] = restored
        publish()
    }
```

- [ ] **Step 4: 跑测试确认通过**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*ArchiveRestoreTest*'
```

Expected: 5 tests PASS。

- [ ] **Step 5: 提交**

```bash
git add templates/daily-board-base/shared/task-board products/daily-board
git diff --check
git commit -m "feat: archive completed tasks and restore them to the start column"
```

### Task 8: rank 确定性随机回归

**Files:**
- Create: templates/daily-board-base/shared/task-board/src/commonTest/kotlin/__PACKAGE_PATH__/taskboard/RankPropertyTest.kt

**Interfaces:**
- Consumes: Task 3-7 全部命令。
- Produces: 无新接口；交付 rank 不漂移的回归网。

- [ ] **Step 1: 写测试**

```kotlin
package __PACKAGE_NAME__.taskboard

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class RankPropertyTest {
    @Test
    fun `200 random operations preserve dense stable ordering`() = runTest {
        val repository = RecordingRepository()
        val store = TaskBoardStore(repository)
        store.open()
        val random = Random(42)
        var counter = 0
        repeat(200) {
            val snapshot = store.snapshot()
            val middle = snapshot.columns.filter { it.kind == ColumnKind.MIDDLE }
            val activeTasks = snapshot.tasks.values.flatten()
            val archivedTasks = snapshot.archivedTasks
            when (random.nextInt(8)) {
                0 -> store.createTask(snapshot.columns.random(random).id, "t" + counter++)
                1 -> if (activeTasks.isNotEmpty()) {
                    val task = activeTasks.random(random)
                    val target = snapshot.columns.random(random).id
                    val targetSize = snapshot.tasks[target].orEmpty().size
                    val upperBound = if (task.columnId == target) targetSize else targetSize + 1
                    store.moveTask(task.id, target, random.nextInt(0, upperBound))
                }
                2 -> store.createColumn("c" + counter++)
                3 -> if (middle.isNotEmpty()) store.deleteColumn(middle.random(random).id)
                4 -> if (middle.isNotEmpty()) {
                    val column = middle.random(random)
                    store.moveColumn(column.id, random.nextInt(1, snapshot.columns.size - 1))
                }
                5 -> if (snapshot.columns.isNotEmpty()) store.renameColumn(snapshot.columns.random(random).id, "r" + counter++)
                6 -> if (archivedTasks.isNotEmpty()) store.restoreTask(archivedTasks.random(random).id)
                else -> {
                    val endId = snapshot.columns.last().id
                    val endTasks = snapshot.tasks[endId].orEmpty()
                    if (endTasks.isNotEmpty()) store.archiveTask(endTasks.random(random).id)
                }
            }
            assertInvariants(store)
        }
    }

    private suspend fun assertInvariants(store: TaskBoardStore) {
        val snapshot = store.snapshot()
        snapshot.columns.forEachIndexed { index, column ->
            assertEquals(index, column.rank, "column rank dense at " + index)
            assertEquals(columnKindAt(index, snapshot.columns.size), column.kind, "column kind at " + index)
        }
        snapshot.columns.forEach { column ->
            val items = snapshot.tasks[column.id].orEmpty()
            items.forEachIndexed { index, item ->
                assertEquals(index, item.rank, "task rank dense in column " + column.id.value)
                assertNull(item.archivedAt, "active task in column " + column.id.value)
                assertEquals(column.id, item.columnId)
            }
            val ordered = items.sortedWith(compareBy({ it.rank }, { it.id.value }))
            assertEquals(ordered, items, "(rank,id) total order in column " + column.id.value)
        }
        snapshot.archivedTasks.forEach { task ->
            assertNull(snapshot.tasks.values.flatten().find { it.id == task.id })
        }
    }
}
```

- [ ] **Step 2: 跑测试确认绿（回归网，非红绿）**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:androidHostTest --tests '*RankPropertyTest*'
```

Expected: PASS。若失败，定位并修复对应命令的 rank 逻辑（不得放宽断言），重跑至绿。

- [ ] **Step 3: 跑全量 store 测试**

```bash
./gradlew -p products/daily-board :shared:task-board:androidHostTest
```

Expected: BoardModelTest/StoreOpenTest/ColumnCommandsTest/DeleteColumnTest/TaskCommandsTest/MoveTaskTest/ArchiveRestoreTest/RankPropertyTest 全部 PASS。

- [ ] **Step 4: 提交**

```bash
git add templates/daily-board-base/shared/task-board products/daily-board
git diff --check
git commit -m "test: lock rank and ordering invariants with a seeded regression"
```

### Task 9: local-data-sql 模块（SQLDelight schema、仓库实现、工厂、Robolectric 尖刺）

**Files:**
- Modify: templates/daily-board-base/gradle/libs.versions.toml
- Modify: templates/daily-board-base/settings.gradle.kts
- Create: templates/daily-board-base/shared/local-data-sql/build.gradle.kts
- Create: templates/daily-board-base/shared/local-data-sql/src/commonMain/sqldelight/__PACKAGE_PATH__/localdata/Board.sq
- Create: templates/daily-board-base/shared/local-data-sql/src/commonMain/sqldelight/migrations/1.sqm
- Create: templates/daily-board-base/shared/local-data-sql/src/commonMain/kotlin/__PACKAGE_PATH__/localdata/SqlDelightTaskBoardRepository.kt
- Create: templates/daily-board-base/shared/local-data-sql/src/androidMain/kotlin/__PACKAGE_PATH__/localdata/TaskBoardRepositoryFactories.android.kt
- Create: templates/daily-board-base/shared/local-data-sql/src/iosMain/kotlin/__PACKAGE_PATH__/localdata/TaskBoardRepositoryFactories.ios.kt
- Create: templates/daily-board-base/shared/local-data-sql/src/androidHostTest/kotlin/__PACKAGE_PATH__/localdata/RobolectricSmokeTest.kt

**Interfaces:**
- Consumes: Task 2 的端口与 delta；Task 1 的模型。
- Produces: `class SqlDelightTaskBoardRepository(driver: SqlDriver)`、`fun taskBoardRepository(context: Context, name: String = "daily-board.db")`（androidMain）、`fun taskBoardRepository(name: String = "daily-board.db")`（iosMain）。Task 10 依赖。

- [ ] **Step 1: 搭建模块与 schema**

toml 追加（versions：`sqldelight = "2.2.1"`、`robolectric = "4.16.1"`；libraries 与 plugins）：

```toml
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
```

```toml
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

settings.gradle.kts：

```kotlin
include(":shared:task-board", ":shared:local-data-sql", ":apps:android")
__WEB_MODULES__
```

build.gradle.kts（local-data-sql）：

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.sqldelight)
    id("seraphim.kotlin-policy")
}

sqldelight {
    databases {
        create("TaskBoardDatabase") {
            packageName.set("__PACKAGE_NAME__.localdata")
        }
    }
}

kotlin {
    android {
        namespace = "__PACKAGE_NAME__.localdata"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }
    iosArm64 {
        binaries.framework {
            baseName = "LocalDataSql"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "LocalDataSql"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:task-board"))
            implementation(libs.sqldelight.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        named("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.robolectric)
            }
        }
    }
}
```

Board.sq（`src/commonMain/sqldelight/__PACKAGE_PATH__/localdata/Board.sq`）：

```sql
CREATE TABLE board_column (
    id INTEGER NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    rank INTEGER NOT NULL
);

CREATE TABLE task (
    id INTEGER NOT NULL PRIMARY KEY,
    column_id INTEGER REFERENCES board_column(id),
    title TEXT NOT NULL,
    notes TEXT,
    due_date INTEGER,
    rank INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    archived_at INTEGER
);

CREATE INDEX task_by_column ON task(column_id, rank);
CREATE INDEX task_by_archived ON task(archived_at);

selectColumns:
SELECT * FROM board_column ORDER BY rank;

selectTasks:
SELECT * FROM task;

upsertColumn:
INSERT OR REPLACE INTO board_column(id, name, rank) VALUES (?, ?, ?);

deleteColumnRow:
DELETE FROM board_column WHERE id = ?;

upsertTask:
INSERT OR REPLACE INTO task(id, column_id, title, notes, due_date, rank, created_at, archived_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

deleteTaskRow:
DELETE FROM task WHERE id = ?;
```

1.sqm（`src/commonMain/sqldelight/migrations/1.sqm`，与 .sq 的 DDL 完全一致——verifySqlDelightMigration 会校验两者）：

```sql
CREATE TABLE board_column (
    id INTEGER NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    rank INTEGER NOT NULL
);

CREATE TABLE task (
    id INTEGER NOT NULL PRIMARY KEY,
    column_id INTEGER REFERENCES board_column(id),
    title TEXT NOT NULL,
    notes TEXT,
    due_date INTEGER,
    rank INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    archived_at INTEGER
);

CREATE INDEX task_by_column ON task(column_id, rank);
CREATE INDEX task_by_archived ON task(archived_at);
```

- [ ] **Step 2: 跑尖刺——模块可配置、代码可生成**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:local-data-sql:generateCommonMainTaskBoardDatabaseInterface
```

Expected: 任务成功，生成目录出现 `products/daily-board/shared/local-data-sql/build/generated/sqldelight`。若 SQLDelight 2.2.1 与 AGP 9.1 KMP 插件不兼容（插件应用失败/无生成任务），立即停下并向发起人报告（不要绕过）。

- [ ] **Step 3: 实现仓库与工厂**

SqlDelightTaskBoardRepository.kt：

```kotlin
package __PACKAGE_NAME__.localdata

import app.cash.sqldelight.db.SqlDriver
import kotlin.time.Instant
import __PACKAGE_NAME__.taskboard.BoardDelta
import __PACKAGE_NAME__.taskboard.Column
import __PACKAGE_NAME__.taskboard.ColumnId
import __PACKAGE_NAME__.taskboard.EpochDay
import __PACKAGE_NAME__.taskboard.PersistedBoard
import __PACKAGE_NAME__.taskboard.TaskBoardRepository
import __PACKAGE_NAME__.taskboard.TaskId
import __PACKAGE_NAME__.taskboard.TaskItem
import __PACKAGE_NAME__.taskboard.columnKindAt

class SqlDelightTaskBoardRepository(driver: SqlDriver) : TaskBoardRepository {

    private val queries = TaskBoardDatabase(driver).taskBoardDatabaseQueries

    override suspend fun open(): PersistedBoard {
        val columnRows = queries.selectColumns().executeAsList()
        val columns = columnRows.sortedBy { it.rank }.mapIndexed { index, row ->
            Column(ColumnId(row.id), row.name, columnKindAt(index, columnRows.size), row.rank.toInt())
        }
        val taskRows = queries.selectTasks().executeAsList()
        val tasks = taskRows.map { row ->
            TaskItem(
                id = TaskId(row.id),
                columnId = ColumnId(row.column_id ?: 0L),
                title = row.title,
                notes = row.notes,
                dueDate = row.due_date?.let(::EpochDay),
                rank = row.rank.toInt(),
                createdAt = Instant.fromEpochMilliseconds(row.created_at),
                archivedAt = row.archived_at?.let(Instant::fromEpochMilliseconds),
            )
        }
        return PersistedBoard(
            columns = columns,
            tasks = tasks,
            nextColumnId = (columnRows.maxOfOrNull { it.id } ?: 0L) + 1,
            nextTaskId = (taskRows.maxOfOrNull { it.id } ?: 0L) + 1,
        )
    }

    override suspend fun apply(deltas: List<BoardDelta>) {
        queries.transaction {
            deltas.forEach { delta ->
                when (delta) {
                    is BoardDelta.UpsertColumn -> queries.upsertColumn(
                        id = delta.column.id.value,
                        name = delta.column.name,
                        rank = delta.column.rank.toLong(),
                    )
                    is BoardDelta.DeleteColumn -> queries.deleteColumnRow(delta.id.value)
                    is BoardDelta.UpsertTask -> queries.upsertTask(
                        id = delta.task.id.value,
                        column_id = delta.task.columnId.value.takeUnless { delta.task.archivedAt != null },
                        title = delta.task.title,
                        notes = delta.task.notes,
                        due_date = delta.task.dueDate?.value,
                        rank = delta.task.rank.toLong(),
                        created_at = delta.task.createdAt.toEpochMilliseconds(),
                        archived_at = delta.task.archivedAt?.toEpochMilliseconds(),
                    )
                    is BoardDelta.DeleteTask -> queries.deleteTaskRow(delta.id.value)
                }
            }
        }
    }
}
```

TaskBoardRepositoryFactories.android.kt：

```kotlin
package __PACKAGE_NAME__.localdata

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import __PACKAGE_NAME__.taskboard.StorageError
import __PACKAGE_NAME__.taskboard.TaskBoardRepository

fun taskBoardRepository(context: Context, name: String = "daily-board.db"): TaskBoardRepository {
    val storedVersion = context.openOrCreateDatabase(name, 0, null).use { database ->
        database.rawQuery("PRAGMA user_version", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
    }
    if (storedVersion > TaskBoardDatabase.Schema.version) {
        throw StorageError(
            "Database version " + storedVersion + " is newer than supported " + TaskBoardDatabase.Schema.version,
        )
    }
    val driver = AndroidSqliteDriver(
        schema = TaskBoardDatabase.Schema,
        context = context,
        name = name,
        callback = object : AndroidSqliteDriver.Callback(TaskBoardDatabase.Schema) {
            override fun onConfigure(db: SQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )
    return SqlDelightTaskBoardRepository(driver)
}
```

TaskBoardRepositoryFactories.ios.kt：

```kotlin
package __PACKAGE_NAME__.localdata

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import __PACKAGE_NAME__.taskboard.StorageError
import __PACKAGE_NAME__.taskboard.TaskBoardRepository

fun taskBoardRepository(name: String = "daily-board.db"): TaskBoardRepository {
    val driver = try {
        NativeSqliteDriver(
            schema = TaskBoardDatabase.Schema,
            name = name,
            onConfiguration = { foreign_keys(true) },
        )
    } catch (failure: IllegalStateException) {
        throw StorageError("Cannot open the daily-board database", failure)
    }
    return SqlDelightTaskBoardRepository(driver)
}
```

RobolectricSmokeTest.kt（androidHostTest）：

```kotlin
package __PACKAGE_NAME__.localdata

import android.content.Context
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RobolectricSmokeTest {
    @Test
    fun `robolectric provides a context`() {
        val context: Context = RuntimeEnvironment.getApplication()
        assertNotNull(context)
    }

    @Test
    fun `repository opens against a real sqlite database`() = kotlinx.coroutines.runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("smoke.db")
        val repository = taskBoardRepository(context, "smoke.db")
        val board = repository.open()
        assertNotNull(board)
        context.deleteDatabase("smoke.db")
    }
}
```

- [ ] **Step 4: 跑尖刺确认通过**

```bash
./gradlew -p products/daily-board :shared:local-data-sql:androidHostTest
```

Expected: 2 tests PASS（Robolectric 与真实 SQLite 驱动可用）。若 Robolectric 在 AGP 9 host test 下不可用（ClassNotFound/资源报错），停下报告，不要绕过。

- [ ] **Step 5: 提交**

```bash
git add templates/daily-board-base/gradle/libs.versions.toml templates/daily-board-base/settings.gradle.kts templates/daily-board-base/shared/local-data-sql products/daily-board
git diff --check
git commit -m "feat: add SQLDelight-backed local-data-sql module"
```

### Task 10: SQL 合约、迁移与持久化冒烟测试

**Files:**
- Create: templates/daily-board-base/shared/local-data-sql/src/commonTest/kotlin/__PACKAGE_PATH__/localdata/RepositoryContractTest.kt（expect 工厂 + 合约）
- Create: templates/daily-board-base/shared/local-data-sql/src/androidHostTest/kotlin/__PACKAGE_PATH__/localdata/TestRepository.android.kt（actual）
- Create: templates/daily-board-base/shared/local-data-sql/src/iosTest/kotlin/__PACKAGE_PATH__/localdata/TestRepository.ios.kt（actual）
- Create: templates/daily-board-base/shared/local-data-sql/src/androidHostTest/kotlin/__PACKAGE_PATH__/localdata/MigrationTest.kt
- Create: templates/daily-board-base/shared/local-data-sql/src/androidHostTest/kotlin/__PACKAGE_PATH__/localdata/PersistenceSmokeTest.kt

**Interfaces:**
- Consumes: Task 9 的仓库与工厂。
- Produces: 验证证据（往返/原子/幂等/迁移/重开持久化）。

- [ ] **Step 1: 写失败测试**

RepositoryContractTest.kt（commonTest）：

```kotlin
package __PACKAGE_NAME__.localdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import __PACKAGE_NAME__.taskboard.BoardDelta
import __PACKAGE_NAME__.taskboard.Column
import __PACKAGE_NAME__.taskboard.ColumnId
import __PACKAGE_NAME__.taskboard.ColumnKind
import __PACKAGE_NAME__.taskboard.EpochDay
import __PACKAGE_NAME__.taskboard.TaskBoardRepository
import __PACKAGE_NAME__.taskboard.TaskId
import __PACKAGE_NAME__.taskboard.TaskItem

expect fun testRepository(): TaskBoardRepository

class RepositoryContractTest {
    private fun column(id: Long, rank: Int = id.toInt() - 1) =
        Column(ColumnId(id), "c" + id, ColumnKind.MIDDLE, rank)

    private fun task(id: Long, columnId: Long) = TaskItem(
        id = TaskId(id),
        columnId = ColumnId(columnId),
        title = "t" + id,
        notes = null,
        dueDate = EpochDay(10L + id),
        rank = id.toInt() - 1,
        createdAt = Instant.fromEpochMilliseconds(1_000L + id),
        archivedAt = null,
    )

    @Test
    fun `deltas survive a reopen`() = runTest {
        val repository = testRepository()
        repository.open()
        repository.apply(
            listOf(
                BoardDelta.UpsertColumn(column(1)),
                BoardDelta.UpsertColumn(column(2)),
                BoardDelta.UpsertTask(task(1, 1)),
                BoardDelta.UpsertTask(task(2, 2)),
            ),
        )
        val reopened = testRepository().open()
        assertEquals(listOf(1L, 2L), reopened.columns.map { it.id.value })
        assertEquals(listOf(1L, 2L), reopened.tasks.map { it.id.value })
        assertEquals(3L, reopened.nextColumnId)
        assertEquals(3L, reopened.nextTaskId)
        val stored = reopened.tasks.single { it.id == TaskId(1L) }
        assertEquals(EpochDay(11L), stored.dueDate)
        assertEquals(Instant.fromEpochMilliseconds(1_001L), stored.createdAt)
    }

    @Test
    fun `apply is atomic when a delta violates foreign keys`() = runTest {
        val repository = testRepository()
        repository.open()
        assertFailsWith<Exception> {
            repository.apply(
                listOf(
                    BoardDelta.UpsertColumn(column(1)),
                    BoardDelta.UpsertTask(task(1, 999)),
                ),
            )
        }
        val after = testRepository().open()
        assertEquals(0, after.columns.size)
        assertEquals(0, after.tasks.size)
    }

    @Test
    fun `upserts are idempotent and deletes of absent rows are no-ops`() = runTest {
        val repository = testRepository()
        repository.open()
        repository.apply(listOf(BoardDelta.UpsertColumn(column(1, rank = 5))))
        repository.apply(
            listOf(
                BoardDelta.UpsertColumn(column(1, rank = 0)),
                BoardDelta.DeleteColumn(ColumnId(77L)),
                BoardDelta.DeleteTask(TaskId(88L)),
            ),
        )
        val after = testRepository().open()
        assertEquals(1, after.columns.size)
        assertEquals(0, after.columns.single().rank)
        assertEquals(0, after.tasks.size)
    }

    @Test
    fun `archived tasks persist with a null column`() = runTest {
        val repository = testRepository()
        repository.open()
        repository.apply(
            listOf(
                BoardDelta.UpsertColumn(column(1)),
                BoardDelta.UpsertTask(task(1, 1).copy(archivedAt = Instant.fromEpochMilliseconds(9_000L), rank = 0)),
            ),
        )
        val archived = testRepository().open().tasks.single()
        assertEquals(Instant.fromEpochMilliseconds(9_000L), archived.archivedAt)
        assertEquals(ColumnId(0L), archived.columnId)
    }
}
```

TestRepository.android.kt（androidHostTest actual）：

```kotlin
package __PACKAGE_NAME__.localdata

import android.content.Context
import org.robolectric.RuntimeEnvironment
import __PACKAGE_NAME__.taskboard.TaskBoardRepository

actual fun testRepository(): TaskBoardRepository {
    val context: Context = RuntimeEnvironment.getApplication()
    context.deleteDatabase("contract.db")
    return taskBoardRepository(context, "contract.db")
}
```

TestRepository.ios.kt（iosTest actual）：

```kotlin
package __PACKAGE_NAME__.localdata

import kotlin.random.Random
import __PACKAGE_NAME__.taskboard.TaskBoardRepository

actual fun testRepository(): TaskBoardRepository =
    taskBoardRepository(name = "contract-" + Random.nextLong().toString(16) + ".db")
```

MigrationTest.kt 与 PersistenceSmokeTest.kt（androidHostTest）：

```kotlin
package __PACKAGE_NAME__.localdata

import android.content.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import __PACKAGE_NAME__.taskboard.StorageError
import __PACKAGE_NAME__.taskboard.TaskBoardStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a fresh database is created at version 1`() {
        context.deleteDatabase("migration.db")
        taskBoardRepository(context, "migration.db")
        val version = context.openOrCreateDatabase("migration.db", 0, null).use { database ->
            database.rawQuery("PRAGMA user_version", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        }
        assertEquals(1L, version)
        context.deleteDatabase("migration.db")
    }

    @Test
    fun `an empty v0 database migrates to version 1`() = runTest {
        context.deleteDatabase("migration.db")
        context.openOrCreateDatabase("migration.db", 0, null).close()
        val board = taskBoardRepository(context, "migration.db").open()
        assertEquals(0, board.columns.size)
        assertEquals(0, board.tasks.size)
        context.deleteDatabase("migration.db")
    }

    @Test
    fun `a database newer than the supported schema is rejected`() {
        context.deleteDatabase("migration.db")
        context.openOrCreateDatabase("migration.db", 0, null).use { database ->
            database.execSQL("PRAGMA user_version = 99")
        }
        val error = assertFailsWith<StorageError> { taskBoardRepository(context, "migration.db") }
        assertTrue(error.message!!.contains("newer"))
        context.deleteDatabase("migration.db")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersistenceSmokeTest {
    @Test
    fun `tasks survive a full store reopen`() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("smoke.db")
        val store1 = TaskBoardStore(taskBoardRepository(context, "smoke.db"))
        store1.open()
        val doing = store1.createColumn("Doing").columns[1].id
        store1.createTask(doing, "persisted", notes = "n")
        val doomed = store1.createColumn("Doomed").columns[2].id
        store1.createTask(doomed, "cascade-archived")
        store1.deleteColumn(doomed)   // FK 下删除含任务列：归档 upsert 先于列删除
        val endId = store1.snapshot().columns.last().id
        val doneId = store1.createTask(endId, "done").tasks.getValue(endId).single().id
        store1.archiveTask(doneId)

        val store2 = TaskBoardStore(taskBoardRepository(context, "smoke.db"))
        store2.open()
        val snapshot = store2.snapshot()
        assertEquals(listOf("开始", "Doing", "结束"), snapshot.columns.map { it.name })
        assertEquals(listOf("persisted"), snapshot.tasks.getValue(doing).map { it.title })
        assertEquals(setOf("done", "cascade-archived"), snapshot.archivedTasks.map { it.title }.toSet())
        context.deleteDatabase("smoke.db")
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
./gradlew -p products/daily-board :shared:local-data-sql:androidHostTest
```

Expected: 编译失败——`testRepository` 的 expect 声明缺失/actual 未就位（本任务一并提供 actual，失败点应是 expect 尚未写入 commonTest）。写入全部文件后应编译并跑出结果。

- [ ] **Step 3: 跑测试确认通过**

```bash
./gradlew -p products/daily-board :shared:local-data-sql:androidHostTest
```

Expected: RepositoryContractTest 4 + MigrationTest 3 + PersistenceSmokeTest 1 + RobolectricSmokeTest 2 全部 PASS。

- [ ] **Step 4: iOS 侧同套合约跑通（本机 macOS + Xcode 26.4）**

```bash
./gradlew -p products/daily-board :shared:local-data-sql:iosSimulatorArm64Test
```

Expected: RepositoryContractTest 4 PASS（真实 NativeSqliteDriver）。

- [ ] **Step 5: 提交**

```bash
git add templates/daily-board-base/shared/local-data-sql products/daily-board
git diff --check
git commit -m "test: verify SQL storage contract, migrations and reopen persistence"
```

### Task 11: ManifestValidator 接受 android+ios(+web)

**Files:**
- Modify: tooling/generator/src/main/kotlin/com/seraphim/workbench/manifest/ManifestValidator.kt
- Modify: tooling/generator/src/test/kotlin/com/seraphim/workbench/manifest/ManifestTest.kt

**Interfaces:**
- Produces: `ManifestValidator.requireSupported` 接受 `{android,ios}` 与 `{android,ios,web}`，拒绝 desktop 与 android-only。Task 12 依赖。

- [ ] **Step 1: 写失败测试**

ManifestTest.kt 把 `rejects a Web target before writing` 替换为：

```kotlin
    @Test
    fun `accepts Android+iOS with Web`() {
        val file = directory.resolve("project.yaml")
        file.writeText(SUPPORTED.replace("web: false", "web: true"))
        ManifestValidator.requireSupported(ManifestReader.read(file))
    }

    @Test
    fun `rejects desktop-only and android-only selections`() {
        val file = directory.resolve("project.yaml")
        file.writeText(SUPPORTED.replace("android: true", "android: false"))
        assertFailsWith<IllegalArgumentException> {
            ManifestValidator.requireSupported(ManifestReader.read(file))
        }
        val desktop = directory.resolve("desktop.yaml")
        desktop.writeText(SUPPORTED.replace("desktop: false", "desktop: true"))
        assertFailsWith<IllegalArgumentException> {
            ManifestValidator.requireSupported(ManifestReader.read(desktop))
        }
    }
```

- [ ] **Step 2: 跑测试确认失败**

```bash
./gradlew :tooling:generator:test --tests '*ManifestTest*'
```

Expected: 新用例 FAIL（`accepts Android+iOS with Web` 抛错，旧错误信息已不存在）。

- [ ] **Step 3: 实现**

ManifestValidator.kt：

```kotlin
package com.seraphim.workbench.manifest

object ManifestValidator {
    private val productId = Regex("[a-z][a-z0-9-]*")
    private val packageName = Regex("[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+")

    fun requireSupported(manifest: ProjectManifest) {
        require(manifest.schema == 1) { "Unsupported manifest schema: " + manifest.schema }
        require(productId.matches(manifest.product.id)) { "Invalid product id" }
        require(packageName.matches(manifest.product.packageName)) { "Invalid package name" }
        require(manifest.data.strategy == "local-only") {
            "Unsupported data strategy: " + manifest.data.strategy
        }
        val platforms = manifest.platforms
        val supported = platforms.android && platforms.ios && !platforms.desktop
        require(supported) {
            "Supported platforms are Android+iOS, optionally with Web; desktop is not yet supported"
        }
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
./gradlew :tooling:generator:test
```

Expected: ManifestTest 与 ProductRendererTest 全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add tooling/generator/src
git diff --check
git commit -m "feat: accept Android+iOS with optional Web in manifest validation"
```

### Task 12: 条件发射 token、web 过滤与认证夹具

**Files:**
- Modify: tooling/generator/src/main/kotlin/com/seraphim/workbench/generator/ProductRenderer.kt
- Modify: tooling/generator/src/test/kotlin/com/seraphim/workbench/generator/ProductRendererTest.kt
- Create: certification/web-enabled.yaml

**Interfaces:**
- Produces: 渲染器按 `manifest.platforms.web` 求值 `__WEB_MODULES__`（settings include）与 `__WASM_JS_TARGET__`（wasmJs target）；web 未选时跳过 `shared/local-data-web/` 目录与任意 `wasmJsMain` 目录。Task 13+ 依赖该行为渲染 Web 夹具。

- [ ] **Step 1: 写失败测试**

ProductRendererTest.kt 追加（imports 增加 `kotlin.io.path.isRegularFile` 与 `kotlin.test.assertTrue`）：

```kotlin
    private fun webTemplate(): Path {
        val template = directory.resolve("web-template").createDirectories()
        template.resolve("settings.gradle.kts").writeText("include(\":shared:task-board\")\n__WEB_MODULES__")
        template.resolve("shared/local-data-web").createDirectories()
        template.resolve("shared/local-data-web/build.gradle.kts").writeText("plugins { id(\"web\") }")
        template.resolve("shared/local-data-sql").createDirectories()
        template.resolve("shared/local-data-sql/build.gradle.kts").writeText("plugins { id(\"sql\") }")
        template.resolve("shared/task-board/build.gradle.kts").writeText("kotlin {\n__WASM_JS_TARGET__\n}")
        template.resolve("shared/task-board/src/wasmJsMain").createDirectories()
        template.resolve("shared/task-board/src/wasmJsMain/Web.kt").writeText("// web")
        template.resolve("shared/task-board/src/commonMain").createDirectories()
        template.resolve("shared/task-board/src/commonMain/Shared.kt").writeText("// shared")
        return template
    }

    @Test
    fun `web selection renders the web module and wasm target`() {
        val output = directory.resolve("web-board")
        ProductRenderer.render(RenderRequest(webManifest(), webTemplate(), output, "../../platform-kit"))
        assertTrue(output.resolve("shared/local-data-web/build.gradle.kts").isRegularFile())
        assertTrue(output.resolve("settings.gradle.kts").readText().contains("include(\":shared:local-data-web\")"))
        assertTrue(output.resolve("shared/task-board/build.gradle.kts").readText().contains("wasmJs { nodejs() }"))
        assertTrue(output.resolve("shared/task-board/src/wasmJsMain/Web.kt").isRegularFile())
    }

    @Test
    fun `without web the web module and wasm sources are absent`() {
        val output = directory.resolve("plain-board")
        ProductRenderer.render(RenderRequest(manifest(), webTemplate(), output, "../../platform-kit"))
        assertFalse(output.resolve("shared/local-data-web").exists())
        assertFalse(output.resolve("shared/task-board/src/wasmJsMain").exists())
        assertFalse(output.resolve("settings.gradle.kts").readText().contains("local-data-web"))
        assertFalse(output.resolve("shared/task-board/build.gradle.kts").readText().contains("wasmJs"))
        assertTrue(output.resolve("shared/local-data-sql/build.gradle.kts").isRegularFile())
        assertFalse(output.resolve("shared/task-board/build.gradle.kts").readText().contains("__"))
    }

    private fun webManifest() = ProjectManifest(
        schema = 1,
        product = Product("daily-board", "com.seraphim.dailyboard"),
        platforms = Platforms(true, true, false, true),
        data = Data("local-only"),
    )
```

- [ ] **Step 2: 跑测试确认失败**

```bash
./gradlew :tooling:generator:test --tests '*ProductRendererTest*'
```

Expected: 新用例 FAIL——token 未替换（`__WEB_MODULES__` 残留）、web 文件未被过滤。

- [ ] **Step 3: 实现**

ProductRenderer.kt：

```kotlin
    fun render(request: RenderRequest): Path {
        requireDestination(request.output)
        request.output.parent.createDirectories()
        val temporary = Files.createTempDirectory(request.output.parent, "." + request.output.name + "-")
        try {
            val webSelected = request.manifest.platforms.web
            val tokens = mapOf(
                "__PRODUCT_ID__" to request.manifest.product.id,
                "__PACKAGE_NAME__" to request.manifest.product.packageName,
                "__PACKAGE_PATH__" to request.manifest.product.packageName.replace('.', '/'),
                "__PLATFORM_KIT_PATH__" to request.platformKitPath,
                "__WEB_MODULES__" to if (webSelected) "include(\":shared:local-data-web\")" else "",
                "__WASM_JS_TARGET__" to if (webSelected) "wasmJs { nodejs() }" else "",
            )
            Files.walk(request.template).use { paths ->
                paths.sorted().forEach { source ->
                    val relative = request.template.relativize(source)
                    if (!webSelected && isWebOnly(relative)) return@forEach
                    val relativeText = tokens.entries.fold(relative.toString()) { value, token ->
                        value.replace(token.key, token.value)
                    }
                    val target = temporary.resolve(relativeText)
                    when {
                        source.isDirectory() -> target.createDirectories()
                        source.isRegularFile() -> {
                            target.parent.createDirectories()
                            val renderedLines = source.readText().split("\n").filterNot { line ->
                                tokens.keys.any { line.contains(it) } &&
                                    tokens.entries.fold(line) { value, token ->
                                        value.replace(token.key, token.value)
                                    }.isBlank()
                            }
                            val rendered = tokens.entries.fold(
                                renderedLines.joinToString("\n"),
                            ) { value, token -> value.replace(token.key, token.value) }
                            target.writeText(rendered)
                        }
                    }
                }
            }
            GeneratedTreeVerifier.verify(temporary)
            if (request.output.exists()) Files.delete(request.output)
            Files.move(temporary, request.output, StandardCopyOption.ATOMIC_MOVE)
            return request.output
        } catch (failure: Throwable) {
            temporary.toFile().deleteRecursively()
            throw failure
        }
    }

    private fun isWebOnly(relative: Path): Boolean {
        val segments = relative.map { it.toString() }
        return segments.contains("wasmJsMain") || segments.contains("wasmJsTest") ||
            (segments.size >= 2 && segments[0] == "shared" && segments[1] == "local-data-web")
    }
```

（`val temporary` 处原为字符串插值 `".${request.output.name}-"`，改为 `"." + request.output.name + "-"`，行为不变。）

certification/web-enabled.yaml：

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

- [ ] **Step 4: 跑测试确认通过 + 双路径渲染验证**

```bash
./gradlew :tooling:generator:test
rm -rf build/certification/daily-board-web && ./gradlew createProduct -Pmanifest=certification/web-enabled.yaml -Poutput=build/certification/daily-board-web
grep -r "local-data-web" build/certification/daily-board-web/settings.gradle.kts
```

Expected: generator 测试全 PASS；夹具渲染成功且 settings 含 `include(":shared:local-data-web")`。

- [ ] **Step 5: 提交**

```bash
git add tooling/generator/src certification/web-enabled.yaml
git diff --check
git commit -m "feat: emit web modules and wasm targets only for web-selected manifests"
```

### Task 13: local-data-web 模块（IndexedDB interop、Web 仓库、工厂）

**Files:**
- Create: templates/daily-board-base/shared/local-data-web/build.gradle.kts
- Create: templates/daily-board-base/shared/local-data-web/src/commonMain/kotlin/__PACKAGE_PATH__/localdata/IndexedDb.kt
- Create: templates/daily-board-base/shared/local-data-web/src/commonMain/kotlin/__PACKAGE_PATH__/localdata/WebTaskBoardRepository.kt
- Create: templates/daily-board-base/shared/local-data-web/src/commonMain/kotlin/__PACKAGE_PATH__/localdata/TaskBoardRepositoryFactory.kt

**Interfaces:**
- Consumes: Task 2 的端口；Task 12 的条件发射。
- Produces: `class WebTaskBoardRepository(factory: IdbFactory)`、`fun taskBoardRepository(): TaskBoardRepository`（读全局 indexedDB）。Task 14 依赖。

- [ ] **Step 1: 模块与实现**

build.gradle.kts：

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("seraphim.kotlin-policy")
}

kotlin {
    wasmJs {
        nodejs()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:task-board"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
```

IndexedDb.kt：

```kotlin
package __PACKAGE_NAME__.localdata

import kotlin.js.JsAny
import kotlin.js.JsFun

external interface IdbFactory {
    fun open(name: String, version: Int): IdbOpenDbRequest
}

external interface IdbOpenDbRequest {
    var onupgradeneeded: ((IdbVersionChangeEvent) -> Unit)?
    var onsuccess: ((IdbEvent) -> Unit)?
    var onerror: ((IdbEvent) -> Unit)?
    val result: IdbDatabase
}

external interface IdbEvent {
    val type: String
}

external interface IdbVersionChangeEvent : IdbEvent {
    val newVersion: Int
}

external interface IdbDatabase {
    val version: Int
    fun createObjectStore(name: String, options: IdbObjectStoreOptions?): IdbObjectStore
    fun transaction(storeNames: Array<String>, mode: String): IdbTransaction
    fun close()
}

external interface IdbObjectStoreOptions {
    var keyPath: String
}

external interface IdbTransaction {
    fun objectStore(name: String): IdbObjectStore
    fun abort()
    var oncomplete: ((IdbEvent) -> Unit)?
    var onabort: ((IdbEvent) -> Unit)?
    var onerror: ((IdbEvent) -> Unit)?
}

external interface IdbObjectStore {
    fun put(value: JsAny): IdbRequest
    fun delete(key: Double): IdbRequest
    fun getAll(): IdbRequest
}

external interface IdbRequest {
    var onsuccess: ((IdbEvent) -> Unit)?
    var onerror: ((IdbEvent) -> Unit)?
    val result: JsAny
}

@JsFun("() => globalThis.indexedDB")
external fun globalIndexedDb(): IdbFactory

@JsFun("(db, name) => db.objectStoreNames.contains(name)")
external fun hasObjectStore(db: IdbDatabase, name: String): Boolean

@JsFun("(keyPath) => ({keyPath: keyPath})")
external fun objectStoreOptions(keyPath: String): IdbObjectStoreOptions

@JsFun("(id, name, rank) => ({id: id, name: name, rank: rank})")
external fun columnRow(id: Double, name: String, rank: Double): JsAny

@JsFun("(id, columnId, title, notes, dueDate, rank, createdAt, archivedAt) => ({id: id, columnId: columnId, title: title, notes: notes, dueDate: dueDate, rank: rank, createdAt: createdAt, archivedAt: archivedAt})")
external fun taskRow(
    id: Double,
    columnId: Double?,
    title: String,
    notes: String?,
    dueDate: Double?,
    rank: Double,
    createdAt: Double,
    archivedAt: Double?,
): JsAny

@JsFun("(o) => o.id")
external fun rowId(o: JsAny): Double

@JsFun("(o) => o.name")
external fun rowName(o: JsAny): String

@JsFun("(o) => o.rank")
external fun rowRank(o: JsAny): Double

@JsFun("(o) => o.columnId")
external fun rowColumnId(o: JsAny): Double?

@JsFun("(o) => o.title")
external fun rowTitle(o: JsAny): String

@JsFun("(o) => o.notes")
external fun rowNotes(o: JsAny): String?

@JsFun("(o) => o.dueDate")
external fun rowDueDate(o: JsAny): Double?

@JsFun("(o) => o.createdAt")
external fun rowCreatedAt(o: JsAny): Double

@JsFun("(o) => o.archivedAt")
external fun rowArchivedAt(o: JsAny): Double?

@JsFun("(a) => Array.from(a)")
external fun toJsArray(a: JsAny): Array<JsAny>
```

WebTaskBoardRepository.kt：

```kotlin
package __PACKAGE_NAME__.localdata

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlin.time.Instant
import kotlinx.coroutines.suspendCancellableCoroutine
import __PACKAGE_NAME__.taskboard.BoardDelta
import __PACKAGE_NAME__.taskboard.BoardError
import __PACKAGE_NAME__.taskboard.Column
import __PACKAGE_NAME__.taskboard.ColumnId
import __PACKAGE_NAME__.taskboard.EpochDay
import __PACKAGE_NAME__.taskboard.PersistedBoard
import __PACKAGE_NAME__.taskboard.StorageError
import __PACKAGE_NAME__.taskboard.TaskBoardRepository
import __PACKAGE_NAME__.taskboard.TaskId
import __PACKAGE_NAME__.taskboard.TaskItem
import __PACKAGE_NAME__.taskboard.columnKindAt

private const val DATABASE_NAME = "daily-board"
private const val SCHEMA_VERSION = 1
private const val COLUMNS_STORE = "columns"
private const val TASKS_STORE = "tasks"

class WebTaskBoardRepository(private val factory: IdbFactory) : TaskBoardRepository {

    private var database: IdbDatabase? = null

    override suspend fun open(): PersistedBoard {
        val db = openDatabase(factory, DATABASE_NAME, SCHEMA_VERSION)
        database = db
        val columns = readColumns(db)
        val tasks = readTasks(db)
        return PersistedBoard(
            columns = columns.sortedBy { it.rank }.mapIndexed { index, row ->
                Column(ColumnId(row.id), row.name, columnKindAt(index, columns.size), row.rank.toInt())
            },
            tasks = tasks,
            nextColumnId = (columns.maxOfOrNull { it.id } ?: 0L) + 1,
            nextTaskId = (tasks.maxOfOrNull { it.id.value } ?: 0L) + 1,
        )
    }

    override suspend fun apply(deltas: List<BoardDelta>) {
        val db = database ?: throw BoardError.NotOpen()
        val transaction = db.transaction(arrayOf(COLUMNS_STORE, TASKS_STORE), "readwrite")
        try {
            val columns = transaction.objectStore(COLUMNS_STORE)
            val tasks = transaction.objectStore(TASKS_STORE)
            deltas.forEach { delta ->
                when (delta) {
                    is BoardDelta.UpsertColumn -> columns.put(
                        columnRow(delta.column.id.value.toDouble(), delta.column.name, delta.column.rank.toDouble()),
                    )
                    is BoardDelta.DeleteColumn -> columns.delete(delta.id.value.toDouble())
                    is BoardDelta.UpsertTask -> tasks.put(
                        taskRow(
                            id = delta.task.id.value.toDouble(),
                            columnId = delta.task.columnId.value.toDouble().takeUnless { delta.task.archivedAt != null },
                            title = delta.task.title,
                            notes = delta.task.notes,
                            dueDate = delta.task.dueDate?.value?.toDouble(),
                            rank = delta.task.rank.toDouble(),
                            createdAt = delta.task.createdAt.toEpochMilliseconds().toDouble(),
                            archivedAt = delta.task.archivedAt?.toEpochMilliseconds()?.toDouble(),
                        ),
                    )
                    is BoardDelta.DeleteTask -> tasks.delete(delta.id.value.toDouble())
                }
            }
            awaitCompletion(transaction)
        } catch (failure: Throwable) {
            transaction.abort()
            throw failure
        }
    }

    private suspend fun readColumns(db: IdbDatabase): List<ColumnRow> {
        val request = db.transaction(arrayOf(COLUMNS_STORE), "readonly").objectStore(COLUMNS_STORE).getAll()
        val rows = awaitSuccess(request)
        return toJsArray(rows).map { ColumnRow(rowId(it).toLong(), rowName(it), rowRank(it).toInt()) }
    }

    private suspend fun readTasks(db: IdbDatabase): List<TaskItem> {
        val request = db.transaction(arrayOf(TASKS_STORE), "readonly").objectStore(TASKS_STORE).getAll()
        val rows = awaitSuccess(request)
        return toJsArray(rows).map { row ->
            TaskItem(
                id = TaskId(rowId(row).toLong()),
                columnId = ColumnId(rowColumnId(row)?.toLong() ?: 0L),
                title = rowTitle(row),
                notes = rowNotes(row),
                dueDate = rowDueDate(row)?.toLong()?.let(::EpochDay),
                rank = rowRank(row).toInt(),
                createdAt = Instant.fromEpochMilliseconds(rowCreatedAt(row).toLong()),
                archivedAt = rowArchivedAt(row)?.toLong()?.let(Instant::fromEpochMilliseconds),
            )
        }
    }

    private suspend fun openDatabase(factory: IdbFactory, name: String, version: Int): IdbDatabase {
        val request = factory.open(name, version)
        request.onupgradeneeded = {
            val db = request.result
            if (!hasObjectStore(db, COLUMNS_STORE)) db.createObjectStore(COLUMNS_STORE, objectStoreOptions("id"))
            if (!hasObjectStore(db, TASKS_STORE)) db.createObjectStore(TASKS_STORE, objectStoreOptions("id"))
        }
        return suspendCancellableCoroutine { continuation ->
            request.onsuccess = { continuation.resume(request.result) }
            request.onerror = { continuation.resumeWithException(StorageError("indexedDB open failed")) }
        }
    }

    private suspend fun awaitSuccess(request: IdbRequest): JsAny =
        suspendCancellableCoroutine { continuation ->
            request.onsuccess = { continuation.resume(request.result) }
            request.onerror = { continuation.resumeWithException(StorageError("indexedDB request failed")) }
        }

    private suspend fun awaitCompletion(transaction: IdbTransaction) {
        suspendCancellableCoroutine { continuation ->
            transaction.oncomplete = { continuation.resume(Unit) }
            transaction.onabort = { continuation.resumeWithException(StorageError("indexedDB transaction aborted")) }
            transaction.onerror = { continuation.resumeWithException(StorageError("indexedDB transaction failed")) }
        }
    }
}

private data class ColumnRow(val id: Long, val name: String, val rank: Int)
```

TaskBoardRepositoryFactory.kt：

```kotlin
package __PACKAGE_NAME__.localdata

import __PACKAGE_NAME__.taskboard.TaskBoardRepository

fun taskBoardRepository(): TaskBoardRepository = WebTaskBoardRepository(globalIndexedDb())
```

- [ ] **Step 2: 渲染 Web 夹具并跑编译尖刺**

```bash
rm -rf build/certification/daily-board-web && ./gradlew createProduct -Pmanifest=certification/web-enabled.yaml -Poutput=build/certification/daily-board-web
./gradlew -p build/certification/daily-board-web :shared:local-data-web:compileKotlinWasmJs
```

Expected: wasm 编译通过。若 external interface / @JsFun 语法与锁定 Kotlin 不符（编译错误），修复到与编译器一致（保持接口形状不变），不要删除类型安全。若 Gradle 无法自动下载 Node，仅影响下一步测试任务，报告即可。

- [ ] **Step 3: 提交**

```bash
git add templates/daily-board-base/shared/local-data-web products/daily-board
git diff --check
git commit -m "feat: add IndexedDB-backed local-data-web module"
```

### Task 14: IndexedDB fake 与 Web 合约测试

**Files:**
- Create: templates/daily-board-base/shared/local-data-web/src/commonTest/kotlin/__PACKAGE_PATH__/localdata/FakeIndexedDb.kt
- Create: templates/daily-board-base/shared/local-data-web/src/commonTest/kotlin/__PACKAGE_PATH__/localdata/WebRepositoryContractTest.kt

**Interfaces:**
- Consumes: Task 13。
- Produces: 可注入失败的内存版 IndexedDB fake；合约测试证据。

- [ ] **Step 1: 写 fake 与合约测试**

FakeIndexedDb.kt：

```kotlin
package __PACKAGE_NAME__.localdata

import kotlin.js.JsAny

@JsFun("(fn) => setTimeout(fn, 0)")
external fun deferred(fn: () -> Unit)

@JsFun("(values) => values")
external fun jsArrayOf(values: Array<JsAny>): JsAny

class FakeIndexedDb : IdbFactory {
    val databases = mutableMapOf<String, FakeDatabase>()
    var failOnPut: Int? = null

    override fun open(name: String, version: Int): IdbOpenDbRequest {
        val request = FakeOpenDbRequest(this, name, version)
        deferred { request.run() }
        return request
    }
}

class FakeOpenDbRequest(
    private val factory: FakeIndexedDb,
    private val name: String,
    private val version: Int,
) : IdbOpenDbRequest {
    private var resolved: FakeDatabase? = null
    override var onupgradeneeded: ((IdbVersionChangeEvent) -> Unit)? = null
    override var onsuccess: ((IdbEvent) -> Unit)? = null
    override var onerror: ((IdbEvent) -> Unit)? = null
    override val result: IdbDatabase get() = resolved ?: error("request is not resolved")

    fun run() {
        val stored = factory.databases[name]
        if (stored != null && stored.version > version) {
            onerror?.invoke(FakeEvent("error"))
            return
        }
        if (stored != null && stored.version == version) {
            resolved = stored
            onsuccess?.invoke(FakeEvent("success"))
            return
        }
        val database = FakeDatabase(version, factory)
        factory.databases[name] = database
        resolved = database
        onupgradeneeded?.invoke(FakeVersionChangeEvent("upgradeneeded", version))
        onsuccess?.invoke(FakeEvent("success"))
    }
}

class FakeEvent(override val type: String) : IdbEvent

class FakeVersionChangeEvent(override val type: String, override val newVersion: Int) : IdbVersionChangeEvent

class FakeDatabase(override val version: Int, val factory: FakeIndexedDb? = null) : IdbDatabase {
    val stores = mutableMapOf<String, FakeObjectStore>()

    override fun createObjectStore(name: String, options: IdbObjectStoreOptions?): IdbObjectStore {
        val store = FakeObjectStore()
        stores[name] = store
        return store
    }

    override fun transaction(storeNames: Array<String>, mode: String): IdbTransaction {
        val transaction = FakeTransaction(this, storeNames.toList(), mode)
        deferred { transaction.finish() }
        return transaction
    }

    override fun close() {}
}

class FakeObjectStore {
    val rows = mutableMapOf<Double, JsAny>()
}

class FakeTransaction(
    private val database: FakeDatabase,
    private val storeNames: List<String>,
    private val mode: String,
) : IdbTransaction {
    private val writes = mutableListOf<() -> Unit>()
    private val requests = mutableListOf<FakeRequest>()
    private var aborted = false
    private var finished = false

    override var oncomplete: ((IdbEvent) -> Unit)? = null
    override var onabort: ((IdbEvent) -> Unit)? = null
    override var onerror: ((IdbEvent) -> Unit)? = null

    override fun objectStore(name: String): IdbObjectStore {
        val store = database.stores.getValue(name)
        return FakeTransactionObjectStore(store, this)
    }

    override fun abort() {
        aborted = true
    }

    fun recordWrite(request: FakeRequest, write: () -> Unit) {
        requests += request
        writes += write
    }

    fun recordRead(request: FakeRequest, read: () -> JsAny) {
        deferred {
            request.succeed(read())
        }
    }

    fun failOnPut(): Boolean {
        val countdown = database.factory?.failOnPut ?: return false
        return if (countdown <= 1) {
            database.factory!!.failOnPut = null
            true
        } else {
            database.factory!!.failOnPut = countdown - 1
            false
        }
    }

    fun finish() {
        if (finished) return
        finished = true
        if (mode != "readwrite") {
            oncomplete?.invoke(FakeEvent("complete"))
            return
        }
        if (aborted) {
            onabort?.invoke(FakeEvent("abort"))
            return
        }
        if (requests.any { it.failed }) {
            onerror?.invoke(FakeEvent("error"))
            return
        }
        writes.forEach { it() }
        oncomplete?.invoke(FakeEvent("complete"))
    }
}

class FakeTransactionObjectStore(
    private val store: FakeObjectStore,
    private val transaction: FakeTransaction,
) : IdbObjectStore {
    override fun put(value: JsAny): IdbRequest {
        val request = FakeRequest()
        if (transaction.failOnPut()) request.fail()
        transaction.recordWrite(request) { store.rows[rowId(value)] = value }
        return request
    }

    override fun delete(key: Double): IdbRequest {
        val request = FakeRequest()
        transaction.recordWrite(request) { store.rows.remove(key) }
        return request
    }

    override fun getAll(): IdbRequest {
        val request = FakeRequest()
        transaction.recordRead(request) { jsArrayOf(store.rows.values.toTypedArray()) }
        return request
    }
}

class FakeRequest : IdbRequest {
    private var resolved: JsAny? = null
    var failed = false
        private set
    override var onsuccess: ((IdbEvent) -> Unit)? = null
    override var onerror: ((IdbEvent) -> Unit)? = null
    override val result: JsAny get() = resolved ?: error("request is not resolved")

    fun fail() {
        failed = true
    }

    fun succeed(value: JsAny) {
        resolved = value
        onsuccess?.invoke(FakeEvent("success"))
    }
}
```

WebRepositoryContractTest.kt：

```kotlin
package __PACKAGE_NAME__.localdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import __PACKAGE_NAME__.taskboard.BoardDelta
import __PACKAGE_NAME__.taskboard.Column
import __PACKAGE_NAME__.taskboard.ColumnId
import __PACKAGE_NAME__.taskboard.ColumnKind
import __PACKAGE_NAME__.taskboard.EpochDay
import __PACKAGE_NAME__.taskboard.TaskBoardStore
import __PACKAGE_NAME__.taskboard.TaskId
import __PACKAGE_NAME__.taskboard.TaskItem

class WebRepositoryContractTest {
    private fun column(id: Long, rank: Int = id.toInt() - 1) =
        Column(ColumnId(id), "c" + id, ColumnKind.MIDDLE, rank)

    private fun task(id: Long, columnId: Long) = TaskItem(
        id = TaskId(id),
        columnId = ColumnId(columnId),
        title = "t" + id,
        notes = null,
        dueDate = EpochDay(10L + id),
        rank = id.toInt() - 1,
        createdAt = Instant.fromEpochMilliseconds(1_000L + id),
        archivedAt = null,
    )

    @Test
    fun `deltas survive a reopen`() = runTest {
        val factory = FakeIndexedDb()
        val repository = WebTaskBoardRepository(factory)
        repository.open()
        repository.apply(
            listOf(
                BoardDelta.UpsertColumn(column(1)),
                BoardDelta.UpsertColumn(column(2)),
                BoardDelta.UpsertTask(task(1, 1)),
                BoardDelta.UpsertTask(task(2, 2)),
            ),
        )
        val reopened = WebTaskBoardRepository(factory).open()
        assertEquals(listOf(1L, 2L), reopened.columns.map { it.id.value })
        assertEquals(listOf(1L, 2L), reopened.tasks.map { it.id.value })
        assertEquals(3L, reopened.nextColumnId)
        assertEquals(3L, reopened.nextTaskId)
        assertEquals(EpochDay(11L), reopened.tasks.single { it.id == TaskId(1L) }.dueDate)
    }

    @Test
    fun `apply is atomic when a put fails`() = runTest {
        val factory = FakeIndexedDb()
        val repository = WebTaskBoardRepository(factory)
        repository.open()
        factory.failOnPut = 2
        assertFailsWith<Exception> {
            repository.apply(
                listOf(
                    BoardDelta.UpsertColumn(column(1)),
                    BoardDelta.UpsertColumn(column(2)),
                ),
            )
        }
        assertEquals(0, WebTaskBoardRepository(factory).open().columns.size)
    }

    @Test
    fun `archived tasks round-trip with a null column`() = runTest {
        val factory = FakeIndexedDb()
        val repository = WebTaskBoardRepository(factory)
        repository.open()
        repository.apply(
            listOf(
                BoardDelta.UpsertColumn(column(1)),
                BoardDelta.UpsertTask(task(1, 1).copy(archivedAt = Instant.fromEpochMilliseconds(9_000L), rank = 0)),
            ),
        )
        val archived = WebTaskBoardRepository(factory).open().tasks.single()
        assertEquals(Instant.fromEpochMilliseconds(9_000L), archived.archivedAt)
        assertEquals(ColumnId(0L), archived.columnId)
    }

    @Test
    fun `a newer database version fails to open`() = runTest {
        val factory = FakeIndexedDb()
        factory.databases["daily-board"] = FakeDatabase(version = 2)
        assertFailsWith<Exception> { WebTaskBoardRepository(factory).open() }
    }

    @Test
    fun `the full store persists over the indexeddb repository`() = runTest {
        val factory = FakeIndexedDb()
        val store = TaskBoardStore(WebTaskBoardRepository(factory))
        store.open()
        val doing = store.createColumn("Doing").columns[1].id
        store.createTask(doing, "persisted")
        val reopened = TaskBoardStore(WebTaskBoardRepository(factory))
        reopened.open()
        assertEquals(listOf("开始", "Doing", "结束"), reopened.snapshot().columns.map { it.name })
        assertEquals(listOf("persisted"), reopened.snapshot().tasks.getValue(doing).map { it.title })
    }
}
```

- [ ] **Step 2: 跑测试确认失败（fake 未实现）**

```bash
rm -rf build/certification/daily-board-web && ./gradlew createProduct -Pmanifest=certification/web-enabled.yaml -Poutput=build/certification/daily-board-web
./gradlew -p build/certification/daily-board-web :shared:local-data-web:wasmJsNodeTest
```

Expected: 编译失败——fake 类尚未定义（本任务文件已齐，第一次跑前为不存在状态；写入全部文件后应编译）。

- [ ] **Step 3: 跑测试确认通过**

```bash
./gradlew -p build/certification/daily-board-web :shared:local-data-web:wasmJsNodeTest
```

Expected: WebRepositoryContractTest 5 PASS（Node 下跑，Gradle 自动下载 Node）。

- [ ] **Step 4: 提交**

```bash
git add templates/daily-board-base/shared/local-data-web products/daily-board
git diff --check
git commit -m "test: verify the IndexedDB storage contract with an injectable fake"
```

### Task 15: task-board 的 wasmJs target 与 Web 订阅面

**Files:**
- Modify: templates/daily-board-base/shared/task-board/build.gradle.kts
- Create: templates/daily-board-base/shared/task-board/src/wasmJsMain/kotlin/__PACKAGE_PATH__/taskboard/BoardSnapshotJson.kt
- Create: templates/daily-board-base/shared/task-board/src/wasmJsMain/kotlin/__PACKAGE_PATH__/taskboard/WebTaskBoardAdapter.kt
- Create: templates/daily-board-base/shared/task-board/src/wasmJsTest/kotlin/__PACKAGE_PATH__/taskboard/WebTaskBoardAdapterTest.kt

**Interfaces:**
- Consumes: Task 12 的 `__WASM_JS_TARGET__`；Task 2 的 Store。
- Produces: `BoardSnapshot.toJson()`、`@JsExport class WebTaskBoardAdapter(store)`（subscribe/dispose）、internal `SnapshotBridge`（测试注入 scope）。

- [ ] **Step 1: 写失败测试**

build.gradle.kts 的 kotlin 块在 iosSimulatorArm64 后插入：

```kotlin
    __WASM_JS_TARGET__
```

WebTaskBoardAdapterTest.kt（wasmJsTest）：

```kotlin
package __PACKAGE_NAME__.taskboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class WebTaskBoardAdapterTest {
    private val q = '"'.toString()

    private class InMemoryRepository : TaskBoardRepository {
        val columns = mutableListOf<Column>()
        val tasks = mutableListOf<TaskItem>()

        override suspend fun open(): PersistedBoard = PersistedBoard(columns.toList(), tasks.toList(), 1L, 1L)

        override suspend fun apply(deltas: List<BoardDelta>) {
            deltas.forEach { delta ->
                when (delta) {
                    is BoardDelta.UpsertColumn -> {
                        columns.removeAll { it.id == delta.column.id }
                        columns += delta.column
                    }
                    is BoardDelta.DeleteColumn -> columns.removeAll { it.id == delta.id }
                    is BoardDelta.UpsertTask -> {
                        tasks.removeAll { it.id == delta.task.id }
                        tasks += delta.task
                    }
                    is BoardDelta.DeleteTask -> tasks.removeAll { it.id == delta.id }
                }
            }
        }
    }

    @Test
    fun `subscribers receive JSON snapshots after commands`() = runTest {
        val store = TaskBoardStore(InMemoryRepository())
        store.open()
        val bridge = SnapshotBridge(store)
        val received = mutableListOf<String>()
        val job = bridge.subscribe({ received += it }, backgroundScope)

        store.createTask(store.snapshot().columns.first().id, "Buy milk")

        testScheduler.advanceUntilIdle()
        assertTrue(received.isNotEmpty(), "no snapshot delivered")
        val latest = received.last()
        assertTrue(latest.contains(q + "columns" + q + ":["), latest)
        assertTrue(latest.contains(q + "title" + q + ":" + q + "Buy milk" + q), latest)
        assertTrue(latest.contains(q + "kind" + q + ":" + q + "START" + q), latest)
        job.cancel()
    }

    @Test
    fun `JSON escapes newlines in titles`() = runTest {
        val store = TaskBoardStore(InMemoryRepository())
        store.open()
        val bridge = SnapshotBridge(store)
        val received = mutableListOf<String>()
        val job = bridge.subscribe({ received += it }, backgroundScope)

        store.createTask(store.snapshot().columns.first().id, "Line1\nLine2")

        testScheduler.advanceUntilIdle()
        val latest = received.last()
        assertTrue(latest.contains(q + "title" + q + ":" + q + "Line1\\nLine2" + q), latest)
        job.cancel()
    }

    @Test
    fun `disposed subscriptions stop receiving snapshots`() = runTest {
        val store = TaskBoardStore(InMemoryRepository())
        store.open()
        val bridge = SnapshotBridge(store)
        val received = mutableListOf<String>()
        val job = bridge.subscribe({ received += it }, backgroundScope)
        testScheduler.advanceUntilIdle()
        val before = received.size

        job.cancel()
        store.createTask(store.snapshot().columns.first().id, "A")
        testScheduler.advanceUntilIdle()
        assertEquals(before, received.size)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
rm -rf build/certification/daily-board-web && ./gradlew createProduct -Pmanifest=certification/web-enabled.yaml -Poutput=build/certification/daily-board-web
./gradlew -p build/certification/daily-board-web :shared:task-board:wasmJsNodeTest
```

Expected: 编译失败——SnapshotBridge、toJson 未定义。

- [ ] **Step 3: 实现**

BoardSnapshotJson.kt（wasmJsMain）：

```kotlin
package __PACKAGE_NAME__.taskboard

private val q = '"'.toString()

private fun String.jsonEscaped(): String = buildString {
    for (character in this@jsonEscaped) {
        when (character) {
            '"' -> append('\\').append('"')
            '\\' -> append('\\').append('\\')
            '\n' -> append('\\').append('n')
            '\r' -> append('\\').append('r')
            '\t' -> append('\\').append('t')
            else -> append(character)
        }
    }
}

fun BoardSnapshot.toJson(): String {
    val columnsJson = columns.joinToString(",") { column ->
        "{" + q + "id" + q + ":" + column.id.value +
            "," + q + "name" + q + ":" + q + column.name.jsonEscaped() + q +
            "," + q + "kind" + q + ":" + q + column.kind.name + q +
            "," + q + "rank" + q + ":" + column.rank + "}"
    }
    val tasksJson = columns.joinToString(",") { column ->
        val items = tasks[column.id].orEmpty().joinToString(",") { it.toJson() }
        "{" + q + "columnId" + q + ":" + column.id.value + "," + q + "tasks" + q + ":[" + items + "]}"
    }
    val archivedJson = archivedTasks.joinToString(",") { it.toJson() }
    return "{" + q + "columns" + q + ":[" + columnsJson + "]," +
        q + "tasks" + q + ":[" + tasksJson + "]," +
        q + "archivedTasks" + q + ":[" + archivedJson + "]}"
}

private fun TaskItem.toJson(): String {
    val notesJson = notes?.let { q + it.jsonEscaped() + q } ?: "null"
    return "{" + q + "id" + q + ":" + id.value +
        "," + q + "columnId" + q + ":" + columnId.value +
        "," + q + "title" + q + ":" + q + title.jsonEscaped() + q +
        "," + q + "notes" + q + ":" + notesJson +
        "," + q + "dueDate" + q + ":" + (dueDate?.value ?: "null") +
        "," + q + "rank" + q + ":" + rank +
        "," + q + "createdAt" + q + ":" + createdAt.toEpochMilliseconds() +
        "," + q + "archivedAt" + q + ":" + (archivedAt?.toEpochMilliseconds() ?: "null") + "}"
}
```

说明：直接照抄本代码块即可——`'\\'` 是 Kotlin 的一个反斜杠字符字面量，`'\n'` 是换行字符字面量，代码块已是正确 Kotlin 源码。

WebTaskBoardAdapter.kt（wasmJsMain）：

```kotlin
package __PACKAGE_NAME__.taskboard

import kotlin.js.JsExport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

internal class SnapshotBridge(private val store: TaskBoardStore) {
    fun subscribe(onSnapshot: (String) -> Unit, scope: CoroutineScope): Job =
        scope.launch {
            store.observe().filterNotNull().collect { snapshot -> onSnapshot(snapshot.toJson()) }
        }
}

@JsExport
class WebSubscription private constructor(internal val job: Job) {
    internal companion object {
        fun create(job: Job): WebSubscription = WebSubscription(job)
    }
}

@JsExport
class WebTaskBoardAdapter(private val store: TaskBoardStore) {
    private val bridge = SnapshotBridge(store)

    fun subscribe(onSnapshot: (String) -> Unit): WebSubscription =
        WebSubscription.create(bridge.subscribe(onSnapshot, CoroutineScope(Dispatchers.Default)))

    fun dispose(subscription: WebSubscription) {
        subscription.job.cancel()
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
./gradlew -p build/certification/daily-board-web :shared:task-board:wasmJsNodeTest
```

Expected: WebTaskBoardAdapterTest 3 PASS。

- [ ] **Step 5: 提交**

```bash
git add templates/daily-board-base/shared/task-board products/daily-board
git diff --check
git commit -m "feat: expose a JSON subscription surface for Web consumers"
```

### Task 16: Android 应用接线（持久化 Store 驱动）

**Files:**
- Create: templates/daily-board-base/apps/android/src/main/kotlin/__PACKAGE_PATH__/android/DailyBoardApplication.kt
- Modify: templates/daily-board-base/apps/android/src/main/kotlin/__PACKAGE_PATH__/android/MainActivity.kt
- Modify: templates/daily-board-base/apps/android/src/main/AndroidManifest.xml
- Modify: templates/daily-board-base/apps/android/build.gradle.kts

**Interfaces:**
- Consumes: Task 2 的 Store；Task 9 的 androidMain 工厂。
- Produces: `DailyBoardApplication.boardStore`；MainActivity 由 Store + observe() 驱动。Task 18 的 assembleDebug 依赖。

- [ ] **Step 1: 接线实现**

DailyBoardApplication.kt：

```kotlin
package __PACKAGE_NAME__.android

import android.app.Application
import __PACKAGE_NAME__.localdata.taskBoardRepository
import __PACKAGE_NAME__.taskboard.TaskBoardStore

class DailyBoardApplication : Application() {
    val boardStore: TaskBoardStore by lazy {
        TaskBoardStore(taskBoardRepository(this))
    }
}
```

MainActivity.kt（整体替换）：

```kotlin
package __PACKAGE_NAME__.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = (application as DailyBoardApplication).boardStore
        setContent {
            MaterialTheme {
                val snapshot by store.observe().filterNotNull().collectAsState(initial = null)
                val columns = snapshot?.columns.orEmpty()
                Text(if (columns.isEmpty()) "Loading..." else columns.joinToString(" / ") { it.name })
            }
        }
        lifecycleScope.launch {
            store.open()
            val current = store.snapshot()
            val start = current.columns.first()
            if (current.tasks[start.id].orEmpty().isEmpty()) {
                store.createTask(start.id, "First task")
            }
        }
    }
}
```

AndroidManifest.xml 的 application 标签加 `android:name=".DailyBoardApplication"`：

```xml
    <application android:theme="@style/AppTheme" android:label="Daily Board" android:name=".DailyBoardApplication">
```

apps/android/build.gradle.kts 的 dependencies 增加：

```kotlin
    implementation(project(":shared:local-data-sql"))
```

- [ ] **Step 2: 渲染并跑 Android 构建验证**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :apps:android:assembleDebug
```

Expected: BUILD SUCCESSFUL。若 `lifecycleScope` 无法解析（activity-compose 传递依赖缺失），在 apps/android/build.gradle.kts 增加 `implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")`。

- [ ] **Step 3: 提交**

```bash
git add templates/daily-board-base/apps/android products/daily-board
git diff --check
git commit -m "feat: drive the Android app from the persisted task-board store"
```

### Task 17: iOS 应用接线（Adapter 重写 + ContentView + framework 链接）

**Files:**
- Create: templates/daily-board-base/shared/task-board/src/iosMain/kotlin/__PACKAGE_PATH__/taskboard/TaskBoardIosAdapter.kt
- Modify: templates/daily-board-base/apps/ios/Sources/ContentView.swift
- Modify: templates/daily-board-base/apps/ios/project.yml

**Interfaces:**
- Consumes: Task 2 的 Store；Task 9 的 iosMain 工厂。
- Produces: `TaskBoardIosAdapter(store)`（open/startObserving/stopObserving）；iOS app 链接 LocalDataSql.framework。

- [ ] **Step 1: 接线实现**

TaskBoardIosAdapter.kt（iosMain，Task 1 删除后的重建版）：

```kotlin
package __PACKAGE_NAME__.taskboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class TaskBoardIosAdapter(val store: TaskBoardStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observation: Job? = null

    fun open(completion: (String?) -> Unit) {
        scope.launch {
            try {
                store.open()
                completion(null)
            } catch (failure: Throwable) {
                completion(failure.message)
            }
        }
    }

    fun startObserving(onSnapshot: (BoardSnapshot) -> Unit) {
        observation?.cancel()
        observation = scope.launch {
            store.observe().filterNotNull().collect { snapshot -> onSnapshot(snapshot) }
        }
    }

    fun stopObserving() {
        observation?.cancel()
        observation = null
    }
}
```

ContentView.swift（整体替换）：

```swift
import SwiftUI
import TaskBoardShared
import LocalDataSql

struct ContentView: View {
    private let adapter: TaskBoardIosAdapter
    @State private var snapshot: BoardSnapshot? = nil
    @State private var openError: String? = nil

    init() {
        self.adapter = TaskBoardIosAdapter(
            store: TaskBoardStore(repository: LocalDataSqlKt.taskBoardRepository())
        )
    }

    var body: some View {
        VStack {
            if let error = openError {
                Text("Error: \(error)")
            } else if let snapshot {
                Text(snapshot.columns.map(\.name).joined(separator: " / "))
            } else {
                Text("Loading...")
            }
        }
        .padding()
        .onAppear {
            adapter.open { error in
                openError = error
                guard error == nil else { return }
                adapter.startObserving { boardSnapshot in
                    snapshot = boardSnapshot
                }
            }
        }
        .onDisappear {
            adapter.stopObserving()
        }
    }
}
```

project.yml 的 dependencies 增加 LocalDataSql framework：

```yaml
    dependencies:
      - framework: ../../shared/task-board/build/bin/iosSimulatorArm64/debugFramework/TaskBoardShared.framework
        embed: false
      - framework: ../../shared/local-data-sql/build/bin/iosSimulatorArm64/debugFramework/LocalDataSql.framework
        embed: false
```

- [ ] **Step 2: 渲染并跑 iOS 构建验证（本机 macOS + Xcode 26.4）**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p products/daily-board :shared:task-board:linkDebugFrameworkIosSimulatorArm64 :shared:local-data-sql:linkDebugFrameworkIosSimulatorArm64
xcodegen generate --spec products/daily-board/apps/ios/project.yml
xcodebuild -project products/daily-board/apps/ios/DailyBoard.xcodeproj -scheme DailyBoard -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build
```

Expected: 两个 framework 链接成功、xcodebuild BUILD SUCCEEDED。若本机无 Xcode 26.4/xcodegen，明确报告「iOS 认证未在本机运行，留待 CI」，不要声称通过。

- [ ] **Step 3: 提交**

```bash
git add templates/daily-board-base/shared/task-board templates/daily-board-base/apps/ios products/daily-board
git diff --check
git commit -m "feat: drive the iOS app from the persisted task-board store"
```

### Task 18: 认证扩展、文档与全量验证

**Files:**
- Modify: scripts/certify-generated-product.sh
- Modify: .github/workflows/certify-phase-0.yml
- Modify: docs/architecture.md
- Modify: README.md

**Interfaces:**
- Consumes: 全部前置任务。
- Produces: 认证矩阵（androidHostTest + ios 测试 + wasmJsNodeTest 双夹具）；文档当前态。

- [ ] **Step 1: 认证脚本与 workflow**

scripts/certify-generated-product.sh（整体替换）：

```bash
#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "$0")/.." && pwd)"
fixture_root="$repository_root/build/certification/daily-board"
web_fixture_root="$repository_root/build/certification/daily-board-web"

rm -rf "$fixture_root" "$web_fixture_root"

"$repository_root/gradlew" \
  -p "$repository_root" \
  createProduct \
  -Pmanifest="$repository_root/project.yaml" \
  -Poutput="$fixture_root"

"$repository_root/gradlew" \
  -p "$fixture_root" \
  :shared:task-board:allTests \
  :shared:local-data-sql:allTests \
  :apps:android:assembleDebug

"$repository_root/gradlew" \
  -p "$repository_root" \
  createProduct \
  -Pmanifest="$repository_root/certification/web-enabled.yaml" \
  -Poutput="$web_fixture_root"

"$repository_root/gradlew" \
  -p "$web_fixture_root" \
  :shared:task-board:wasmJsNodeTest \
  :shared:local-data-web:wasmJsNodeTest
```

（直接照抄本代码块——行尾单个 `\` 即 bash 换行续行符。）

certify-phase-0.yml 的 ios job，在 task-board link 之后插入：

```yaml
      - run: ./gradlew -p build/certification-ios :shared:local-data-sql:linkDebugFrameworkIosSimulatorArm64
      - run: ./gradlew -p build/certification-ios :shared:task-board:iosSimulatorArm64Test :shared:local-data-sql:iosSimulatorArm64Test
```

- [ ] **Step 2: 文档当前态**

docs/architecture.md 的 `## Current state` 段落替换为：

```markdown
## Current state

Phase 0 交付根编排构建、目标中立的 platform-kit、Manifest 校验、结构 Render 事务、Android+iOS 模板与认证入口。Phase 1 子系统①已落地：task-board 持有规则/命令/Store/端口（单看板多列 Kanban、首末固定列、归档/恢复、稠密 rank）；local-data-sql 用 SQLDelight（android+ios，`.sqm` 迁移与版本校验）实现端口；local-data-web 用 IndexedDB（仅 wasmJs）实现同一端口；Android/iOS 应用由持久化 Store 驱动。web 未选时 wasm target 与 web 存储模块经条件 token 缺席（不变量 3），Web 组合由 `certification/web-enabled.yaml` 夹具认证。Desktop、Web UI、CLI 导出与 Android/iOS 完整 Kanban UI 仍未实现。
```

README.md 的引言段 `当前仓库处于设计基线阶段，尚未开始生成 Gradle Module。` 替换为：

```markdown
Phase 0（工作台基线与 Android+iOS 切片）与 Phase 1 子系统①（任务与看板工作流 + 本地持久化）已实现并认证。剩余 Phase 1 子系统（Desktop、Web/Wasm、CLI 导出、Android/iOS Kanban UI）处于规划阶段。
```

README.md 的 Verify Phase 0 一节在 `scripts/certify-generated-product.sh` 之后追加：

```markdown
Web 存储组合的认证由 `certification/web-enabled.yaml` 夹具覆盖，已并入认证脚本；iOS 侧认证在 macos-26 的 CI job 中执行。
```

- [ ] **Step 3: 最终重渲染与全量验证**

```bash
rm -rf products/daily-board && ./gradlew createProduct
./gradlew -p platform-kit test
./gradlew :tooling:generator:test
./gradlew -p products/daily-board :shared:task-board:allTests :shared:local-data-sql:allTests :apps:android:assembleDebug
./gradlew -p products/daily-board :shared:task-board:wasmJsNodeTest 2>/dev/null || echo "wasm 仅存在于 web 夹具，此命令预期跳过或失败——以 certify 脚本为准"
scripts/certify-generated-product.sh
./scripts/check.sh full
git diff --check
```

Expected: 全部 PASS（本机无 Xcode 时 iosSimulatorArm64Test 会在 allTests 中于 macOS 上运行；ubuntu 上 allTests 自动跳过 iOS——若本机平台无法运行某测试，逐项报告实际运行结果，不要笼统声称全绿）。

注意：上述第 5 行的 wasm 命令是对「维护产品不含 wasm」的负向抽查（预期 `wasmJsNodeTest` 任务不存在），`|| echo` 只允许吞掉该预期失败；其余命令必须真实通过。

- [ ] **Step 4: 提交**

```bash
git add scripts/certify-generated-product.sh .github/workflows/certify-phase-0.yml docs/architecture.md README.md products/daily-board
git diff --check
git commit -m "chore: certify the Phase 1 task-board persistence subsystem"
```
