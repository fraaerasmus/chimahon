package chimahon.novel.kosync.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import chimahon.custom.core.describeForUser
import chimahon.custom.ui.ServerLoginFields
import chimahon.novel.kosync.KosyncPreferences
import chimahon.novel.kosync.KosyncSession
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.material.Scaffold
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Settings > Data and storage > KOReader Sync. */
class KosyncScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val preferences = remember { Injekt.get<KosyncPreferences>() }
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(title = "KOReader sync", navigateUp = navigator::pop, scrollBehavior = scrollBehavior)
            },
        ) { contentPadding ->
            PreferenceScreen(
                items = listOf(
                    Preference.PreferenceGroup(
                        title = "KOReader sync",
                        preferenceItems = persistentListOf(
                            Preference.PreferenceItem.SwitchPreference(
                                preference = preferences.enabled(),
                                title = "Enable",
                                subtitle = "Sync reading position with a KOReader sync server",
                            ),
                            Preference.PreferenceItem.SwitchPreference(
                                preference = preferences.mangaEnabled(),
                                title = "Sync manga chapters",
                                subtitle = "Also sync the page of CBZ chapters in the local source and in downloads",
                            ),
                        ),
                    ),
                    Preference.PreferenceGroup(
                        title = "Server",
                        preferenceItems = persistentListOf(
                            Preference.PreferenceItem.CustomPreference(title = "Server") { KosyncLogin(preferences) },
                        ),
                    ),
                    Preference.PreferenceGroup(
                        title = "Behaviour",
                        preferenceItems = persistentListOf(
                            Preference.PreferenceItem.SwitchPreference(
                                preference = preferences.autoSync(),
                                title = "Sync on open and resume",
                                subtitle = "Pull a newer position from the server when a book or chapter opens",
                            ),
                            Preference.PreferenceItem.SwitchPreference(
                                preference = preferences.push(),
                                title = "Push progress",
                                subtitle = "Send this device's position when a book or chapter is closed",
                            ),
                        ),
                    ),
                ),
                contentPadding = contentPadding,
            )
        }
    }
}

/** The login form. A login is saved only after the server has accepted it. */
@Composable
private fun KosyncLogin(preferences: KosyncPreferences) {
    val scope = rememberCoroutineScope()
    val session = remember { Injekt.get<KosyncSession>() }
    var serverUrl by remember { mutableStateOf(preferences.serverUrl().get()) }
    var username by remember { mutableStateOf(preferences.username().get()) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(preferences.credentials() != null) }

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
            val credentials = KosyncPreferences.credentialsFor(serverUrl, username, password)
            runCatching { session.signIn(credentials, register) }
                .onSuccess {
                    password = ""
                    signedIn = true
                    report(if (register) "Account created and signed in." else "Signed in.", false)
                }
                .onFailure { report(it.describeForUser(), true) }
            busy = false
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ServerLoginFields(
            url = serverUrl,
            onUrlChange = { serverUrl = it },
            urlLabel = "Server URL",
            urlPlaceholder = "http://host:7200",
            username = username,
            onUsernameChange = { username = it },
            password = password,
            onPasswordChange = { password = it },
            passwordLabel = if (signedIn) "Password (stored)" else "Password",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { submit(register = false) }, enabled = !busy) {
                Text("Sign in")
            }
            OutlinedButton(onClick = { submit(register = true) }, enabled = !busy) {
                Text("Register")
            }
            if (signedIn) {
                TextButton(
                    onClick = {
                        session.signOut()
                        password = ""
                        signedIn = false
                        report("Signed out. This device no longer syncs.", false)
                    },
                    enabled = !busy,
                ) {
                    Text("Sign out")
                }
            }
        }
        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "The password is stored only as the MD5 hash KOReader authenticates with. " +
                "Books must be imported into this app for their progress to sync, and only " +
                "books imported after this feature was added carry the file identity KOReader " +
                "matches on. Manga chapters sync when they are a single archive file, so a CBZ " +
                "fetched from the same OPDS catalog on a Kobo matches.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
