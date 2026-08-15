package __PACKAGE_NAME__.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import __PACKAGE_NAME__.taskboard.TaskBoard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val snapshot = TaskBoard().createTask("First task")
        setContent {
            MaterialTheme { Text(snapshot.tasks.single().title) }
        }
    }
}
