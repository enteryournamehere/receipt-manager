package zip.zaop.paylink

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
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
import zip.zaop.paylink.database.getDatabase
import zip.zaop.paylink.domain.Receipt
import zip.zaop.paylink.network.LidlApi
import zip.zaop.paylink.network.NetworkLidlReceiptItem
import zip.zaop.paylink.repository.LidlRepository
import okio.IOException
import retrofit2.HttpException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BonnetjesViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UiState(mutableStateListOf()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mExecutor: ExecutorService? = null
    private val TAG = "Biewmoel"

    private val lidlRepository = LidlRepository(getDatabase(application))

    val receipts = lidlRepository.receipts

    fun getBonnetjes() {
        _uiState.value = UiState(status = "loading...")
        val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
        mStateManager!!.current.performActionWithFreshTokens(mAuthService!!, clientAuthentication, this::getBonnetjes)
    }

    @MainThread
    fun getBonnetjes(accessToken: String?, _idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            // negotiation for fresh tokens failed, check ex for more details
            Log.e(TAG, "fresh token problem: $ex")
            return
        }

        viewModelScope.launch {
            try {
                lidlRepository.refreshReceipts(accessToken!!)
            }
            catch (networkError: IOException) {
                Log.e("Error", "NETWORK ERROR!")
            }
        }
    }

    fun fetchReceiptInfo(receipt: Receipt) {
        _uiState.value = _uiState.value.copy(status = "loading bonnetje")
        val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
        mStateManager!!.current.performActionWithFreshTokens(mAuthService!!, clientAuthentication
        ) { accessToken: String?, _idToken: String?, ex: AuthorizationException? ->
            fetchReceiptInfo(accessToken, _idToken, ex, receipt)
        }
    }

    @MainThread
    fun fetchReceiptInfo(accessToken: String?, _idToken: String?, ex: AuthorizationException?, receipt: Receipt) {
        if (ex != null) {
            // negotiation for fresh tokens failed, check ex for more details
            Log.e(TAG, "fresh token problem: $ex")
            return
        }

        viewModelScope.launch {
            try {
                lidlRepository.fetchReceipt(accessToken!!, receipt)
            }
            catch (networkError: IOException) {
                Log.e("Error", "NETWORK ERROR!")
            }
        }

        /*
        viewModelScope.launch {
            try {
                val response = LidlApi.retrofitService.getReceipt(id, "Bearer $accessToken")
                var found = uiState.value.bonnetjes.find { state -> state.id == id }
                var index = uiState.value.bonnetjes.indexOf(found)
                _uiState.value.bonnetjes[index] = found!!.copy(items = response.itemsLine)
                _uiState.value = UiState(bonnetjes = _uiState.value.bonnetjes
                , status = "loaded successfully")
            }
            catch (e: HttpException) {
                _uiState.value = _uiState.value.copy(status = "epic fail: ${e.message}")
            }
        }*/
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


    private fun displayNotAuthorized(eep: String) {
        _uiState.value = _uiState.value.copy(status = "not authorized... $eep")
    }
    private fun displayLoading(eep: String) {
        _uiState.value = _uiState.value.copy(status = "loading... $eep")
    }
    private fun displayAuthorized(eep: String) {
        _uiState.value = _uiState.value.copy(status = "authorized! $eep")
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
            displayAuthorized(":D")
        }
    }
}

data class UiState(
    val bonnetjes: MutableList<BonnetjeUiState> = mutableStateListOf(),
    val status: String = "init",
)

data class BonnetjeUiState (
    val id: String,
    val date: String,
    val amount: String,
    val items: List<NetworkLidlReceiptItem>
)