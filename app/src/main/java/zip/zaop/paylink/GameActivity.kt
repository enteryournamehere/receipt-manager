package zip.zaop.paylink

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import net.openid.appauth.*


class GameActivity : AppCompatActivity() {

    var m_AuthStateManager: AuthStateManager? = null;
    var m_AuthService: AuthorizationService? = null;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)


        m_AuthStateManager = AuthStateManager.getInstance(this)
        if (m_AuthStateManager!!.current.isAuthorized) {
            Log.i("BEEP", "IT WORKS");
            startActivity(Intent(this, AuthCompleteActivity::class.java))
            return;
        } else {
            Log.i("BEEP", "not yet.")
        }

        val button: Button = findViewById(R.id.button2)
        button.setOnClickListener {
            doLidlLogin()
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
        m_AuthStateManager!!.replace(AuthState(serviceConfig))

        m_AuthService!!.performAuthorizationRequest(
            authRequest,
            PendingIntent.getActivity(this, 0, Intent(this, AuthCompleteActivity::class.java), FLAG_MUTABLE),
            PendingIntent.getActivity(this, 0, Intent(this, AuthCanceledActivity::class.java), FLAG_MUTABLE)
        )

    }

}