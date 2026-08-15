@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package __PACKAGE_NAME__.localdata

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.toJsArray
import kotlin.js.toList
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
        val transaction = db.transaction(storeNamesArray(COLUMNS_STORE, TASKS_STORE), "readwrite")
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
        val request = db.transaction(storeNamesArray(COLUMNS_STORE), "readonly").objectStore(COLUMNS_STORE).getAll()
        val rows = awaitSuccess(request)
        return toJsArray(rows).toList().map { ColumnRow(rowId(it).toLong(), rowName(it), rowRank(it).toInt()) }
    }

    private suspend fun readTasks(db: IdbDatabase): List<TaskItem> {
        val request = db.transaction(storeNamesArray(TASKS_STORE), "readonly").objectStore(TASKS_STORE).getAll()
        val rows = awaitSuccess(request)
        return toJsArray(rows).toList().map { row ->
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
