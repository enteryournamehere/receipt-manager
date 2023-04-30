package zip.zaop.paylink.database

import android.content.Context
import androidx.lifecycle.LiveData
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll( receipts: List<DatabaseReceipt> )
}

@Database(entities = [DatabaseReceipt::class, DatabaseReceiptItem::class], version = 1)
abstract class ReceiptsDatabase: RoomDatabase() {
    abstract val receiptDao: ReceiptDao
}

private lateinit var INSTANCE: ReceiptsDatabase

fun getDatabase(context: Context): ReceiptsDatabase {
    synchronized(ReceiptsDatabase::class.java) {
        if (!::INSTANCE.isInitialized) {
            INSTANCE = Room.databaseBuilder(context.applicationContext,
                    ReceiptsDatabase::class.java,
                    "receipts").build()
        }
    }
    return INSTANCE
}
