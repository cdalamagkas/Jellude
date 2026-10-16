package dev.jellude.listenbrainz

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ListenBrainzSettings(manager: ListenBrainzManager, offline: Boolean) {
    val connection by manager.connection.collectAsState()
    val status by manager.status.collectAsState()
    val summary by manager.dao.observeSummary().collectAsState(initial = ListenQueueSummary(0, 0))
    val queueError by manager.dao.observeError().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var connecting by remember { mutableStateOf(false) }
    var disconnecting by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun action(block: suspend () -> Unit) {
        scope.launch {
            busy = true; error = null
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "ListenBrainz operation failed" }
            finally { busy = false }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ListenBrainz", style = MaterialTheme.typography.titleLarge)
            Text("Sends track, artist, album and playback time to ListenBrainz.",
                style = MaterialTheme.typography.bodySmall)
            Text("Listens are public on your ListenBrainz profile. Disable Jellude in other scrobblers to avoid duplicate submissions.",
                style = MaterialTheme.typography.bodySmall)
            if (connection.username.isNotBlank()) {
                ListItem(headlineContent = { Text("Submit listens directly") },
                    supportingContent = { Text("Connected as ${connection.username}") },
                    trailingContent = { Switch(connection.enabled, { manager.setEnabled(it) }, enabled = !busy) })
                Text(if (offline && connection.enabled) "Offline mode: listens are queued until you go online" else status,
                    style = MaterialTheme.typography.bodySmall)
                Text("${summary.total} queued listens · ${summary.failed} needing attention", style = MaterialTheme.typography.bodySmall)
                queueError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row {
                    TextButton(enabled = !busy && connection.enabled && !offline && summary.total > 0,
                        onClick = { action { manager.retry() } }) { Text("Retry uploads") }
                    TextButton(enabled = !busy && summary.total > 0, onClick = { clearing = true }) { Text("Clear queue") }
                }
                Row {
                    TextButton(enabled = !busy && !offline, onClick = { connecting = true }) { Text("Update token") }
                    TextButton(enabled = !busy, onClick = { disconnecting = true }) { Text("Disconnect") }
                }
            } else {
                Text("Not connected", style = MaterialTheme.typography.bodySmall)
                Button(enabled = !busy && !offline, onClick = { connecting = true }) { Text("Connect ListenBrainz") }
                if (offline) Text("Go online to connect. Once connected, offline listens can be queued.", style = MaterialTheme.typography.bodySmall)
            }
            Text("A listen counts after half the track or four minutes of actual playback, whichever comes first.",
                style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (connecting) {
        var token by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { if (!busy) { token = ""; connecting = false } },
            title = { Text("Connect ListenBrainz") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste your ListenBrainz user token. Connecting enables direct submission of future listens.")
                    TextButton(onClick = { uri.openUri("https://listenbrainz.org/settings/") }) { Text("Get your user token") }
                    OutlinedTextField(token, { token = it }, label = { Text("ListenBrainz user token") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), enabled = !busy)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }, confirmButton = {
                TextButton(enabled = token.isNotBlank() && !busy && !offline, onClick = {
                    action { manager.connect(token); token = ""; connecting = false }
                }) { Text("Connect and enable") }
            }, dismissButton = { TextButton(enabled = !busy, onClick = { token = ""; connecting = false }) { Text("Cancel") } })
    }
    if (disconnecting || clearing) AlertDialog(onDismissRequest = { disconnecting = false; clearing = false },
        title = { Text(if (disconnecting) "Disconnect ListenBrainz?" else "Delete queued listens?") },
        text = { Text(if (disconnecting) "Stop tracking, forget the token, and delete queued listens from this device. Already uploaded listens stay on ListenBrainz."
            else "Delete all queued listens from this device. Already uploaded listens stay on ListenBrainz.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val disconnect = disconnecting
            action { if (disconnect) manager.disconnect() else manager.clearQueue(); disconnecting = false; clearing = false }
        }) { Text(if (disconnecting) "Disconnect" else "Delete queue") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { disconnecting = false; clearing = false }) { Text("Cancel") } })
}
