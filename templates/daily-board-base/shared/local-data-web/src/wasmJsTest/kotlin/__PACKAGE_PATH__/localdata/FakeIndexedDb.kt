@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package __PACKAGE_NAME__.localdata

import kotlin.JsFun
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.toJsArray
import kotlin.js.toList

// Kotlin/Wasm forbids non-external classes from implementing external interfaces, so the
// fake cannot literally implement IdbFactory/IdbDatabase/... Instead each fake value is a
// small JS object ("facade") whose IndexedDB-shaped properties forward every call into a
// Kotlin lambda. All fake state and behaviour (stores, transactions, failOnPut, deferred
// events) live in the Kotlin classes below; only the plumbing is JS. The facades are
// structurally identical to real IndexedDB objects, which is all the repository observes.

@JsFun("(fn) => setTimeout(fn, 0)")
external fun deferred(fn: () -> Unit)

@JsFun("(value) => typeof value === 'string'")
external fun isJsString(value: JsAny): Boolean

@JsFun("(openImpl) => ({ open: (name, version) => openImpl(name, version) })")
external fun jsIdbFactory(openImpl: (String, Int) -> JsAny): JsAny

@JsFun("(js) => js")
external fun asIdbFactory(js: JsAny): IdbFactory

@JsFun("""() => { let result = null; return { onupgradeneeded: null, onsuccess: null, onerror: null, get result() { return result }, _resolve(db) { result = db } }; }""")
external fun jsOpenRequest(): JsAny

@JsFun("(req, db) => req._resolve(db)")
external fun resolveOpenResult(req: JsAny, db: JsAny): Unit

@JsFun("(req, version) => { if (req.onupgradeneeded) req.onupgradeneeded({ type: 'upgradeneeded', newVersion: version }) }")
external fun fireOpenUpgrade(req: JsAny, version: Int): Unit

@JsFun("(req) => { if (req.onsuccess) req.onsuccess({ type: 'success' }) }")
external fun fireOpenSuccess(req: JsAny): Unit

@JsFun("(req) => { if (req.onerror) req.onerror({ type: 'error' }) }")
external fun fireOpenError(req: JsAny): Unit

@JsFun("(version, hasStoreImpl, createStoreImpl, transactionImpl, closeImpl) => ({ version: version, objectStoreNames: { contains: (name) => hasStoreImpl(name) }, createObjectStore: (name, options) => createStoreImpl(name, options), transaction: (storeNames, mode) => transactionImpl(storeNames, mode), close: () => closeImpl() })")
external fun jsIdbDatabase(
    version: Int,
    hasStoreImpl: (String) -> Boolean,
    createStoreImpl: (String, JsAny?) -> JsAny,
    transactionImpl: (JsArray<JsAny>, String) -> JsAny,
    closeImpl: () -> Unit,
): JsAny

@JsFun("(objectStoreImpl, abortImpl) => ({ objectStore: (name) => objectStoreImpl(name), abort: () => abortImpl(), oncomplete: null, onabort: null, onerror: null })")
external fun jsIdbTransaction(objectStoreImpl: (String) -> JsAny, abortImpl: () -> Unit): JsAny

@JsFun("(tx) => { if (tx.oncomplete) tx.oncomplete({ type: 'complete' }) }")
external fun fireTransactionComplete(tx: JsAny): Unit

@JsFun("(tx) => { if (tx.onabort) tx.onabort({ type: 'abort' }) }")
external fun fireTransactionAbort(tx: JsAny): Unit

@JsFun("(tx) => { if (tx.onerror) tx.onerror({ type: 'error' }) }")
external fun fireTransactionError(tx: JsAny): Unit

@JsFun("(putImpl, deleteImpl, getAllImpl) => ({ put: (value) => putImpl(value), delete: (key) => deleteImpl(key), getAll: () => getAllImpl() })")
external fun jsIdbObjectStore(putImpl: (JsAny) -> JsAny, deleteImpl: (Double) -> JsAny, getAllImpl: () -> JsAny): JsAny

@JsFun("""() => { let result = null; return { onsuccess: null, onerror: null, get result() { return result }, _resolve(value) { result = value } }; }""")
external fun jsIdbRequest(): JsAny

@JsFun("(req, value) => { req._resolve(value); if (req.onsuccess) req.onsuccess({ type: 'success' }) }")
external fun fireRequestSuccess(req: JsAny, value: JsAny): Unit

class FakeIndexedDb {
    val databases = mutableMapOf<String, FakeDatabase>()
    var failOnPut: Int? = null

    val idbFactory: IdbFactory = asIdbFactory(
        jsIdbFactory(
            { name, version ->
                val request = FakeOpenDbRequest(this, name, version)
                deferred { request.run() }
                request.js
            },
        ),
    )
}

