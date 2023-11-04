package zip.zaop.paylink.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import zip.zaop.paylink.database.DatabaseAuthState
import zip.zaop.paylink.database.DatabaseWbwList
import zip.zaop.paylink.database.DatabaseWbwMember
import zip.zaop.paylink.database.LinkablePlatform
import zip.zaop.paylink.database.ReceiptsDatabase
import zip.zaop.paylink.database.asDomainModel
import zip.zaop.paylink.domain.Receipt
import zip.zaop.paylink.network.AppieApi
import zip.zaop.paylink.network.JumboApi
import zip.zaop.paylink.network.LidlApi
import zip.zaop.paylink.network.WbwApi
import zip.zaop.paylink.network.asDatabaseModel
import zip.zaop.paylink.network.toDatabaseModel

class ReceiptRepository(private val database: ReceiptsDatabase, val context: Context) {
    val receipts: Flow<List<Receipt>> =
        database.receiptDao.loadReceiptsAndItems().map { it.asDomainModel() }

    val auth: Flow<Map<LinkablePlatform, String>> = database.receiptDao.getAuthStates()

    val wbwLists: Flow<List<DatabaseWbwList>> = database.receiptDao.getWbwLists()
    val wbwMembers: Flow<List<DatabaseWbwMember>> = database.receiptDao.getWbwMembers()

    suspend fun refreshReceipts(platform: LinkablePlatform, accessToken: String) {
        withContext(Dispatchers.IO) {
            when (platform) {
                LinkablePlatform.LIDL -> {
                    val receipts = LidlApi.retrofitService.getReceipts(1, "Bearer $accessToken")
                    database.receiptDao.insertReceipts(receipts.asDatabaseModel())
                }

                LinkablePlatform.APPIE -> {
                    val receipts = AppieApi.retrofitService.getReceipts("Bearer $accessToken")
                    database.receiptDao.insertReceipts(receipts.asDatabaseModel())
                }

                LinkablePlatform.JUMBO -> {
                    val receipts =
                        JumboApi.getRetrofitService(context).getReceipts("Bearer $accessToken")
                    database.receiptDao.insertReceipts(receipts.asDatabaseModel())
                }

                else -> return@withContext
            }
        }
    }

    suspend fun fetchReceipt(accessToken: String, receipt: Receipt) {
        withContext(Dispatchers.IO) {
            when (receipt.store) {
                "lidl" -> {
                    val details =
                        LidlApi.retrofitService.getReceipt(
                            receipt.storeProvidedId,
                            "Bearer $accessToken"
                        )
                    database.receiptDao.insertReceiptItems(details.itemsLine.asDatabaseModel(receipt.id))
                }

                "appie" -> {
                    val details =
                        AppieApi.retrofitService.getReceipt(
                            receipt.storeProvidedId,
                            "Bearer $accessToken"
                        )
                    database.receiptDao.insertReceiptItems(
                        details.receiptUiItems.asDatabaseModel(
                            receipt.id
                        )
                    )
                }

                "jumbo" -> {
                    @Suppress("UNUSED_VARIABLE") val details =
                        JumboApi.getRetrofitService(context)
                            .getReceipt(receipt.storeProvidedId, "Bearer $accessToken")
//                    database.receiptDao.insertReceiptItems(details.receiptUiItems.asDatabaseModel(receipt.id))
                }

                else -> return@withContext
            }
        }
    }

    suspend fun updateAuthState(platform: LinkablePlatform, state: String) {
        withContext(Dispatchers.IO) {
            database.receiptDao.setAuthState(DatabaseAuthState(platform, state))
        }
    }

    suspend fun deleteAuthState(platform: LinkablePlatform) {
        withContext(Dispatchers.IO) {
            database.receiptDao.clearAuthState(platform)
        }
    }

    suspend fun refreshWbwLists() {
        withContext(Dispatchers.IO) {
            val lists = WbwApi.getRetrofitService(context).getLists()
            database.receiptDao.insertWbwLists(lists.asDatabaseModel())

            // Delete lists from cache that no longer exist
            wbwLists.take(1).collect { storedLists ->
                for (list in storedLists) {
                    if (lists.data.find { it.list.id == list.id } == null) {
                        database.receiptDao.deleteWbwList(list)
                    }
                }
            }

            val balances = WbwApi.getRetrofitService(context).getBalances()
            for (item in balances.data) {
                val balance = item.balance
                database.receiptDao.setOurMemberId(balance.list.id, balance.member.id)
            }
        }
    }

    suspend fun refreshWbwMembers(listId: String) {
        withContext(Dispatchers.IO) {
            val details = WbwApi.getRetrofitService(context).getMembers(listId)
            database.receiptDao.insertWbwMembers(details.data.toDatabaseModel())
        }
    }
}