@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package __PACKAGE_NAME__.taskboard

import kotlin.js.JsExport
import kotlin.js.JsReference
import kotlin.js.toJsReference
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

/**
 * Opaque subscription handle handed to the Web side. JS never inspects the job;
 * it only returns the handle to [dispose] to cancel the subscription.
 */
class WebSubscription internal constructor(internal val job: Job) {
    internal companion object {
        fun create(job: Job): WebSubscription = WebSubscription(job)
    }
}

/**
 * Web subscription surface (Kotlin/Wasm realizes it as top-level @JsExport
 * functions over opaque [JsReference] handles; class-level @JsExport is not
 * supported on the wasmJs target).
 */
class WebTaskBoardAdapter(private val store: TaskBoardStore) {
    private val bridge = SnapshotBridge(store)

    fun subscribe(onSnapshot: (String) -> Unit): WebSubscription =
        WebSubscription.create(bridge.subscribe(onSnapshot, CoroutineScope(Dispatchers.Default)))

    fun dispose(subscription: WebSubscription) {
        subscription.job.cancel()
    }
}

@JsExport
fun createWebTaskBoardAdapter(store: JsReference<TaskBoardStore>): JsReference<WebTaskBoardAdapter> =
    WebTaskBoardAdapter(store.get()).toJsReference()

@JsExport
fun subscribe(adapter: JsReference<WebTaskBoardAdapter>, onSnapshot: (String) -> Unit): JsReference<WebSubscription> =
    adapter.get().subscribe(onSnapshot).toJsReference()

@JsExport
fun dispose(adapter: JsReference<WebTaskBoardAdapter>, subscription: JsReference<WebSubscription>) {
    adapter.get().dispose(subscription.get())
}