class FakeOpenDbRequest(
    private val factory: FakeIndexedDb,
    private val name: String,
    private val version: Int,
) {
    val js: JsAny = jsOpenRequest()
    private var resolved: FakeDatabase? = null

    fun run() {
        val stored = factory.databases[name]
        if (stored != null && stored.version > version) {
            fireOpenError(js)
            return
        }
        if (stored != null && stored.version == version) {
            resolved = stored
            resolveOpenResult(js, stored.js)
            fireOpenSuccess(js)
            return
        }
        val database = FakeDatabase(version, factory)
        factory.databases[name] = database
        resolved = database
        resolveOpenResult(js, database.js)
        fireOpenUpgrade(js, version)
        fireOpenSuccess(js)
    }
}

class FakeDatabase(val version: Int, val factory: FakeIndexedDb? = null) {
    val stores = mutableMapOf<String, FakeObjectStore>()

    val js: JsAny = jsIdbDatabase(
        version = version,
        hasStoreImpl = { name -> stores.containsKey(name) },
        createStoreImpl = { name, options -> createObjectStore(name).js },
        transactionImpl = { storeNames, mode -> transaction(storeNames, mode).js },
        closeImpl = {},
    )

    fun createObjectStore(name: String): FakeObjectStore {
        val store = FakeObjectStore()
        stores[name] = store
        return store
    }

    fun transaction(storeNames: JsArray<JsAny>, mode: String): FakeTransaction {
        val names = storeNames.toList()
        check(names.isNotEmpty()) { "transaction requires at least one store name" }
        names.forEach { check(isJsString(it)) { "transaction store names must be strings, got $it" } }
        val transaction = FakeTransaction(this, names, mode)
        deferred { transaction.finish() }
        return transaction
    }
}

class FakeObjectStore {
    val rows = mutableMapOf<Double, JsAny>()

    // The repository never uses the store returned by database.createObjectStore;
    // it only reaches stores through transaction.objectStore. Keep a facade that
    // fails loudly if anything tries to use it.
    val js: JsAny = jsIdbObjectStore(
        putImpl = { error("createObjectStore stores are not usable") },
        deleteImpl = { error("createObjectStore stores are not usable") },
        getAllImpl = { error("createObjectStore stores are not usable") },
    )
}

class FakeTransaction(
    private val database: FakeDatabase,
    private val storeNames: List<JsAny>,
    private val mode: String,
) {
    val js: JsAny = jsIdbTransaction(
        objectStoreImpl = { name -> objectStore(name).js },
        abortImpl = { abort() },
    )

    private val writes = mutableListOf<() -> Unit>()
    private val requests = mutableListOf<FakeRequest>()
    private var aborted = false
    private var finished = false

    fun objectStore(name: String): FakeTransactionObjectStore {
        val store = database.stores.getValue(name)
        return FakeTransactionObjectStore(store, this)
    }

    fun abort() {
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
        val factory = database.factory ?: return false
        val countdown = factory.failOnPut ?: return false
        return if (countdown <= 1) {
            factory.failOnPut = null
            true
        } else {
            factory.failOnPut = countdown - 1
            false
        }
    }

    fun finish() {
        if (finished) return
        finished = true
        if (mode != "readwrite") {
            fireTransactionComplete(js)
            return
        }
        if (aborted) {
            fireTransactionAbort(js)
            return
        }
        if (requests.any { it.failed }) {
            // real IndexedDB fires onerror and then onabort for a failed transaction
            fireTransactionError(js)
            fireTransactionAbort(js)
            return
        }
        writes.forEach { it() }
        fireTransactionComplete(js)
    }
}

class FakeTransactionObjectStore(
    private val store: FakeObjectStore,
    private val transaction: FakeTransaction,
) {
    val js: JsAny = jsIdbObjectStore(
        putImpl = { value -> put(value) },
        deleteImpl = { key -> delete(key) },
        getAllImpl = { getAll() },
    )

    fun put(value: JsAny): JsAny {
        val request = FakeRequest()
        if (transaction.failOnPut()) request.fail()
        transaction.recordWrite(request) { store.rows[rowId(value)] = value }
        return request.js
    }

    fun delete(key: Double): JsAny {
        val request = FakeRequest()
        transaction.recordWrite(request) { store.rows.remove(key) }
        return request.js
    }

    fun getAll(): JsAny {
        val request = FakeRequest()
        transaction.recordRead(request) { store.rows.values.toTypedArray().toJsArray() }
        return request.js
    }
}

class FakeRequest {
    val js: JsAny = jsIdbRequest()
    private var resolved: JsAny? = null
    var failed = false
        private set

    fun fail() {
        failed = true
    }

    fun succeed(value: JsAny) {
        resolved = value
        fireRequestSuccess(js, value)
    }
}
