package zip.zaop.paylink

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import zip.zaop.paylink.ui.MyApp
import zip.zaop.paylink.ui.theme.PaylinkTheme

class BonnetjesActivity : ComponentActivity() {
    private val TAG = "COMPLETE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "creating activiy")

        setContent {
            PaylinkTheme {
                MyApp(applicationContext)
            }
        }
    }
}
