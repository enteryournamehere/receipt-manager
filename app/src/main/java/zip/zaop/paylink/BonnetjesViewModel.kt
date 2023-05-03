package zip.zaop.paylink

//import androidx.core.content.Context.getSystemService
import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
import zip.zaop.paylink.domain.ReceiptItem
import zip.zaop.paylink.network.NetworkLidlReceiptItem
import zip.zaop.paylink.repository.LidlRepository
import zip.zaop.paylink.util.convertCentsToString
import okio.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


data class UiState(
//    val bonnetjes: MutableList<BonnetjeUiState> = mutableStateListOf(),
    val status: String = "init",
    val selectedCount: Int = 0,
    val selectedAmount: Int = 0,
)

data class BonnetjeUiState(
    val id: String,
    val date: String,
    val amount: String,
    val items: List<NetworkLidlReceiptItem>
)

data class FullInfo(
    val receipt: Receipt,
    val selectedItems: Set<Int>,
)

data class TestState(
    val selectedItems: Map<Int, List<Int>>
)

class BonnetjesViewModel(application: Application) : AndroidViewModel(application) {
    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null
    private var mExecutor: ExecutorService? = null
    private val TAG = "binomiaalverdeling"

    private val lidlRepository = LidlRepository(getDatabase(application))

//    val receiptsPlusOld =
//        receipts.map { receipts -> receipts.map { receipt -> FullInfo(receipt, selectionState.getOrDefault(receipt, setOf())) } }

//    private var selectionState: MutableMap<Receipt, MutableSet<Int>> = mutableMapOf()


    fun getBonnetjes() {
        _uiState.value = _uiState.value.copy(status = "downloading...")
        val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
        mStateManager!!.current.performActionWithFreshTokens(
            mAuthService!!,
            clientAuthentication,
            this::getBonnetjes
        )
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val receipts: Flow<List<Receipt>> = lidlRepository.receipts

    private val _selectionStateFlow = MutableStateFlow<Map<Int, Set<Int>>>(emptyMap())
    private val selectionStateFlow = _selectionStateFlow.asStateFlow()

    // order doesn't make a difference
    val receiptsPlus: Flow<List<FullInfo>> =
        receipts.combine(selectionStateFlow) { receipts, selectionState ->
            receipts.map { receipt ->
                FullInfo(
                    receipt,
                    selectionState.getOrDefault(receipt.id, emptySet())
                )
            }
        }

    // if the user selects an item, mark it as selected ,so that the ui updates
    fun select(receipt: Receipt, item: ReceiptItem, state: Boolean) {
        val updatedSet = (_selectionStateFlow.value[receipt.id] ?: emptySet()).toMutableSet()
        if (state) {
            val added = updatedSet.add(item.indexInsideReceipt)
            if (added)
                _uiState.value = _uiState.value.copy(
                    selectedCount = _uiState.value.selectedCount.inc(),
                    selectedAmount = _uiState.value.selectedAmount + item.totalPrice
                )
        } else {
            val removed = updatedSet.remove(item.indexInsideReceipt)
            if (removed) _uiState.value = _uiState.value.copy(
                selectedCount = _uiState.value.selectedCount.dec(),
                selectedAmount = _uiState.value.selectedAmount - item.totalPrice
            )
        }
        _selectionStateFlow.value = _selectionStateFlow.value + (receipt.id to updatedSet)
    }

    fun getSelectedAmountToCopy(): String {
        val valueToCopy = convertCentsToString(_uiState.value.selectedAmount, ".", false)
        _selectionStateFlow.value = emptyMap()
        _uiState.value = _uiState.value.copy(selectedAmount = 0, selectedCount = 0)
        return valueToCopy
    }

    @MainThread
    fun getBonnetjes(accessToken: String?, _idToken: String?, ex: AuthorizationException?) {
        if (ex != null) {
            // negotiation for fresh tokens failed, check ex for more details
            Log.e(TAG, "fresh token problem: $ex")
            _uiState.value =
                _uiState.value.copy(status = "could not get fresh token (network error?)")
            return
        }

        viewModelScope.launch {
            try {
                lidlRepository.refreshReceipts(accessToken!!)
                _uiState.value = _uiState.value.copy(status = "done")
            } catch (networkError: IOException) {
                Log.e("Error", "NETWORK ERROR!")
                _uiState.value = _uiState.value.copy(status = "network error")
            }
        }
    }

    fun fetchReceiptInfo(receipt: Receipt) {
        val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
        mStateManager!!.current.performActionWithFreshTokens(
            mAuthService!!, clientAuthentication
        ) { accessToken: String?, _idToken: String?, ex: AuthorizationException? ->
            fetchReceiptInfo(accessToken, _idToken, ex, receipt)
        }
    }

    @MainThread
    fun fetchReceiptInfo(
        accessToken: String?,
        _idToken: String?,
        ex: AuthorizationException?,
        receipt: Receipt
    ) {
        if (ex != null) {
            // negotiation for fresh tokens failed, check ex for more details
            Log.e(TAG, "fresh token problem: $ex")
            _uiState.value =
                _uiState.value.copy(status = "could not get fresh token (network error?)")
            return
        }

        viewModelScope.launch {
            try {
                lidlRepository.fetchReceipt(accessToken!!, receipt)
                _uiState.value = _uiState.value.copy(status = "done")
            } catch (networkError: IOException) {
                Log.e("Error", "NETWORK ERROR!")
                _uiState.value = _uiState.value.copy(status = "network error")
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