package zip.zaop.paylink.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zip.zaop.paylink.ui.theme.PaylinkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Alert(title: String, content: String, onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var copyButtonText = "copy message"
    AlertDialog(
        onDismissRequest = { onDismiss() },
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
            )
            {
                Text(title, fontSize = 20.sp)
                Spacer(Modifier.height(10.dp))
                Text(content)
                Row(
                    modifier = Modifier.align(Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(content))
                            copyButtonText = "copied"
                        },
                    ) {
                        Text(copyButtonText)
                    }
                    TextButton(
                        onClick = {
                            onDismiss()
                        },
                    ) {
                        Text("ok")
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun AlertPreview() {
    PaylinkTheme {
        Surface {
            Alert("Error", "Not good.") {}
        }
    }
}
