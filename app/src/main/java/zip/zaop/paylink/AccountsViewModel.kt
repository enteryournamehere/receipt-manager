package zip.zaop.paylink

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import zip.zaop.paylink.database.getDatabase
import zip.zaop.paylink.repository.LidlRepository
import java.util.concurrent.ExecutorService


class AccountsViewModel(application: Application) : AndroidViewModel(application) {
    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mExecutor: ExecutorService? = null
    private val TAG = "accounts"

    private val lidlRepository = LidlRepository(getDatabase(application))

    // todo find out validity duration of wbw tokens
    // wbw room database i guess?
    // or use same technique as authstatemanager
    // OR make those ALL use a room db with acc info . . . would be nice for adding jumby and appho
    // cuz rn it does not support that


    init {
        val context = application.applicationContext;

        mStateManager = AuthStateManager.getInstance(context, lidlRepository)

        mAuthService = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(DefaultConnectionBuilder.INSTANCE)
                .build()
        )
    }

    fun doLidlLogin() {
        doLogin(
            "LidlPlusNativeClient",
            "com.lidlplus.app://callback",
            "openid profile offline_access lpprofile lpapis",
            AuthorizationServiceConfiguration(
                Uri.parse("https://accounts.lidl.com/connect/authorize"),
                Uri.parse("https://accounts.lidl.com/connect/token")
            )
        )
    }

    // https://login.ah.nl/secure/oauth/authorize?client_id=appie&redirect_uri=appie://login-exit&response_type=code
    //
    // responds with header: location: https://login.ah.nl/login?response_type=code&redirect_uri=appie://login-exit&client_id=appie&fdii=39caeb4d302bc6b1
    // note: fdii is the same as x-fraud-detection-installation-id header
    // note note: it does not give a shit about the fraud detection

    // x-requested-with: com.icemobile.albertheijn
    //
    // .../token
    // x-application: AHWEBSHOP
    // x-fraud-detection-installation-id: 39caeb4d302bc6b1
    fun doAppieLogin() {
        doLogin(
            "appie",
            "appie://login-exit",
            "",
            AuthorizationServiceConfiguration(
                Uri.parse("https://login.ah.nl/secure/oauth/authorize"),
                Uri.parse("https://api.ah.nl/mobile-auth/v1/auth/token")
            )
        )
    }

    private fun doLogin(
        clientId: String,
        callbackUri: String,
        scope: String,
        serviceConfig: AuthorizationServiceConfiguration
    ) {
        val authRequest: AuthorizationRequest = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(callbackUri)
        ).setScope(scope).build()

        runBlocking(Dispatchers.IO) {
            mStateManager!!.replace(AuthState(serviceConfig))
        }

        mAuthService!!.performAuthorizationRequest(
            authRequest,
            PendingIntent.getActivity(
                this.getApplication(),
                0,
                Intent(this.getApplication(), BonnetjesActivity::class.java),
                PendingIntent.FLAG_MUTABLE
            ),
            PendingIntent.getActivity(
                this.getApplication(),
                0,
                Intent(this.getApplication(), AuthCanceledActivity::class.java),
                PendingIntent.FLAG_MUTABLE
            )
        )
    }
}