package zip.zaop.paylink.database

import android.util.Log
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import zip.zaop.paylink.domain.Receipt
import zip.zaop.paylink.domain.ReceiptItem

@Entity(tableName="receipt", indices = [Index(value = ["store", "store_provided_id"], unique = true)])
data class DatabaseReceipt constructor(
    @PrimaryKey(autoGenerate = true)
    val id: Int, // unique database ID
    @ColumnInfo(name = "store_provided_id")
    val storeProvidedId: String,
    val date: String,
    val store: String, // todo ... yeah. for now: "ah", "lidl"
)

@Entity(tableName="receipt_item", indices = [Index(value = ["receipt_id", "index_inside_receipt"], unique = true)])
data class DatabaseReceiptItem constructor(
    @PrimaryKey(autoGenerate = true)
    val item_id: Int,
    @ColumnInfo(name = "index_inside_receipt")
    val indexInsideReceipt: Int,
    @ColumnInfo(name = "receipt_id")
    val receiptId: Int, // foreign key
    @ColumnInfo(name = "unit_price")
    val unitPrice: Float,
    val quantity: Float,
    @ColumnInfo(name = "store_provided_item_code")
    val storeProvidedItemCode: String?,
    val description: String,
    @ColumnInfo(name = "total_price")
    val totalPrice: Float,
)

fun Map<DatabaseReceipt, List<DatabaseReceiptItem>>.asDomainModel(): List<Receipt> {
    Log.i("CONV", "Running asDomainModel, map size: ${this.size}")

    return this.entries.map { entry ->
        Receipt(
            id = entry.key.id,
            store = entry.key.store,
            storeProvidedId = entry.key.storeProvidedId,
            date = entry.key.date,
            items = entry.value.map { ReceiptItem(
                id = it.item_id,
                unitPrice = it.unitPrice,
                quantity = it.quantity,
                storeProvidedItemCode = it.storeProvidedItemCode,
                description = it.description,
                totalPrice = it.totalPrice
            ) }
        )
    }
}
