package zip.zaop.paylink.repository

import android.content.Context
import android.net.Uri
import android.util.Log
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
import zip.zaop.paylink.database.getDatabase
import zip.zaop.paylink.domain.Receipt
import zip.zaop.paylink.network.AppieApi
import zip.zaop.paylink.network.JumboApi
import zip.zaop.paylink.network.LidlApi
import zip.zaop.paylink.network.WbwApi
import zip.zaop.paylink.network.asDatabaseModel
import zip.zaop.paylink.network.toDatabaseModel
import java.io.IOException

class ReceiptRepository(private val database: ReceiptsDatabase, val context: Context) {
    val receipts: Flow<List<Receipt>> =
        database.receiptDao.loadReceiptsAndItems().map { it.asDomainModel() }

    val auth: Flow<Map<LinkablePlatform, String>> = database.receiptDao.getAuthStates()

    val wbwLists: Flow<List<DatabaseWbwList>> = database.receiptDao.getWbwLists()
    val wbwMembers: Flow<List<DatabaseWbwMember>> = database.receiptDao.getWbwMembers()

    fun exportDatabase(path: Uri): Boolean {
        getDatabase(context).openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(FULL);").use { cursor ->
                if (cursor.moveToFirst()) {
                    val log = cursor.getString(0)
                    Log.d("DB export", "WAL checkpoint result: $log")
                }
            }
        Log.d("DB export", "Checkpoint done")

        val dbFile = context.getDatabasePath("receipts")
        try {
            context.contentResolver.openOutputStream(path, "w")
                .use { output ->
                    dbFile.inputStream().use { input ->
                        input.copyTo(output!!)
                    }
                }

            Log.d("DB export", "Copied file")

            return true
        } catch (e: IOException) {
            Log.e("DB export", "Failed to copy file.")
            e.printStackTrace()
            return false
        }
    }

    suspend fun deleteAllReceipts() {
        withContext(Dispatchers.IO) {
            database.receiptDao.clearReceipts()
        }
    }

    suspend fun refreshReceipts(platform: LinkablePlatform, accessToken: String) {
        withContext(Dispatchers.IO) {
            when (platform) {
                LinkablePlatform.LIDL -> {
                    val receipts = LidlApi.getRetrofitService(context).getReceipts(1, "Bearer $accessToken")
                    database.receiptDao.insertReceipts(receipts.asDatabaseModel())
                }

                LinkablePlatform.APPIE -> {
                    val receipts = AppieApi.getRetrofitService(context).getReceipts("Bearer $accessToken")
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
                        LidlApi.getRetrofitService(context).getReceipt(
                            receipt.storeProvidedId,
                            "Bearer $accessToken"
                        )
                    database.receiptDao.insertReceiptItems(details.asDatabaseModel(receipt.id))
                }

                "appie" -> {
                    val details =
                        AppieApi.getRetrofitService(context).getReceipt(
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

    suspend fun setWbwFlags(items: Map<Int, Set<Int>>) {
        withContext(Dispatchers.IO) {
            for ((receiptId, receiptItems) in items.entries) {
                for (receiptItem in receiptItems) {
                    database.receiptDao.setWbwFlag(receiptId, receiptItem)
                }
            }
        }
    }
}