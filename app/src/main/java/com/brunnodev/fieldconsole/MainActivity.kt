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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.room.Room
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val database by lazy { Room.databaseBuilder(applicationContext, FieldDatabase::class.java, "field-console.db").build() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { FieldConsoleScreen(FieldRepository(database)) } }
    }
}

@Composable
fun FieldConsoleScreen(repository: FieldRepository) {
    val scope = rememberCoroutineScope()
    val tasks = remember { listOf(ChecklistTask("isolation", "Confirmar isolamento e autorização", true), ChecklistTask("seal", "Inspecionar vedação e acoplamento", true), ChecklistTask("readings", "Registrar medições", true), ChecklistTask("evidence", "Conferir referências de evidência", true)) }
    val completed = remember { mutableStateMapOf<String, Boolean>() }
    var asset by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var pending by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { pending = repository.pending().size }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Field Console", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(asset, { asset = it }, label = { Text("Ativo / QR") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(operator, { operator = it }, label = { Text("Operador") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(evidence, { evidence = it }, label = { Text("Referência da evidência / medições") }, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f)) {
            items(tasks) { task -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task.label, modifier = Modifier.weight(1f))
                Checkbox(completed[task.id] == true, { completed[task.id] = it })
            } }
        }
        Button(enabled = !busy && operator.isNotBlank() && tasks.all { completed[it.id] == true }, onClick = {
            busy = true
            scope.launch {
                try {
                    val id = repository.begin(asset, operator, tasks)
                    for (task in tasks) repository.completeTask(id, task.id, evidence)
                    repository.submit(id)
                    completed.clear()
                    pending = repository.pending().size
                    status = "Inspeção $id salva no dispositivo."
                } catch (error: Exception) { status = error.message ?: "Falha ao salvar" }
                finally { busy = false }
            }
        }) { Text("Salvar inspeção") }
        OutlinedTextField(token, { token = it }, label = { Text("Token da sessão Supabase") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && token.isNotBlank() && pending > 0, onClick = {
            busy = true
            scope.launch {
                try {
                    val delivery = ArchiveDelivery(token)
                    for (row in repository.pending()) {
                        check(delivery.send(row.id, row.eventType, row.payload)) { "Servidor não confirmou o registro" }
                        repository.acknowledge(listOf(row.id))
                    }
                    status = "Eventos sincronizados."
                } catch (error: Exception) { status = error.message ?: "Falha de conexão" }
                finally { pending = repository.pending().size; busy = false }
            }
        }) { Text("Sincronizar $pending eventos") }
        Text(status)
    }
}
