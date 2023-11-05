package zip.zaop.paylink.network

import kotlinx.serialization.Serializable
import zip.zaop.paylink.database.DatabaseReceipt
import zip.zaop.paylink.database.DatabaseReceiptItem
import java.lang.Float.parseFloat
import kotlin.math.roundToInt

@Serializable
data class NetworkLidlReceiptList(
    val page: Int,
    val size: Int,
    val totalCount: Int,
    val tickets: List<NetworkLidlReceiptListItem>
)

fun NetworkLidlReceiptList.asDatabaseModel(): List<DatabaseReceipt> {
    return tickets.map {
        DatabaseReceipt(
            id = 0,
            store = "lidl",
            date = it.date,
            storeProvidedId = it.id,
            totalAmount = (it.totalAmount * 100).roundToInt()
        )
    }
}

fun List<NetworkLidlReceiptItem>.asDatabaseModel(receiptId: Int): List<DatabaseReceiptItem> {
    return this.mapIndexed { int, it ->
        DatabaseReceiptItem(
            item_id = 0,
            receiptId = receiptId,
            unitPrice = floatEurosToCents(it.currentUnitPrice)!!,
            quantity = parseFloat(it.quantity.replace(",", ".")), // TODO handle KGs!!
            storeProvidedItemCode = it.codeInput,
            description = it.name,
            totalPrice = floatEurosToCents(it.originalAmount)!! - it.discounts.sumOf {
                floatEurosToCents(
                    it.amount
                )!!
            },
            indexInsideReceipt = int,
            hasBeenSentToWbw = false,
        )
    }
}

@Serializable
data class NetworkLidlReceiptListItem(
    val id: String,
    val isFavorite: Boolean,
    val date: String,
    val totalAmount: Float,
    val storeCode: String,
    val currency: Currency,
    val articlesCount: Int,
    val couponsUsedCount: Int,
//    val returns: List<?>
    val hasHtmlDocument: Boolean,
    val isHtml: Boolean
    // vendor: null
)

@Serializable
data class Currency(
    val code: String,
    val symbol: String
)

@Serializable
data class NetworkLidlReceiptDetails(
    val id: String,
    val barCode: String,
    var sequenceNumber: String,
    val workstation: String,
    val itemsLine: List<NetworkLidlReceiptItem>,
    val taxes: List<Tax>,
    val totalTaxes: TotalTaxes,
    val couponsUsed: List<UsedCoupon>,
//    val returnedTickets: List<???>,
    val isFavorite: Boolean,
    val date: String,
    val totalAmount: String,
    val store: StoreInfo,
    val currency: Currency,
    val payments: List<Payment>,
    val tenderChange: List<String>,
//    val fiscalDataAt: null,
//    val fiscalDataCZ: null,
//    val fiscalDataDe: null,
    val isEmployee: Boolean,
    val linesScannedCount: Int,
    val totalDiscount: String,
    val taxExemptTexts: String,
//    val ustIdNr: null,
    val languageCode: String,
//    val operatorId: null,
    val htmlPrintedReceipt: String,
    val printedReceiptState: String,
    val isHtml: Boolean,
    val hasHtmlDocument: Boolean,
)

@Serializable
data class Payment(
    val type: String,
    val amount: String,
    val description: String,
    val roundingDifference: String,
//    val foreignPayment: null,
    val cardInfo: CardInfo,
    val rawPaymentInformationHTML: String,
)

@Serializable
data class CardInfo(
    val accountNumber: String,
)

@Serializable
data class Tax(
    val taxGroupName: String,
    val percentage: String,
    val amount: String,
    val taxableAmount: String,
    val netAmount: String,
)

@Serializable
data class StoreInfo(
    val address: String,
    val id: String,
    val locality: String,
    val name: String,
    val postalCode: String,
    val schedule: String,
)

@Serializable
data class NetworkLidlReceiptItem(
    val currentUnitPrice: String,
    val quantity: String,
    val isWeight: Boolean,
    val originalAmount: String,
    val name: String,
    val taxGroupName: String,
    val codeInput: String,
    val discounts: List<ItemDiscount>,
)

@Serializable
data class ItemDiscount(
    val amount: String,
    val description: String,
)

@Serializable
data class TotalTaxes(
    val totalAmount: String,
    val totalTaxableAmount: String,
    val totalNetAmount: String
)

@Serializable
data class UsedCoupon(
//    val couponId: String,
    val title: String,
    val discount: String,
//    val footerTitle: String,
//    val footerDescription: String,
    val block2Description: String,
//    val image: String
)

