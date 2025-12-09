package com.plyr.assistant

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.plyr.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantChatScreen(
    context: Context,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel?
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val assistant = remember { AssistantManager(ctx) }

    var messages by remember { mutableStateOf(AssistantStorage.loadChat(ctx).toMutableList()) }
    var input by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Ensure messages is up to date
        messages = AssistantStorage.loadChat(ctx).toMutableList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Asistente") },
                navigationIcon = {
                    IconButton(onClick = {
                        AssistantStorage.saveChat(ctx, messages)
                        onBack()
                    }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver") }
                }
            )
        }
    ) { inner ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(inner),
            verticalArrangement = Arrangement.SpaceBetween) {

            LazyColumn(modifier = Modifier.weight(1f).padding(8.dp)) {
                items(messages) { msg ->
                    val isUser = msg.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(tonalElevation = 2.dp) {
                            Text(text = msg.text, modifier = Modifier.padding(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (input.isBlank()) return@Button
                    val text = input
                    input = ""
                    messages.add(ChatMessage("user", text))
                    // Save immediately
                    AssistantStorage.saveChat(ctx, messages)

                    // Analyze + perform
                    scope.launch {
                        isProcessing = true
                        val result = withContext(Dispatchers.Default) { assistant.analyze(text) }
                        val vm = playerViewModel ?: run {
                            isProcessing = false
                            return@launch
                        }
                        val reply = withContext(Dispatchers.Default) { assistant.perform(result, vm) }
                        messages.add(ChatMessage("assistant", reply))
                        AssistantStorage.saveChat(ctx, messages)
                        isProcessing = false
                    }
                }) { Text("Enviar") }
            }
        }
    }
}
