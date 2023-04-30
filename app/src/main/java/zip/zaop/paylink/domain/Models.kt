package zip.zaop.paylink.domain

data class Receipt(
    val id: Int,
    val items: List<ReceiptItem>?,
    val store: String,
    val date: String,
    val storeProvidedId: String,
)

data class ReceiptItem (
    val id: Int,
    val unitPrice: Float,
    val quantity: Float,
    val storeProvidedItemCode: String?,
    val description: String,
    val totalPrice: Float,
)