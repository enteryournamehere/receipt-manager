package zip.zaop.paylink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import net.openid.appauth.*
import net.openid.appauth.AuthorizationService.TokenResponseCallback


class GameActivity : AppCompatActivity() {

    var m_AuthStateManager: AuthStateManager? = null;
    var m_AuthService: AuthorizationService? = null;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)


        m_AuthStateManager = AuthStateManager.getInstance(this)
        if (m_AuthStateManager!!.current.isAuthorized) {
            Log.i("BEEP", "IT WORKS");
            return;
        }
        else {
            Log.i("BEEP", "not yet.")
        }

        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.lidl.com/connect/authorize"),
            Uri.parse("https://accounts.lidl.com/connect/token")
        )

        val authRequest: AuthorizationRequest = AuthorizationRequest.Builder(
            serviceConfig,  // the authorization service configuration
            "LidlPlusNativeClient",  // the client ID, typically pre-registered and static
            ResponseTypeValues.CODE,  // the response_type value: we want a code
            Uri.parse("com.lidlplus.app://callback")
        ) // the redirect URI to which the auth response is sent
            .setScope("openid profile offline_access lpprofile lpapis").build()

        m_AuthService = AuthorizationService(this)
        val authIntent = m_AuthService!!.getAuthorizationRequestIntent(authRequest)
        m_AuthStateManager!!.replace(AuthState(serviceConfig))
        startActivityForResult(authIntent, 100) // RC_AUTH

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) { // RC_AUTH
            val resp = AuthorizationResponse.fromIntent(data!!)
            val ex = AuthorizationException.fromIntent(data)
            m_AuthStateManager!!.updateAfterAuthorization(resp, ex)
            Log.v("BEEP", resp.toString() + " - " + ex.toString())
            val clientAuth: ClientAuthentication = ClientSecretBasic("secret")
            m_AuthService!!.performTokenRequest(
                resp!!.createTokenExchangeRequest(),
                clientAuth,
                TokenResponseCallback { resp, ex ->
                    Log.v("BEEP", "TOKENED" + resp.toString() + " - " + ex.toString())
                    if (resp != null) {
                        m_AuthStateManager!!.updateAfterTokenResponse(resp, ex)
                    } else {
                        // authorization failed, check ex for more details
                    }
                })
        } else {
            Log.v("BEEP", "request code was $requestCode")
        }
    }
}