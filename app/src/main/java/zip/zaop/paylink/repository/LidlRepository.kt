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
import zip.zaop.paylink.network.LidlApi
import zip.zaop.paylink.network.asDatabaseModel

class LidlRepository(private val database: ReceiptsDatabase) {
    val receipts: Flow<List<Receipt>> =
        database.receiptDao.loadReceiptsAndItems().map { it.asDomainModel() }

    val auth: Flow<Map<LinkablePlatform, String>> = database.receiptDao.getAuthStates()

    suspend fun refreshReceipts(accessToken: String) {
        withContext(Dispatchers.IO) {
            val receipts = LidlApi.retrofitService.getReceipts(1, "Bearer $accessToken")
            database.receiptDao.insertReceipts(receipts.asDatabaseModel())
        }
    }

    suspend fun fetchReceipt(accessToken: String, receipt: Receipt) {
        withContext(Dispatchers.IO) {
            val details = LidlApi.retrofitService.getReceipt(receipt.storeProvidedId, "Bearer $accessToken")
            database.receiptDao.insertReceiptItems(details.itemsLine.asDatabaseModel(receipt.id))
        }
    }

    suspend fun updateAuthState(platform: LinkablePlatform, state: String) {
        withContext(Dispatchers.IO) {
            database.receiptDao.setAuthState(DatabaseAuthState(platform, state))
        }
    }
}