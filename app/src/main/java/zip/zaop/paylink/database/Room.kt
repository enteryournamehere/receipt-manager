package zip.zaop.paylink.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.MapInfo
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Query("select * from receipt " +
            "left join receipt_item on receipt.id = receipt_item.receipt_id " +
            "order by receipt.date desc")
    fun loadReceiptsAndItems(): Flow<Map<DatabaseReceipt, List<DatabaseReceiptItem>>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertReceipts(receipts: List<DatabaseReceipt> )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertReceiptItems( receipts: List<DatabaseReceiptItem> )

    @Query("select * from auth_state where platform = :platform")
    fun getAuthState(platform: LinkablePlatform): DatabaseAuthState

    @MapInfo(keyColumn = "platform", valueColumn = "state")
    @Query("select * from auth_state")
    fun getAuthStates(): Flow<Map<LinkablePlatform, String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setAuthState(state: DatabaseAuthState)
}

@Database(entities = [DatabaseReceipt::class, DatabaseReceiptItem::class, DatabaseAuthState::class], version = 3)
abstract class ReceiptsDatabase: RoomDatabase() {
    abstract val receiptDao: ReceiptDao
}

private lateinit var INSTANCE: ReceiptsDatabase

fun getDatabase(context: Context): ReceiptsDatabase {
    synchronized(ReceiptsDatabase::class.java) {
        if (!::INSTANCE.isInitialized) {
            INSTANCE = Room.databaseBuilder(context.applicationContext,
                    ReceiptsDatabase::class.java,
                    "receipts").fallbackToDestructiveMigration().build()
        }
    }
    return INSTANCE
}
