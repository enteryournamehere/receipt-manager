package zip.zaop.paylink

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.openid.appauth.*
import zip.zaop.paylink.database.getDatabase
import zip.zaop.paylink.repository.LidlRepository
import zip.zaop.paylink.ui.theme.PaylinkTheme


class GameActivity : ComponentActivity() {

    var m_AuthStateManager: AuthStateManager? = null
    var m_AuthService: AuthorizationService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PaylinkTheme {
                InitialLoginButton()
            }
        }

        val lidlRepository = LidlRepository(getDatabase(application))
        m_AuthStateManager = AuthStateManager.getInstance(this.applicationContext, lidlRepository)
        if (m_AuthStateManager!!.current.isAuthorized) {
            Log.i("BEEP", "IT WORKS")
            startActivity(Intent(this, BonnetjesActivity::class.java))
            return
        } else {
            Log.i("BEEP", "not yet.")
        }

    }

    private fun doLidlLogin() {
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.lidl.com/connect/authorize"),
            Uri.parse("https://accounts.lidl.com/connect/token")
        )

        val authRequest: AuthorizationRequest = AuthorizationRequest.Builder(
            serviceConfig,
            "LidlPlusNativeClient",
            ResponseTypeValues.CODE,
            Uri.parse("com.lidlplus.app://callback")
        )
            .setScope("openid profile offline_access lpprofile lpapis").build()

        m_AuthService = AuthorizationService(this)
        runBlocking(Dispatchers.IO) {
            m_AuthStateManager!!.replace(AuthState(serviceConfig))
        }

        m_AuthService!!.performAuthorizationRequest(
            authRequest,
            PendingIntent.getActivity(this, 0, Intent(this, BonnetjesActivity::class.java), FLAG_MUTABLE),
            PendingIntent.getActivity(this, 0, Intent(this, AuthCanceledActivity::class.java), FLAG_MUTABLE)
        )

    }

    @Composable
    fun InitialLoginButton() {
        Button(onClick = {
            doLidlLogin()
        }) {
            Text("bbbbbb")
        }
    }

}