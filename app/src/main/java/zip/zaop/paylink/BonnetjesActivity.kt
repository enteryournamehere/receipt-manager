package zip.zaop.paylink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import zip.zaop.paylink.ui.MyApp
import zip.zaop.paylink.ui.theme.PaylinkTheme

class BonnetjesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PaylinkTheme {
                MyApp(applicationContext)
            }
        }
    }
}
