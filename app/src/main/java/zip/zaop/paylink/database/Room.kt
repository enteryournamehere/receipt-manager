package zip.zaop.paylink.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

import androidx.sqlite.db.SupportSQLiteDatabase

@Dao
interface ReceiptDao {
    @Query(
        "select * from receipt " +
                "left join receipt_item on receipt.id = receipt_item.receipt_id " +
                "order by receipt.date desc"
    )
    fun loadReceiptsAndItems(): Flow<Map<DatabaseReceipt, List<DatabaseReceiptItem>>>

    @Query("delete from receipt_item")
    fun clearReceiptItemsTable()
    @Query("delete from receipt")
    fun clearReceiptsTable()
    @Transaction
    fun clearReceipts() {
        clearReceiptItemsTable()
        clearReceiptsTable()
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertReceipts(receipts: List<DatabaseReceipt>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertReceiptItems(receipts: List<DatabaseReceiptItem>)

    @Query("update receipt_item set has_been_sent_to_wbw = 1 where receipt_id = :receipt_id and index_inside_receipt = :item_index")
    fun setWbwFlag(receipt_id: Int, item_index: Int)

    @Query("select * from auth_state where platform = :platform")
    fun getAuthState(platform: LinkablePlatform): List<DatabaseAuthState>

    @Query("select * from auth_state")
    fun getAuthStates(): Flow<List<DatabaseAuthState>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setAuthState(state: DatabaseAuthState): Long

    @Query("delete from auth_state where platform = :platform")
    fun clearAuthState(platform: LinkablePlatform)

    @Query("delete from auth_state where id = :id and platform = :platform")
    fun deleteAuthState(id: Long, platform: LinkablePlatform)

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
    version = 11,
    autoMigrations = [
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
    ]
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
            ).addMigrations(MIGRATION_10_11).fallbackToDestructiveMigration().build()
        }
    }
    return INSTANCE
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS auth_state")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS auth_state (
                id INTEGER NOT NULL DEFAULT 0,
                platform TEXT NOT NULL DEFAULT 'undefined',
                state TEXT NOT NULL DEFAULT 'undefined',
                PRIMARY KEY(id, platform, state)
            )
        """.trimIndent())
    }
}
