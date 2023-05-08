package zip.zaop.paylink.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import zip.zaop.paylink.database.DatabaseAuthState
import zip.zaop.paylink.database.LinkablePlatform
import zip.zaop.paylink.database.ReceiptsDatabase

class AuthRepository(private val database: ReceiptsDatabase) {
    val accounts: Flow<Map<LinkablePlatform, String>> =
        database.receiptDao.getAuthStates()

    suspend fun updateAuthState(platform: LinkablePlatform, state: String) {
        withContext(Dispatchers.IO) {
            database.receiptDao.setAuthState(DatabaseAuthState(platform, state))
        }
    }

    fun getCurrentAuthState(platform: LinkablePlatform) {

    }
}