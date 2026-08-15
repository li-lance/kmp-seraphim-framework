@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package __PACKAGE_NAME__.localdata

import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.JsFun

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
    fun transaction(storeNames: JsArray<JsAny>, mode: String): IdbTransaction
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

@JsFun("(...names) => names")
external fun storeNamesArray(vararg names: String): JsArray<JsAny>

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
external fun toJsArray(a: JsAny): JsArray<JsAny>
