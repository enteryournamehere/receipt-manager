package zip.zaop.paylink

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import zip.zaop.paylink.database.LinkablePlatform
import zip.zaop.paylink.database.getDatabase
import zip.zaop.paylink.network.LoginRequest
import zip.zaop.paylink.network.User
import zip.zaop.paylink.network.WbwApi
import zip.zaop.paylink.network.asDatabaseModel
import zip.zaop.paylink.repository.ReceiptRepository
import java.util.concurrent.ExecutorService

data class ConnectedAccounts(
    val connections: Map<LinkablePlatform, Boolean> = mapOf(),
    val wbwLoginState: WbwLoginState,
)

data class WbwLoginState(
    val visible: Boolean = false,
    val username: String = "",
    val password: String = "",
)

class AccountsViewModel(private val application: Application) : AndroidViewModel(application) {
    private var mAuthServices: MutableMap<LinkablePlatform, AuthorizationService> = mutableMapOf();
    private var mStateManagers: MutableMap<LinkablePlatform, AuthStateManager> = mutableMapOf();
    private var mExecutor: ExecutorService? = null
    private val TAG = "accounts"

    private val receiptRepository = ReceiptRepository(getDatabase(application), application)

    val auths = receiptRepository.auth
    val wbwAuthState: Flow<String?> = auths.mapLatest { it -> it.get(LinkablePlatform.WBW) }

    private val _uiState = MutableStateFlow(ConnectedAccounts(wbwLoginState = WbwLoginState()))

    val uiState = _uiState.combine(wbwAuthState) { uistate, wbwstate ->
        uistate.copy(connections = uistate.connections.plus(LinkablePlatform.WBW to (wbwstate != null)))
    }.stateIn(viewModelScope, SharingStarted.Eagerly,ConnectedAccounts(wbwLoginState = WbwLoginState()) )

    init {
        val context = application.applicationContext;

        mStateManagers[LinkablePlatform.LIDL] =
            AuthStateManager.getInstance(context, receiptRepository, LinkablePlatform.LIDL)
        mStateManagers[LinkablePlatform.APPIE] =
            AuthStateManager.getInstance(context, receiptRepository, LinkablePlatform.APPIE)
        mStateManagers[LinkablePlatform.JUMBO] =
            AuthStateManager.getInstance(context, receiptRepository, LinkablePlatform.JUMBO)

        _uiState.value =
            ConnectedAccounts(
                connections = mapOf(
                    LinkablePlatform.LIDL to mStateManagers[LinkablePlatform.LIDL]!!.current.isAuthorized,
                    LinkablePlatform.APPIE to mStateManagers[LinkablePlatform.APPIE]!!.current.isAuthorized,
                    LinkablePlatform.JUMBO to mStateManagers[LinkablePlatform.JUMBO]!!.current.isAuthorized,
                ),
                wbwLoginState = WbwLoginState()
            )

        mAuthServices[LinkablePlatform.LIDL] = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(DefaultConnectionBuilder.INSTANCE)
                .build()
        )

        mAuthServices[LinkablePlatform.APPIE] = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(DefaultConnectionBuilder.INSTANCE)
                .build()
        )

        mAuthServices[LinkablePlatform.JUMBO] = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(DefaultConnectionBuilder.INSTANCE)
                .build()
        )
    }

    fun startWbwLogin() {
        _uiState.value =
            _uiState.value.copy(wbwLoginState = _uiState.value.wbwLoginState.copy(visible = true))
    }

    fun getWbwListStuff() {
        viewModelScope.launch {
            val lists = WbwApi.getRetrofitService(application).getLists()

            receiptRepository.insertWbwLists(lists.asDatabaseModel())

            val balances = WbwApi.getRetrofitService(application).getBalances()

            for (item in balances.data) {
                val balance = item.balance
                receiptRepository.setOurMemberId(balance.list.id, balance.member.id)
            }
        }
    }

    fun updateWbwUsername(text: String) {
        _uiState.value =
            _uiState.value.copy(wbwLoginState = _uiState.value.wbwLoginState.copy(username = text))
    }

    fun updateWbwPassword(text: String) {
        _uiState.value =
            _uiState.value.copy(wbwLoginState = _uiState.value.wbwLoginState.copy(password = text))
    }

    fun submitWbwLogin() {
        viewModelScope.launch {
            try {
                val response = WbwApi.getRetrofitService(application).logIn(
                    LoginRequest(
                        User(
                            _uiState.value.wbwLoginState.username,
                            _uiState.value.wbwLoginState.password
                        )
                    )
                )

                if (response.errors == null) {
                    // :)
                    // go back
                    _uiState.value = _uiState.value.copy(wbwLoginState = WbwLoginState())
                    // save ??? (actually stored in cookie jar)
                    receiptRepository.updateAuthState(LinkablePlatform.WBW, "yeah")

                    getWbwListStuff()
                } else {
                    // :(
                }
            } catch (e: Exception) {
                Log.e("hi", e.toString())
            }
        }
    }

    fun doLidlLogin() {
        doLogin(
            LinkablePlatform.LIDL,
            "LidlPlusNativeClient",
            "com.lidlplus.app://callback",
            "openid profile offline_access lpprofile lpapis",
            AuthorizationServiceConfiguration(
                Uri.parse("https://accounts.lidl.com/connect/authorize"),
                Uri.parse("https://accounts.lidl.com/connect/token")
            )
        )
    }

    fun doJumboLogin() {
//        if (_uiState.value.connections[LinkablePlatform.JUMBO]!!) {
//            viewModelScope.launch {
//                receiptRepository.deleteAuthState(LinkablePlatform.JUMBO)
//            }
//        } else {
        doLogin(
            LinkablePlatform.JUMBO,
            "ZVa0cW0LadbDHINgrBLuEAp5amVBKQh1",
            "jumboextras://home",
            "openid offline_access",
            AuthorizationServiceConfiguration(
                Uri.parse("https://auth.jumbo.com/authorize"),
                Uri.parse("https://auth.jumbo.com/oauth/token")
            )
        )
//        }
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
            LinkablePlatform.APPIE,
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
        platform: LinkablePlatform,
        clientId: String,
        callbackUri: String,
        scope: String,
        serviceConfig: AuthorizationServiceConfiguration
    ) {
        var authRequestBuilder = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(callbackUri),
            clientId == "appie"
        ).setScope(scope)

        if (platform == LinkablePlatform.JUMBO) {
            authRequestBuilder = authRequestBuilder.setAdditionalParameters(
                mapOf(
                    "audience" to "https://jumbo.com/loyalty",
                    "ext-login_uri" to "https://loyalty-app.jumbo.com/user/account",
                    "ext-password_reset_uri" to "https://loyalty-app.jumbo.com/user/forgot-password",
                    "ext-register_uri" to "https://loyalty-app.jumbo.com/user/signup/email",
                    "auth0Client" to "eyJuYW1lIjoiYXV0aDAtc3BhLWpzIiwidmVyc2lvbiI6IjIuMC4zIn0="
                )
            ).setPrompt("login").setResponseMode("query")
        }

        val authRequest = authRequestBuilder.build()

        runBlocking(Dispatchers.IO) {
            mStateManagers[platform]!!.replace(AuthState(serviceConfig))
        }

        mAuthServices[platform]!!.performAuthorizationRequest(
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