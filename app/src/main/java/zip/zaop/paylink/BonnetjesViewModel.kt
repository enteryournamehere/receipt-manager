package zip.zaop.paylink

import android.app.Application
import android.content.Intent
import android.icu.util.Calendar
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
import zip.zaop.paylink.database.DatabaseWbwMember
import zip.zaop.paylink.database.LinkablePlatform
import zip.zaop.paylink.database.getDatabase
import zip.zaop.paylink.domain.Receipt
import zip.zaop.paylink.domain.ReceiptItem
import zip.zaop.paylink.network.Amount
import zip.zaop.paylink.network.Expense
import zip.zaop.paylink.network.NewExpenseWrapper
import zip.zaop.paylink.network.ShareInfo
import zip.zaop.paylink.network.ShareMeta
import zip.zaop.paylink.network.WbwApi
import zip.zaop.paylink.repository.ReceiptRepository
import zip.zaop.paylink.util.convertCentsToString
import java.text.SimpleDateFormat
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class UiState(
    val status: String = "",
    val selectedCount: Int = 0,
    val selectedAmount: Int = 0,
    val wbwPopupShown: Boolean = false,
    val wbwMembersSelected: List<String> = emptyList(),
    val wbwListSelected: String? = null,
    val wbwNewExpenseText: String = "",
    val alertInfo: AlertInfo? = null,
)

data class AlertInfo(
    val shown: Boolean,
    val content: String,
)

data class FullInfo(
    val receipt: Receipt,
    val selectedItems: Set<Int>,
)

private const val TAG = "BonnetjesViewModel"

class BonnetjesViewModel(private val application: Application) : AndroidViewModel(application) {
    private var mExecutor: ExecutorService? = null
    private var mAuthServices: MutableMap<LinkablePlatform, AuthorizationService> = mutableMapOf()
    private var mStateManagers: MutableMap<LinkablePlatform, AuthStateManager> = mutableMapOf()

    private val receiptRepository = ReceiptRepository(getDatabase(application), application)

    fun closeAlertDialog() {
        _uiState.value = _uiState.value.copy(alertInfo = null)
    }

    fun getAllBonnetjes() {
        for ((platform, stateManager) in mStateManagers.entries) {
            if (stateManager.current.isAuthorized)
                getBonnetjes(platform)
        }
    }

