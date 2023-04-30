package zip.zaop.paylink.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import zip.zaop.paylink.database.ReceiptsDatabase
import zip.zaop.paylink.database.asDomainModel
import zip.zaop.paylink.domain.Receipt
import zip.zaop.paylink.network.LidlApi
import zip.zaop.paylink.network.asDatabaseModel

class LidlRepository(private val database: ReceiptsDatabase) {
    val receipts: Flow<List<Receipt>> =
        database.receiptDao.loadReceiptsAndItems().map { it.asDomainModel() }

    suspend fun refreshReceipts(accessToken: String) {
        withContext(Dispatchers.IO) {
            val receipts = LidlApi.retrofitService.getReceipts(1, "Bearer $accessToken")
            database.receiptDao.insertAll(receipts.asDatabaseModel())
        }
    }
}