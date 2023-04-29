package zip.zaop.paylink.network

import kotlinx.serialization.Serializable

@Serializable
data class ReceiptListResponse(
    val page: Int,
    val size: Int,
    val totalCount: Int,
    val records: List<Receipt>
)

@Serializable
data class Receipt(
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