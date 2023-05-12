package zip.zaop.paylink.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import zip.zaop.paylink.database.DatabaseAuthState
import zip.zaop.paylink.database.LinkablePlatform
import zip.zaop.paylink.database.ReceiptsDatabase
import zip.zaop.paylink.database.asDomainModel
import zip.zaop.paylink.domain.Receipt
import zip.zaop.paylink.network.AppieApi
import zip.zaop.paylink.network.LidlApi
import zip.zaop.paylink.network.asDatabaseModel

class ReceiptRepository(private val database: ReceiptsDatabase) {
    val receipts: Flow<List<Receipt>> =
        database.receiptDao.loadReceiptsAndItems().map { it.asDomainModel() }

    val auth: Flow<Map<LinkablePlatform, String>> = database.receiptDao.getAuthStates()

    suspend fun refreshReceipts(platform: LinkablePlatform, accessToken: String) {
        withContext(Dispatchers.IO) {
            if (platform == LinkablePlatform.LIDL) {
                val receipts = LidlApi.retrofitService.getReceipts(1, "Bearer $accessToken")
                database.receiptDao.insertReceipts(receipts.asDatabaseModel())
            } else {
                val receipts = AppieApi.retrofitService.getReceipts("Bearer $accessToken")
                database.receiptDao.insertReceipts(receipts.asDatabaseModel())
            }
        }
    }

    suspend fun fetchReceipt(accessToken: String, receipt: Receipt) {
        withContext(Dispatchers.IO) {
            if (receipt.store == "lidl") {
                val details =
                    LidlApi.retrofitService.getReceipt(receipt.storeProvidedId, "Bearer $accessToken")
                database.receiptDao.insertReceiptItems(details.itemsLine.asDatabaseModel(receipt.id))
            }
            else {
                val details =
                    AppieApi.retrofitService.getReceipt(receipt.storeProvidedId, "Bearer $accessToken")
                database.receiptDao.insertReceiptItems(details.receiptUiItems.asDatabaseModel(receipt.id))
            }
        }
    }

    suspend fun updateAuthState(platform: LinkablePlatform, state: String) {
        withContext(Dispatchers.IO) {
            database.receiptDao.setAuthState(DatabaseAuthState(platform, state))
        }
    }
}