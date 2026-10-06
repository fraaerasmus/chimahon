package chimahon.custom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * The address, username and password fields of a login to one of the user's own servers. The
 * address and the username are typed without autocorrect or capitals, the password is hidden.
 */
@Composable
fun ServerLoginFields(
    url: String,
    onUrlChange: (String) -> Unit,
    urlLabel: String,
    urlPlaceholder: String,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    usernameLabel: String = "Username",
    passwordLabel: String = "Password",
) {
    val plain = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(urlLabel) },
            placeholder = { Text(urlPlaceholder) },
            singleLine = true,
            keyboardOptions = plain.copy(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text(usernameLabel) },
            singleLine = true,
            keyboardOptions = plain,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(passwordLabel) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = plain.copy(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
