package zip.zaop.paylink

//import androidx.core.content.Context.getSystemService
import android.app.Application
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
import zip.zaop.paylink.database.LinkablePlatform
import zip.zaop.paylink.database.getDatabase
import zip.zaop.paylink.domain.Receipt
import zip.zaop.paylink.domain.ReceiptItem
import zip.zaop.paylink.network.NetworkLidlReceiptItem
import zip.zaop.paylink.repository.ReceiptRepository
import zip.zaop.paylink.util.convertCentsToString
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
    private var mExecutor: ExecutorService? = null
    private val TAG = "binomiaalverdeling"
    private var mAuthServices: MutableMap<LinkablePlatform, AuthorizationService> = mutableMapOf();
    private var mStateManagers: MutableMap<LinkablePlatform, AuthStateManager> = mutableMapOf();

    private val receiptRepository = ReceiptRepository(getDatabase(application), application)

    fun getBonnetjes(platform: LinkablePlatform) {
        _uiState.value = _uiState.value.copy(status = "downloading...")
        if (platform == LinkablePlatform.LIDL) {
            val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
            mStateManagers[platform]!!.current.performActionWithFreshTokens(
                mAuthServices[platform]!!,
                clientAuthentication,
            ) { accessToken: String?, _idToken: String?, ex: AuthorizationException? ->
                this.getBonnetjes(platform, accessToken, _idToken, ex)
            }
        } else {
            mStateManagers[platform]!!.current.performActionWithFreshTokens(
                mAuthServices[platform]!!
            ) { accessToken: String?, _idToken: String?, ex: AuthorizationException? ->
                this.getBonnetjes(platform, accessToken, _idToken, ex)
            }
        }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val receipts: Flow<List<Receipt>> = receiptRepository.receipts

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
    fun getBonnetjes(
        platform: LinkablePlatform,
        accessToken: String?,
        _idToken: String?,
        ex: AuthorizationException?
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
                receiptRepository.refreshReceipts(platform, accessToken!!)
                _uiState.value = _uiState.value.copy(status = "done")
            } catch (networkError: Exception) {
                Log.e("Error", "NETWORK ERROR! $networkError")
                _uiState.value = _uiState.value.copy(status = "network error")
            }
        }
    }

    fun fetchReceiptInfo(platform: LinkablePlatform, receipt: Receipt) {
        val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
        mStateManagers[platform]!!.current.performActionWithFreshTokens(
            mAuthServices[platform]!!, clientAuthentication
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
                receiptRepository.fetchReceipt(accessToken!!, receipt)
                _uiState.value = _uiState.value.copy(status = "done")
            } catch (networkError: Exception) {
                Log.e("Error", "NETWORK ERROR! $networkError")
                _uiState.value = _uiState.value.copy(status = "network error")
            }
        }
    }

    init {
        val context = application.applicationContext;

        mStateManagers[LinkablePlatform.LIDL] = AuthStateManager.getInstance(context, receiptRepository, LinkablePlatform.LIDL)
        mStateManagers[LinkablePlatform.APPIE] = AuthStateManager.getInstance(context, receiptRepository, LinkablePlatform.APPIE)
        mStateManagers[LinkablePlatform.JUMBO] = AuthStateManager.getInstance(context, receiptRepository, LinkablePlatform.JUMBO)

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

    fun start(intent: Intent) {
        if (mExecutor == null || mExecutor!!.isShutdown) {
            mExecutor = Executors.newSingleThreadExecutor()
        }

        // the stored AuthState is incomplete, so check if we are currently receiving the result of
        // the authorization flow from the browser.
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        if (response != null) {
            val platform =
                when (response.request.clientId) {
                    "appie" -> LinkablePlatform.APPIE
                    "LidlPlusNativeClient" -> LinkablePlatform.LIDL
                    "ZVa0cW0LadbDHINgrBLuEAp5amVBKQh1" -> LinkablePlatform.JUMBO
                    else -> {
                        Log.e(TAG, "Unknown platform!")
                        return
                    }
                }

            if (mStateManagers[platform]!!.current.isAuthorized) {
                _uiState.value = _uiState.value.copy(status = "intent but authorized")
                return
            }

            Log.i(TAG, "response: " + response.toString() + " ; ex=" + ex.toString())
            if (response != null || ex != null) {
                Log.i(TAG, "Either is not null.")
                viewModelScope.launch {
                    mStateManagers[platform]!!.updateAfterAuthorization(response, ex)
                }
            }
            if (response?.authorizationCode != null) {
                // authorization code exchange is required
                Log.i(TAG, "Auth code exchange is required.")
                viewModelScope.launch {
                    mStateManagers[platform]!!.updateAfterAuthorization(response, ex)
                }
                exchangeAuthorizationCode(platform, response) // the good stuff :)
            } else if (ex != null) {
                displayNotAuthorized("Authorization flow failed: " + ex.message)
            } else {
                displayNotAuthorized("No authorization state retained - reauthorization required")
            }
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
    private fun exchangeAuthorizationCode(
        platform: LinkablePlatform,
        authorizationResponse: AuthorizationResponse
    ) {
        displayLoading("Exchanging authorization code")
        performTokenRequest(
            platform,
            authorizationResponse.createTokenExchangeRequest()
        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            handleCodeExchangeResponse(
                platform,
                tokenResponse,
                authException
            )
        }
    }

    @MainThread
    private fun performTokenRequest(
        platform: LinkablePlatform,
        request: TokenRequest,
        callback: AuthorizationService.TokenResponseCallback
    ) {
        if (platform == LinkablePlatform.LIDL) {
            val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
            mAuthServices[platform]!!.performTokenRequest(
                request,
                clientAuthentication,
                callback
            )
        } else {
            mAuthServices[platform]!!.performTokenRequest(
                request,
                callback
            )
        }
    }

    @WorkerThread
    private fun handleAccessTokenResponse(
        platform: LinkablePlatform,
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        viewModelScope.launch {
            mStateManagers[platform]!!.updateAfterTokenResponse(tokenResponse, authException)
        }

    }

    @WorkerThread
    private fun handleCodeExchangeResponse(
        platform: LinkablePlatform,
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        viewModelScope.launch {
            mStateManagers[platform]!!.updateAfterTokenResponse(tokenResponse, authException)
        }
        if (!mStateManagers[platform]!!.current.isAuthorized) {
            val message = ("Authorization Code exchange failed"
                    + if (authException != null) authException.error else "")

            // WrongThread inference is incorrect for lambdas
            displayNotAuthorized(message)
        } else {
            displayAuthorized(":D")
        }
    }
}