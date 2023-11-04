package zip.zaop.paylink.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.MapInfo
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Query(
        "select * from receipt " +
                "left join receipt_item on receipt.id = receipt_item.receipt_id " +
                "order by receipt.date desc"
    )
    fun loadReceiptsAndItems(): Flow<Map<DatabaseReceipt, List<DatabaseReceiptItem>>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertReceipts(receipts: List<DatabaseReceipt>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertReceiptItems(receipts: List<DatabaseReceiptItem>)

    @Query("select * from auth_state where platform = :platform")
    fun getAuthState(platform: LinkablePlatform): DatabaseAuthState

    @MapInfo(keyColumn = "platform", valueColumn = "state")
    @Query("select * from auth_state")
    fun getAuthStates(): Flow<Map<LinkablePlatform, String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setAuthState(state: DatabaseAuthState)

    @Query("delete from auth_state where platform = :platform")
    fun clearAuthState(platform: LinkablePlatform)

    @Query("select * from wbw_list")
    fun getWbwLists(): Flow<List<DatabaseWbwList>>

    @Query("select * from wbw_member")
    fun getWbwMembers(): Flow<List<DatabaseWbwMember>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWbwLists(lists: List<DatabaseWbwList>)

    @Delete
    fun deleteWbwList(list: DatabaseWbwList)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWbwMembers(lists: List<DatabaseWbwMember>)

    @Query("update wbw_list set our_member_id = :member_id where id = :list_id")
    fun setOurMemberId(list_id: String, member_id: String)
}

@Database(
    entities = [
        DatabaseReceipt::class,
        DatabaseReceiptItem::class,
        DatabaseAuthState::class,
        DatabaseWbwMember::class,
        DatabaseWbwList::class],
    version = 6
)
abstract class ReceiptsDatabase : RoomDatabase() {
    abstract val receiptDao: ReceiptDao
}

private lateinit var INSTANCE: ReceiptsDatabase

fun getDatabase(context: Context): ReceiptsDatabase {
    synchronized(ReceiptsDatabase::class.java) {
        if (!::INSTANCE.isInitialized) {
            INSTANCE = Room.databaseBuilder(
                context.applicationContext,
                ReceiptsDatabase::class.java,
                "receipts"
            ).fallbackToDestructiveMigration().build()
        }
    }
    return INSTANCE
}
