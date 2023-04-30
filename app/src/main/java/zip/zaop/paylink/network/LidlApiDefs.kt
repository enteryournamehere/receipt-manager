package zip.zaop.paylink.network

import kotlinx.serialization.Serializable
import zip.zaop.paylink.database.DatabaseReceipt
import zip.zaop.paylink.domain.Receipt
import zip.zaop.paylink.domain.ReceiptItem

@Serializable
data class NetworkLidlReceiptList(
    val page: Int,
    val size: Int,
    val totalCount: Int,
    val records: List<NetworkLidlReceiptListItem>
)

// illegal !!! go through repo
//fun NetworkLidlReceiptList.asDomainModel(): List<Receipt> {
//    return records.map {
//        Receipt(
//            items = null,
//            store = "lidl",
//            date = it.date,
//            storeProvidedId = it.id,
//
//        )
//    }
//}

fun NetworkLidlReceiptList.asDatabaseModel(): List<DatabaseReceipt> {
    return records.map {
        DatabaseReceipt(
            id = 0,
            store = "lidl",
            date = it.date,
            storeProvidedId = it.id
        )
    }
}
@Serializable
data class NetworkLidlReceiptListItem(
    val id: String,
    val isFavorite: Boolean,
    val date: String,
    val totalAmount: String,
    val storeCode: String,
    val currency: Currency,
    val articlesCount: Int,
    val couponsUsedCount: Int,
    val hasReturnedItems: Boolean,
    val returnsCount: Int,
    val returnedAmount: String,
//    val invoiceRequestId: null,
//    val invoiceId: null,
//    val vendor: null,
    val hasHtmlDocument: Boolean,
    val isHtml: Boolean
)

@Serializable
data class Currency(
    val code: String,
    val symbol: String
)

@Serializable
data class NetworkLidlReceiptDetails (
    val id: String,
    val barCode: String,
    var sequenceNumber: String,
    val workstation: String,
    val itemsLine: List<NetworkLidlReceiptItem>,
    val totalTaxes: TotalTaxes,
    val couponsUsed: List<UsedCoupon>,
    val returnedTickets: List<String>,
    val isFavorite: Boolean,
    val date: String,
    val totalAmount: String,
    val sumAmount: String,
    val storeCode: String,
    val tenderChange: List<String>,
    val fiscalDataAt: String?,
    val isEmployee: Boolean,
    val linesScannedCount: Int,
    val totalDiscount: String,
    val taxExcemptTexts: String,
    val ustIdNr: String?,
    val languageCode: String
)

@Serializable
data class NetworkLidlReceiptItem(
    val currentUnitPrice: String,
    val quantity: String,
    val isWeight: Boolean,
    val originalAmount: String,
    val extendedAmount: String,
    val description: String,
    val taxGroup: String,
    val taxGroupName: String,
    val codeInput: String,
//    val discounts: List<String>, unknown item type
)

@Serializable
data class TotalTaxes(
    val totalAmount: String,
    val totalTaxableAmount: String,
    val totalNetAmount: String
)

@Serializable
data class UsedCoupon(
    val couponId: String,
    val title: String,
    val discount: String,
    val footerTitle: String,
    val footerDescription: String,
    val block2Description: String,
    val image: String
)

