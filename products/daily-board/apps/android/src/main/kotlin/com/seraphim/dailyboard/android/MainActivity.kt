package com.seraphim.dailyboard.android

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
