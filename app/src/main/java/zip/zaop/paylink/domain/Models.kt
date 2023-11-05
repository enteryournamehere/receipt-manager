package zip.zaop.paylink.domain

data class Receipt(
    val id: Int,
    val items: List<ReceiptItem>?,
    val store: String,
    val date: String,
    val totalAmount: Int, // CENTS
    val storeProvidedId: String,
)

data class ReceiptItem (
    val id: Int,
    val indexInsideReceipt: Int,
    val unitPrice: Int,
    val quantity: Float,
    val storeProvidedItemCode: String?,
    val description: String,
    val totalPrice: Int,
    val hasBeenSentToWbw: Boolean,
)