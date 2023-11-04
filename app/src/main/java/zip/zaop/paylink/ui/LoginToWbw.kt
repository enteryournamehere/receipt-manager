package zip.zaop.paylink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zip.zaop.paylink.ui.theme.PaylinkTheme

@Composable
fun WbwLoginPage(
    onCancel: () -> Unit,
    onKeyboardDone: () -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    username: String,
    password: String,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
//        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(20.dp)
    ) {
        Text(
            text = "Log in to WBW",
            fontSize = 24.sp,
            modifier = modifier.align(Alignment.CenterHorizontally)
        )
        OutlinedTextField(
            value = username,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            onValueChange = onUsernameChanged,
            label = { Text("Email address") },
            keyboardActions = KeyboardActions(
                onDone = { onKeyboardDone() }
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        OutlinedTextField(
            value = password,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            onValueChange = onPasswordChanged,
            label = { Text("Password") },
            keyboardActions = KeyboardActions(
                onDone = { onKeyboardDone() }
            ),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
            ) {
                Text("Cancel", Modifier.padding(7.dp))
            }
            Button(
                onClick = onKeyboardDone,
                modifier = Modifier
            ) {
                Text("Log in", Modifier.padding(7.dp))
            }
        }
    }
}

@Composable
@Preview
fun Preview() {
    PaylinkTheme {
        Surface {
            WbwLoginPage(
                onCancel = {},
                onKeyboardDone = {},
                onUsernameChanged = {},
                onPasswordChanged = {},
                username = "",
                password = "hunter2",
            )
        }
    }
}