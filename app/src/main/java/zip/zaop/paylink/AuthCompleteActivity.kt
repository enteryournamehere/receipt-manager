package zip.zaop.paylink

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import net.openid.appauth.*
import net.openid.appauth.AuthState.AuthStateAction
import net.openid.appauth.AuthorizationService.TokenResponseCallback
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import okio.IOException
import okio.buffer
import okio.source
import org.chromium.net.CronetEngine
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.nio.charset.Charset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference


const val TICKET_URL = "https://tickets.lidlplus.com/api/v1/NL/list/1"

class AuthCompleteActivity : AppCompatActivity() {
    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private val mUserInfoJson = AtomicReference<JSONObject>()
    private val KEY_USER_INFO = "userInfo"
    private var mExecutor: ExecutorService? = null
    private val TAG = "COMPLETE";
    private var m_CronetEngine: CronetEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "creating activiy")
        mStateManager = AuthStateManager.getInstance(this)

        mAuthService = AuthorizationService(
            this,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(DefaultConnectionBuilder.INSTANCE)
                .build()
        )
        setContentView(R.layout.activity_auth_complete)

        if (savedInstanceState != null) {
            Log.i(TAG, "Saved instance is not null")
            try {
                mUserInfoJson.set(JSONObject(savedInstanceState.getString(KEY_USER_INFO) ?: "Pis}{sed"))
            } catch (ex: JSONException) {
                Log.e(
                    TAG,
                    "Failed to parse saved user info JSON, discarding",
                    ex
                )
            }
        }

        val myBuilder = CronetEngine.Builder(this)
        m_CronetEngine = myBuilder.build()


        val button: Button = findViewById(R.id.button3)
        button.setOnClickListener {
            getBonnetjes()
        }
    }

    @MainThread
    fun getBonnetjes() {
        val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
        mStateManager!!.current.performActionWithFreshTokens(mAuthService!!,
            clientAuthentication,
            AuthStateAction { accessToken, idToken, ex ->
                if (ex != null) {
                    // negotiation for fresh tokens failed, check ex for more details
                    Log.e(TAG, "oh nooooo " + ex.toString())
                    return@AuthStateAction
                }

                Log.i(TAG, "gonna do the thing")

                mExecutor!!.submit {
                    Log.i(TAG, "gonna do the thing pt 2")
                    try {
                        var conn: HttpURLConnection =
                            DefaultConnectionBuilder.INSTANCE.openConnection(
                                Uri.parse(TICKET_URL)
                            )
                        conn.setRequestProperty("Authorization", "Bearer $accessToken")
                        conn.setRequestProperty("App-Version", "999.99.9")
                        conn.setRequestProperty("Operating-System", "iOS")
                        conn.setRequestProperty("App", "com.lidl.eci.lidl.plus")
                        conn.setRequestProperty("Accept-Language", "NL")

                        val response: String = conn.inputStream.source().buffer()
                            .readString(Charset.forName("UTF-8"))

                        val test = JSONObject(response)

                        Log.i(TAG, "RESPONSED!!!! " + response)
                    }catch (ioEx: IOException) {
                        Log.e(TAG, "Network error when querying userinfo endpoint", ioEx);
                    } catch (jsonEx: JSONException) {
                        Log.e(TAG, "Failed to parse userinfo response");
                    }

                }
            })
    }

    override fun onStart() {
        super.onStart()
        if (mExecutor == null || mExecutor!!.isShutdown) {
            mExecutor = Executors.newSingleThreadExecutor()
        }
        if (mStateManager!!.current.isAuthorized) {
            displayAuthorized()
            return
        }

        // the stored AuthState is incomplete, so check if we are currently receiving the result of
        // the authorization flow from the browser.
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        Log.i(TAG, "response: " + response.toString() + " ; ex=" + ex.toString())
        if (response != null || ex != null) {
            Log.i(TAG, "Either is not null.")
            mStateManager!!.updateAfterAuthorization(response, ex)
        }
        if (response != null && response.authorizationCode != null) {
            // authorization code exchange is required
            Log.i(TAG, "Auth code exchange is required.")
            mStateManager!!.updateAfterAuthorization(response, ex)
            exchangeAuthorizationCode(response) // the good stuff :)
        } else if (ex != null) {
            displayNotAuthorized("Authorization flow failed: " + ex.message)
        } else {
            displayNotAuthorized("No authorization state retained - reauthorization required")
        }
    }

    private fun displayAuthorized() {
        var textView: TextView = findViewById<TextView>(R.id.textView);
        textView.text = "AUTHORIZED!!!!!"
    }

    private fun displayNotAuthorized(txt: String) {
        var textView: TextView = findViewById<TextView>(R.id.textView);
        textView.text = "not authorized; " + txt
    }
    private fun displayLoading(txt: String) {
        var textView: TextView = findViewById<TextView>(R.id.textView);
        textView.text = "loeidng/// " + txt
    }


    @MainThread
    private fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
        displayLoading("Exchanging authorization code")
        performTokenRequest(
            authorizationResponse.createTokenExchangeRequest()
        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            handleCodeExchangeResponse(
                tokenResponse,
                authException
            )
        }
    }

    @MainThread
    private fun performTokenRequest(
        request: TokenRequest,
        callback: TokenResponseCallback
    ) {

        val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
        mAuthService!!.performTokenRequest(
            request,
            clientAuthentication,
            callback
        )
    }

    @WorkerThread
    private fun handleAccessTokenResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        mStateManager!!.updateAfterTokenResponse(tokenResponse, authException)
        runOnUiThread { displayAuthorized() }
    }

    @WorkerThread
    private fun handleCodeExchangeResponse(
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        mStateManager!!.updateAfterTokenResponse(tokenResponse, authException)
        if (!mStateManager!!.current.isAuthorized) {
            val message = ("Authorization Code exchange failed"
                    + if (authException != null) authException.error else "")

            // WrongThread inference is incorrect for lambdas
            runOnUiThread { displayNotAuthorized(message) }
        } else {
            runOnUiThread { displayAuthorized() }
        }
    }
}