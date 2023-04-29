package zip.zaop.paylink

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import zip.zaop.paylink.network.LidlApi
import okio.IOException
import retrofit2.HttpException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BonnetjesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState(listOf()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mExecutor: ExecutorService? = null
    private val TAG = "Biewmoel"

    fun getBonnetjes() {
        _uiState.value = UiState(status = "loading...")
        val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
        mStateManager!!.current.performActionWithFreshTokens(mAuthService!!, clientAuthentication, this::getBonnetjes2)
    }

    @MainThread
    fun getBonnetjes2(accessToken: String?, _idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            // negotiation for fresh tokens failed, check ex for more details
            Log.e(TAG, "fresh token problem: $ex")
            return
        }

        viewModelScope.launch {
            try {
                val response = LidlApi.retrofitService.getReceipts(1, "Bearer $accessToken")
                val bonnetjes = response.records
                _uiState.value = UiState(bonnetjes = bonnetjes.map {
                    BonnetjeUiState(
                        date = convertDateTimeString(it.date),
                        amount = it.totalAmount,
                        items = it.articlesCount
                    )
                }, status = "loaded successfully")
            }
            catch (e: HttpException) {
                _uiState.value = _uiState.value.copy(status = "epic fail: ${e.message}")
            }
        }
    }

    fun create(context: Context) {
        mStateManager = AuthStateManager.getInstance(context)

        mAuthService = AuthorizationService(
            context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(DefaultConnectionBuilder.INSTANCE)
                .build()
        )
    }

    fun start(intent: Intent) {
        if (mExecutor == null || mExecutor!!.isShutdown) {
            mExecutor = Executors.newSingleThreadExecutor()
        }
        if (mStateManager!!.current.isAuthorized) {
            _uiState.value = _uiState.value.copy(status = "authorized")
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
        if (response?.authorizationCode != null) {
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


    fun convertDateTimeString(dateTimeString: String): String {
        val offsetDateTime = OffsetDateTime.parse(dateTimeString)
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'at' HH:mm")
        return offsetDateTime.format(formatter)
    }

    private fun displayNotAuthorized(eep: String) {
        _uiState.value = _uiState.value.copy(status = "not authd... $eep")
    }
    private fun displayLoading(eep: String) {
        _uiState.value = _uiState.value.copy(status = "loading... $eep")
    }
    private fun displayAuthorized(eep: String) {
        _uiState.value = _uiState.value.copy(status = "authd... $eep")
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
        callback: AuthorizationService.TokenResponseCallback
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
            displayNotAuthorized(message)
        } else {
            displayAuthorized("jel; yeah")
        }
    }
}

data class UiState(
    val bonnetjes: List<BonnetjeUiState> = listOf(),
    val status: String = "init",
)

data class BonnetjeUiState (
    val date: String,
    val amount: String,
    val items: Int,
)