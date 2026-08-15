package com.seraphim.dailyboard.taskboard

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