    private fun getBonnetjes(platform: LinkablePlatform) {
        _uiState.value = _uiState.value.copy(status = "downloading...")
        if (platform == LinkablePlatform.LIDL) {
            val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
            mStateManagers[platform]!!.current.performActionWithFreshTokens(
                mAuthServices[platform]!!,
                clientAuthentication,
            ) { accessToken: String?, _idToken: String?, ex: AuthorizationException? ->
                this.getBonnetjes(platform, accessToken, ex)
            }
        } else {
            mStateManagers[platform]!!.current.performActionWithFreshTokens(
                mAuthServices[platform]!!
            ) { accessToken: String?, _idToken: String?, ex: AuthorizationException? ->
                this.getBonnetjes(platform, accessToken, ex)
            }
        }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val receipts: Flow<List<Receipt>> = receiptRepository.receipts
    val lists = receiptRepository.wbwLists

    private val wbwListSelectedFlow: MutableStateFlow<String?> = MutableStateFlow("")

    private var wbwListSelected: String?
        get() = wbwListSelectedFlow.value
        set(value) {
            wbwListSelectedFlow.value = value
            _uiState.value = _uiState.value.copy(wbwListSelected = value)
        }

    val members: StateFlow<List<DatabaseWbwMember>> =
        receiptRepository.wbwMembers
            .combine(wbwListSelectedFlow) { list, filter ->
                list.filter { it.list_id == filter }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val ourMemberId: StateFlow<String?> =
        receiptRepository.wbwLists.combine(wbwListSelectedFlow) { list, filter ->
            list.find { it.id == filter }?.our_member_id
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Map<Receipt ID, Set<Item-in-receipt-index>>
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
    fun selectReceiptItem(receipt: Receipt, item: ReceiptItem, state: Boolean) {
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

    fun openWbwPopup() {
        wbwListSelected = null
        _uiState.value = _uiState.value.copy(
            wbwPopupShown = true,
            wbwMembersSelected = emptyList(),
            wbwNewExpenseText = ""
        )
    }

    fun wbwListSelected(id: String) {
        wbwListSelected = id
        _uiState.value = _uiState.value.copy(
            wbwMembersSelected = emptyList(),
        )
        viewModelScope.launch {
            try {
                receiptRepository.refreshWbwMembers(id)
            } catch (networkError: Exception) {
                Log.e(TAG, "NETWORK ERROR! $networkError")
            }
        }
    }

    fun wbwMemberToggled(memberId: String) {
        if (_uiState.value.wbwMembersSelected.contains(memberId)) {
            _uiState.value = _uiState.value.copy(
                wbwMembersSelected = _uiState.value.wbwMembersSelected.minus(memberId)
            )
        } else {
            _uiState.value = _uiState.value.copy(
                wbwMembersSelected = _uiState.value.wbwMembersSelected.plus(memberId)
            )
        }
    }

    fun hideWbwPopup() {
        _uiState.value = _uiState.value.copy(wbwPopupShown = false)
    }

    fun wbwSubmit() {
        val cents = _uiState.value.selectedAmount
        val sharedBetween = _uiState.value.wbwMembersSelected.size
        val remainder = cents % sharedBetween
        val shareList = _uiState.value.wbwMembersSelected.map {
            ShareInfo(
                id = it,
                member_id = it,
                source_amount = Amount("EUR", cents / sharedBetween),
                amount = Amount("EUR", cents / sharedBetween),
                meta = ShareMeta(type = "factor", multiplier = 1)
            )
        }.toMutableList()

        for (i in 1..remainder) {
            val newAmount = Amount("EUR", shareList[i - 1].amount.fractional + 1)
            val newShare = shareList[i - 1].copy(amount = newAmount, source_amount = newAmount)
            shareList[i - 1] = newShare
        }

        val time = Calendar.getInstance().time
        val formatter = SimpleDateFormat("yyyy-MM-dd")
        val date = formatter.format(time)
        viewModelScope.launch {
            try {
                val response = WbwApi.getRetrofitService(application).addExpense(
                    wbwListSelected!!, NewExpenseWrapper(
                        expense = Expense(
                            id = UUID.randomUUID().toString(),
                            name = _uiState.value.wbwNewExpenseText,
                            payed_by_id = ourMemberId.value!!,
                            payed_on = date,
                            source_amount = Amount(currency = "EUR", fractional = cents),
                            amount = Amount(currency = "EUR", fractional = cents),
                            exchange_rate = 1,
                            shares_attributes = shareList,
                        )
                    )
                )
                if (response.message != null) { // TODO maybe this never works bc error status code means an exception is thrown
                    _uiState.value = _uiState.value.copy(status = response.message)
                } else {
                    _uiState.value = _uiState.value.copy(
                        wbwPopupShown = false,
                        selectedCount = 0,
                        selectedAmount = 0
                    )
                    _selectionStateFlow.value = emptyMap()
                }
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
            }
        }
    }

    fun updateWbwNewExpenseText(text: String) {
        _uiState.value = _uiState.value.copy(
            wbwNewExpenseText = text,
        )
    }

    @MainThread
    fun getBonnetjes(
        platform: LinkablePlatform,
        accessToken: String?,
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
                Log.e(TAG, "NETWORK ERROR! $networkError")
                _uiState.value = _uiState.value.copy(status = "network error")
            }
        }
    }

    fun fetchReceiptInfo(platform: LinkablePlatform, receipt: Receipt) {
        val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
        mStateManagers[platform]!!.current.performActionWithFreshTokens(
            mAuthServices[platform]!!, clientAuthentication
        ) { accessToken: String?, _idToken: String?, ex: AuthorizationException? ->
            fetchReceiptInfo(accessToken, ex, receipt)
        }
    }

    @MainThread
    fun fetchReceiptInfo(
        accessToken: String?,
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
                Log.e(TAG, "NETWORK ERROR! $networkError")
                _uiState.value = _uiState.value.copy(status = "network error")
            }
        }
    }

    init {
        val context = application.applicationContext

        mStateManagers[LinkablePlatform.LIDL] =
            AuthStateManager.getInstance(context, receiptRepository, LinkablePlatform.LIDL)
        mStateManagers[LinkablePlatform.APPIE] =
            AuthStateManager.getInstance(context, receiptRepository, LinkablePlatform.APPIE)
        mStateManagers[LinkablePlatform.JUMBO] =
            AuthStateManager.getInstance(context, receiptRepository, LinkablePlatform.JUMBO)

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
                _uiState.value = _uiState.value.copy(status = "logged in")
                return
            }

            Log.i(TAG, "response: " + response.toString() + " ; ex=" + ex.toString())

            viewModelScope.launch {
                mStateManagers[platform]!!.updateAfterAuthorization(response, ex)
            }

            if (response.authorizationCode != null) {
                // authorization code exchange is required
                Log.i(TAG, "Auth code exchange is required.")
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