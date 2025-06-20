package zip.zaop.paylink

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import net.openid.appauth.connectivity.DefaultConnectionBuilder
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import zip.zaop.paylink.database.DatabaseAuthState
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
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class UiState(
    val status: String = "",
    val selectedCount: Int = 0,
    val selectedAmount: Int = 0,
    val latestSelectedDate: String = "",
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

    private val receiptRepository = ReceiptRepository(getDatabase(application), application)
    private val authStatesFlow = receiptRepository.authStates
    private val handledStates = mutableSetOf<String>()

    fun closeAlertDialog() {
        _uiState.value = _uiState.value.copy(alertInfo = null)
    }

    fun getAllBonnetjes() {
        viewModelScope.launch {
            authStatesFlow.take(1).collect { map ->
                for ((platform, states) in map) {
                    if (platform == LinkablePlatform.WBW) {
                        continue
                    }
                    for (state in states) {
                        val manager = AuthStateManager.getInstance(
                            application.applicationContext,
                            receiptRepository,
                            platform,
                            state.id
                        )
                        Log.i(TAG, "Fetching receipts for platform: $platform, account ID: ${state.id}")
                        if (manager.current.isAuthorized) {
                            Log.i(TAG, "It is authorised")
                            getBonnetjes(state)
                        }
                    }
                }
            }
        }
    }

    private fun getBonnetjes(state: DatabaseAuthState) {
        val platform = state.platform
        _uiState.value = _uiState.value.copy(status = "downloading...")
        val manager = AuthStateManager.getInstance(
            application.applicationContext,
            receiptRepository,
            platform,
            state.id
        )
        if (platform == LinkablePlatform.LIDL) {
            val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
            manager.current.performActionWithFreshTokens(
                mAuthServices[platform]!!,
                clientAuthentication,
            ) { accessToken: String?, _idToken: String?, ex: AuthorizationException? ->
                viewModelScope.launch { manager.replace(manager.current) }
                this.getBonnetjes(state, accessToken, ex)
            }
        } else {
            manager.current.performActionWithFreshTokens(
                mAuthServices[platform]!!
            ) { accessToken: String?, _idToken: String?, ex: AuthorizationException? ->
                viewModelScope.launch { manager.replace(manager.current) }
                this.getBonnetjes(state, accessToken, ex)
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
                    selectedAmount = _uiState.value.selectedAmount + (item.totalPrice - item.totalDiscount)
                )
        } else {
            val removed = updatedSet.remove(item.indexInsideReceipt)
            if (removed) _uiState.value = _uiState.value.copy(
                selectedCount = _uiState.value.selectedCount.dec(),
                selectedAmount = _uiState.value.selectedAmount - (item.totalPrice - item.totalDiscount)
            )
        }
        _selectionStateFlow.value = _selectionStateFlow.value + (receipt.id to updatedSet)
        _uiState.value = _uiState.value.copy(latestSelectedDate = receipt.date.split("T")[0])
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

        val date = _uiState.value.latestSelectedDate
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

                receiptRepository.setWbwFlags(_selectionStateFlow.value)

                _uiState.value = _uiState.value.copy(
                    wbwPopupShown = false,
                    selectedCount = 0,
                    selectedAmount = 0
                )
                _selectionStateFlow.value = emptyMap()

                // TODO actually handle errors (see accounts viewmodel: wbw login for example)
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
        state: DatabaseAuthState,
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
                receiptRepository.refreshReceipts(state, accessToken!!)
                _uiState.value = _uiState.value.copy(status = "done")
            } catch (networkError: Exception) {
                Log.e(TAG, "NETWORK ERROR! $networkError")
                _uiState.value = _uiState.value.copy(status = "network error")
            }
        }
    }

    fun fetchReceiptInfo(platform: LinkablePlatform, accountId: Long, receipt: Receipt) {
        val manager = AuthStateManager.getInstance(
            application.applicationContext,
            receiptRepository,
            platform,
            accountId
        )
        if (platform == LinkablePlatform.LIDL) {
            val clientAuthentication: ClientAuthentication = ClientSecretBasic("secret")
            manager.current.performActionWithFreshTokens(
                mAuthServices[platform]!!,
                clientAuthentication
            ) { accessToken: String?, _idToken: String?, ex: AuthorizationException? ->
                viewModelScope.launch { manager.replace(manager.current) }
                fetchReceiptInfo(accessToken, ex, receipt)
            }
        } else {
            manager.current.performActionWithFreshTokens(
                mAuthServices[platform]!!
            ) { accessToken: String?, _idToken: String?, ex: AuthorizationException? ->
                viewModelScope.launch { manager.replace(manager.current) }
                fetchReceiptInfo(accessToken, ex, receipt)
            }
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
        Log.i(TAG, "Starting BonnetjesViewModel with intent: $intent")
        if (mExecutor == null || mExecutor!!.isShutdown) {
            mExecutor = Executors.newSingleThreadExecutor()
        }

        // the stored AuthState is incomplete, so check if we are currently receiving the result of
        // the authorization flow from the browser.
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        if (response != null) {
            val state = response.request.state
            if (state == null || handledStates.contains(state)) {
                Log.i(TAG, "Authorization response already handled or no state, ignoring. State: $state")
                return
            }
            handledStates.add(state)

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

            viewModelScope.launch {
                Log.i(TAG, "Running viewModelScope launch for platform: $platform")
                val manager = AuthStateManager.getInstance(
                    application.applicationContext,
                    receiptRepository,
                    platform,
                    0 // temp ID
                )
                manager.updateAfterAuthorization(response, ex)

                if (response.authorizationCode != null) {
                    // authorization code exchange is required
                    Log.i(TAG, "Auth code exchange is required.")
                    exchangeAuthorizationCode(platform, 0, response)
                } else if (ex != null) {
                    displayNotAuthorized("Authorization flow failed: " + ex.message)
                } else {
                    displayNotAuthorized("No authorization state retained - reauthorization required")
                }
            }
        }
    }

    fun fetchLidlMemberId(token: String): Long? {
        val parts = token.split(".")
        if (parts.size < 2) {
            Log.e(TAG, "Invalid Lidl JWT")
            return null
        }
        val payloadBase64 = parts[1]

        val decoder = Base64.getUrlDecoder()
        val payloadJson = String(decoder.decode(payloadBase64))

        val json = JSONObject(payloadJson)
        val sub = json.optString("sub")
        val subInt = sub.toLongOrNull()

        return subInt
    }

    suspend fun fetchAppieMemberId(token: String): Long? {
        return withContext(Dispatchers.IO) {
            val client = okhttp3.OkHttpClient()
            val mediaType = "application/json".toMediaTypeOrNull()
            val body = okhttp3.RequestBody.create(
                mediaType, """
            {
                "operationName": "GetMember",
                "variables": {},
                "query": "query GetMember { member { __typename ...MemberData } }   fragment PersonalDetails on Member { name { first last } }  fragment MemberData on Member { __typename id ...PersonalDetails }"
            }
            """.trimIndent()
            )
            val request = okhttp3.Request.Builder()
                .url("https://api.ah.nl/graphql")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $token")
                .build()

            return@withContext try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    json.optJSONObject("data")
                        ?.optJSONObject("member")
                        ?.optLong("id")
                } else {
                    Log.e(TAG, "Failed to fetch AH member ID: ${responseBody}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching AH member ID: $e")
                null
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
        accountId: Long,
        authorizationResponse: AuthorizationResponse
    ) {
        displayLoading("Exchanging authorization code")
        Log.i(TAG, "Exchanging authorization code for platform: $platform, account ID: $accountId")
        performTokenRequest(
            platform,
            authorizationResponse.createTokenExchangeRequest()
        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            handleCodeExchangeResponse(
                platform,
                accountId,
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

    // TODO: unused?
    @WorkerThread
    private fun handleAccessTokenResponse(
        platform: LinkablePlatform,
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?
    ) {
        val manager = AuthStateManager.getInstance(
            application.applicationContext,
            receiptRepository,
            platform,
            0
        )
        viewModelScope.launch {
            manager.updateAfterTokenResponse(tokenResponse, authException)
        }
    }

    @WorkerThread
    private fun handleCodeExchangeResponse(
        platform: LinkablePlatform,
        accountId: Long,
        tokenResponse: TokenResponse?,
        authException: AuthorizationException?,
    ) {
        Log.i(TAG, "Handling code exchange response: $tokenResponse, $authException")
        val tempManager = AuthStateManager.getInstance(
            application.applicationContext,
            receiptRepository,
            platform,
            accountId
        )
        viewModelScope.launch {
            tempManager.updateAfterTokenResponse(tokenResponse, authException)
            if (!tempManager.current.isAuthorized) {
                val message = ("Authorization Code exchange failed "
                        + if (authException != null) authException.error else "")
                displayNotAuthorized(message)
            } else {
                displayAuthorized(":D")
                val memberId = when (platform) {
                    LinkablePlatform.LIDL -> fetchLidlMemberId(tempManager.current.accessToken!!)
                    LinkablePlatform.JUMBO -> TODO()
                    LinkablePlatform.APPIE -> fetchAppieMemberId(tempManager.current.accessToken!!)
                    LinkablePlatform.WBW -> 0 // Wbw doesn't use this auth flow
                }
                if (memberId != null) {
                    val finalManager = AuthStateManager.getInstance(
                        application.applicationContext,
                        receiptRepository,
                        platform,
                        memberId
                    )
                    finalManager.replace(tempManager.current)
                    if (accountId == 0.toLong()) {
                        tempManager.delete()
                    }
                } else {
                    Log.e(TAG, "Could not fetch member ID.")
                    if (accountId == 0.toLong()) {
                        tempManager.delete()
                    }
                }
            }
        }
    }
}