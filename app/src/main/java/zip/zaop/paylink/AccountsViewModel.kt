package zip.zaop.paylink

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
import retrofit2.HttpException
import zip.zaop.paylink.database.LinkablePlatform
import zip.zaop.paylink.database.getDatabase
import zip.zaop.paylink.network.LoginErrorResponse
import zip.zaop.paylink.network.LoginRequest
import zip.zaop.paylink.network.User
import zip.zaop.paylink.network.WbwApi
import zip.zaop.paylink.repository.ReceiptRepository
import zip.zaop.paylink.util.ErrorResponse
import zip.zaop.paylink.util.parseHttpException

data class AccountsScreenUiState(
    val connections: Map<LinkablePlatform, Boolean> = mapOf(),
    val wbwLoginState: WbwLoginState,
    val alertInfo: AlertInfo? = null,
)

data class WbwLoginState(
    val visible: Boolean = false,
    val username: String = "",
    val password: String = "",
)

class AccountsViewModel(private val application: Application) : AndroidViewModel(application) {
    private var mAuthServices: MutableMap<LinkablePlatform, AuthorizationService> = mutableMapOf()
    private var mStateManagers: MutableMap<LinkablePlatform, AuthStateManager> = mutableMapOf()

    private val receiptRepository = ReceiptRepository(getDatabase(application), application)

    private val auths = receiptRepository.auth
    @OptIn(ExperimentalCoroutinesApi::class)
    private val wbwAuthState: Flow<String?> = auths.mapLatest { it[LinkablePlatform.WBW] }

    private val _uiState = MutableStateFlow(AccountsScreenUiState(wbwLoginState = WbwLoginState()))

    val uiState = _uiState.combine(wbwAuthState) { uistate, wbwstate ->
        uistate.copy(connections = uistate.connections.plus(LinkablePlatform.WBW to (wbwstate != null)))
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AccountsScreenUiState(wbwLoginState = WbwLoginState())
    )

    fun closeAlertDialog() {
        _uiState.value = _uiState.value.copy(alertInfo = null)
    }

    init {
        val context = application.applicationContext

        mStateManagers[LinkablePlatform.LIDL] =
            AuthStateManager.getInstance(context, receiptRepository, LinkablePlatform.LIDL)
        mStateManagers[LinkablePlatform.APPIE] =
            AuthStateManager.getInstance(context, receiptRepository, LinkablePlatform.APPIE)
        mStateManagers[LinkablePlatform.JUMBO] =
            AuthStateManager.getInstance(context, receiptRepository, LinkablePlatform.JUMBO)

        _uiState.value =
            AccountsScreenUiState(
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
            receiptRepository.refreshWbwLists()
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

    fun hideWbwLoginScreen() {
        _uiState.value =
            _uiState.value.copy(wbwLoginState = _uiState.value.wbwLoginState.copy(visible = false))
    }

    fun submitWbwLogin() {
        viewModelScope.launch {
            try {
                @Suppress("UNUSED_VARIABLE") val response =
                    WbwApi.getRetrofitService(application).logIn(
                        LoginRequest(
                            User(
                                _uiState.value.wbwLoginState.username,
                                _uiState.value.wbwLoginState.password
                            )
                    )
                )

                // go back
                _uiState.value = _uiState.value.copy(wbwLoginState = WbwLoginState())
                // save ??? (actually stored in cookie jar)
                receiptRepository.updateAuthState(LinkablePlatform.WBW, "yeah")

                getWbwListStuff()
            } catch (e: HttpException) {
                when (val errorResponse = parseHttpException<LoginErrorResponse>(e)) {
                    is ErrorResponse.Text -> {
                        _uiState.value = _uiState.value.copy(
                            alertInfo = AlertInfo(true, errorResponse.data)
                        )
                    }

                    is ErrorResponse.Custom<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        _uiState.value = _uiState.value.copy(
                            alertInfo = AlertInfo(
                                true,
                                (errorResponse as ErrorResponse.Custom<LoginErrorResponse>).data.message
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    alertInfo = AlertInfo(
                        true,
                        e.message?.replace("zip.zaop.", "") ?: "Unknown error occured."
                    )
                )
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
        doLogin(
            LinkablePlatform.JUMBO,
            "ZVa0cW0LadbDHINgrBLuEAp5amVBKQh1",
            "jumboextras://home",
            "openid offline_access",
            AuthorizationServiceConfiguration(
                Uri.parse("https://auth.jumbo.com/authorize"),
                Uri.parse("https://auth.jumbo.com/oauth/token")
            ),
            mapOf(
                "audience" to "https://jumbo.com/loyalty",
                "ext-login_uri" to "https://loyalty-app.jumbo.com/user/account",
                "ext-password_reset_uri" to "https://loyalty-app.jumbo.com/user/forgot-password",
                "ext-register_uri" to "https://loyalty-app.jumbo.com/user/signup/email",
                "auth0Client" to "eyJuYW1lIjoiYXV0aDAtc3BhLWpzIiwidmVyc2lvbiI6IjIuMC4zIn0="
            ),
            prompt = "login",
            responseMode = "query"
        )
    }

    fun doAppieLogin() {
        doLogin(
            LinkablePlatform.APPIE,
            "appie",
            "appie://login-exit",
            "",
            AuthorizationServiceConfiguration(
                Uri.parse("https://login.ah.nl/secure/oauth/authorize"),
                Uri.parse("https://api.ah.nl/mobile-auth/v1/auth/token")
            ),
            useJsonVariant = true
        )
    }

    private fun doLogin(
        platform: LinkablePlatform,
        clientId: String,
        callbackUri: String,
        scope: String,
        serviceConfig: AuthorizationServiceConfiguration,
        additionalParameters: Map<String, String> = emptyMap(),
        prompt: String? = null,
        responseMode: String? = null,
        useJsonVariant: Boolean = false,
    ) {
        var authRequestBuilder = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(callbackUri),
            useJsonVariant
        ).setScope(scope)

        if (additionalParameters.isNotEmpty()) {
            authRequestBuilder = authRequestBuilder.setAdditionalParameters(additionalParameters)
        }
        if (prompt != null) authRequestBuilder = authRequestBuilder.setPrompt(prompt)
        if (responseMode != null) authRequestBuilder = authRequestBuilder.setResponseMode(prompt)

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
                Intent(this.getApplication(), BonnetjesActivity::class.java),
                PendingIntent.FLAG_MUTABLE
            )
        )
    }
}