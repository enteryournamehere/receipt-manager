package zip.zaop.paylink.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Query("select * from receipt " +
            "left join receipt_item on receipt.id = receipt_item.receipt_id")
    fun loadReceiptsAndItems(): Flow<Map<DatabaseReceipt, List<DatabaseReceiptItem>>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertReceipts(receipts: List<DatabaseReceipt> )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertReceiptItems( receipts: List<DatabaseReceiptItem> )
}

@Database(entities = [DatabaseReceipt::class, DatabaseReceiptItem::class], version = 2)
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
