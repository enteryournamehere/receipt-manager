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

//        m_AuthService = AuthorizationService(this)
        runBlocking(Dispatchers.IO) {
            mStateManager!!.replace(AuthState(serviceConfig))
        }

        mAuthService!!.performAuthorizationRequest(
            authRequest,
            PendingIntent.getActivity(this.getApplication(), 0, Intent(this.getApplication(), BonnetjesActivity::class.java),
                PendingIntent.FLAG_MUTABLE
            ),
            PendingIntent.getActivity(this.getApplication(), 0, Intent(this.getApplication(), AuthCanceledActivity::class.java),
                PendingIntent.FLAG_MUTABLE
            )
        )

    }
}