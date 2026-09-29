package com.brunnodev.fieldconsole

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { FieldConsoleScreen() } }
    }
}

@Composable
fun FieldConsoleScreen() {
    val tasks = remember {
        mutableStateListOf(
            "Confirm isolation and permit" to false,
            "Inspect pump seal and coupling" to false,
            "Record pressure and vibration" to false,
            "Attach equipment photo reference" to false
        )
    }
    var assetCode by remember { mutableStateOf("RF:PUMP-204") }
    var status by remember { mutableStateOf("Offline-ready · 0 pending events") }
    val completed = tasks.count { it.second }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FIELD CONSOLE", style = MaterialTheme.typography.headlineMedium)
        Text("Device: offline · local queue available")
        OutlinedTextField(assetCode, { assetCode = it }, label = { Text("Equipment QR / asset tag") }, modifier = Modifier.fillMaxWidth())
        Text("Inspection progress: $completed of ${tasks.size}")
        LazyColumn(Modifier.weight(1f)) {
            items(tasks.indices.toList()) { index ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tasks[index].first, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    Checkbox(checked = tasks[index].second, onCheckedChange = { checked -> tasks[index] = tasks[index].first to checked })
                }
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = completed == tasks.size,
            onClick = {
                status = try {
                    "Queued locally: ${parseAssetTag(assetCode)}"
                } catch (_: IllegalArgumentException) {
                    "Invalid equipment tag"
                }
            }
        ) { Text("Submit inspection") }
        Text(status)
    }
}
