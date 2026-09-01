package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.canopus.chimareader.kosync.KosyncManager
import com.canopus.chimareader.kosync.KosyncSettingsRepository
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.material.Scaffold
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun KosyncSettingsScreen() {
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.currentOrThrow
    val repository = remember { Injekt.get<KosyncSettingsRepository>() }
    val manager = remember { Injekt.get<KosyncManager>() }
    val settings by repository.settings.collectAsState(initial = repository.currentSettings())

    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        serverUrl = repository.currentSettings().serverUrl
        username = repository.currentSettings().username
    }

    fun report(text: String, error: Boolean) {
        message = text
        isError = error
    }

    fun submit(register: Boolean) {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            report("Enter the server, username and password first.", true)
            return
        }
        busy = true
        message = null
        scope.launch {
            val credentials = repository.draftCredentials(serverUrl, username, password)
            val outcome = runCatching {
                if (register) manager.register(credentials)
                manager.testConnection(credentials)
            }
            outcome
                .onSuccess {
                    repository.saveLogin(serverUrl, username, password)
                    password = ""
                    report(if (register) "Account created and signed in." else "Signed in.", false)
                }
                .onFailure { report(it.message ?: "Could not reach the server.", true) }
            busy = false
        }
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = "KOReader sync",
                navigateUp = navigator::pop,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            PreferenceGroupHeader(title = "KOReader sync")
            SwitchPreferenceWidget(
                title = "Enable",
                subtitle = "Sync novel reading position with a KOReader sync server",
                checked = settings.enabled,
                onCheckedChanged = { enabled -> repository.update { it.copy(enabled = enabled) } },
            )

            PreferenceGroupHeader(title = "Server")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://host:7200") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (repository.hasUserKey()) "Password (stored)" else "Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { submit(register = false) }, enabled = !busy) {
                        Text("Sign in")
                    }
                    OutlinedButton(onClick = { submit(register = true) }, enabled = !busy) {
                        Text("Register")
                    }
                }
                message?.let { text ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The password is stored only as the MD5 hash KOReader authenticates with. " +
                        "Books must be imported into this app for their progress to sync, and only " +
                        "books imported after this feature was added carry the file identity KOReader " +
                        "matches on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PreferenceGroupHeader(title = "Behaviour")
            SwitchPreferenceWidget(
                title = "Sync on open and resume",
                subtitle = "Pull a newer position from the server when a book opens",
                checked = settings.autoSyncEnabled,
                onCheckedChanged = { enabled -> repository.update { it.copy(autoSyncEnabled = enabled) } },
            )
            SwitchPreferenceWidget(
                title = "Push progress",
                subtitle = "Send this device's position when a book is closed",
                checked = settings.pushEnabled,
                onCheckedChanged = { enabled -> repository.update { it.copy(pushEnabled = enabled) } },
            )
        }
    }
}
