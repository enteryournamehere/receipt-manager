package zip.zaop.paylink.network

import kotlinx.serialization.Serializable
import zip.zaop.paylink.database.DatabaseReceipt
import zip.zaop.paylink.database.DatabaseReceiptItem
import kotlin.math.roundToInt

// https://api.ah.nl/mobile-services/member/v3/member
@Serializable
data class MemberResponse(
    val email: String,
    val name: NameInfo,
)

@Serializable
data class NameInfo(
    val firstName: String,
    val surname: String,
)

// https://api.ah.nl/mobile-services/v1/receipts
// List<AppieReceiptListItem>

@Serializable
data class AppieReceiptListItem(
//    val storeAddress: StoreAddress,
    val total: TotalInfo,
    val totalDiscount: TotalDiscountInfo,
    val transactionId: String,
    val transactionMoment: String
)

@Serializable
data class StoreAddress(
    val city: String,
    val countryCode: String,
    val houseNumber: String,
    val postalCode: String,
    val street: String
)

@Serializable
data class TotalInfo(
    val amount: AmountInfo
)

@Serializable
data class AmountInfo(
    val amount: Float,
    val currency: String
)

@Serializable
data class TotalDiscountInfo(
    val amount: Float
)

// https://api.ah.nl/mobile-services/v2/receipts/AH1c03a3cf618b3709130867a53e2ce25d0b693
@Serializable
data class AppieReceiptDetailsResponse(
    val receiptUiItems: List<AppieReceiptItem>,
    val storeId: Int,
    val transactionMoment: String
)

@Serializable
data class AppieReceiptItem(
    val style: String?,
    val type: String, // product, spacer, products-header, divider, subtotal, total, text, tech-info, other nonsense
    val alignment: String?,
    val isBold: Boolean?,
    val value: String?,
    val description: String?, // for products + bonuskaart
    val amount: String?, // 1,43 for products + bonuskaart
    val indicator: String?, // BTW indicator, always present for products?
    val quantity: String?, // int as string, for products, also "0.61KG"
    val price: String?, // used for total, also for "price per unit" if product is by weight or quantity > 1
)

fun List<AppieReceiptListItem>.asDatabaseModel(): List<DatabaseReceipt> {
    return this.map {
        DatabaseReceipt(
            id = 0,
            store = "appie",
            date = it.transactionMoment,
            storeProvidedId = it.transactionId,
            totalAmount = (it.total.amount.amount * 100).roundToInt()
        )
    }
}

fun floatEurosToCents(inString: String?): Int? {
    if (inString == null) return null
    val str = inString.replace(",",".")
    val float = str.toFloatOrNull() ?: return null
    val cents = float * 100
    return cents.roundToInt()
}

fun toFloat(inString: String): Float {
    return inString.replace(",",".").toFloat()
}

fun List<AppieReceiptItem>.asDatabaseModel(receiptId: Int): List<DatabaseReceiptItem> {
    return this.filter {
        // only keep actual products
        it.type == "product" && it.indicator != null
    }.mapIndexed { int, it ->
        DatabaseReceiptItem(
            item_id = 0,
            receiptId = receiptId,
            unitPrice = floatEurosToCents(it.price) ?: floatEurosToCents(it.amount)!!, // TODO check whats the situation for multiple of same product
            quantity = toFloat(it.quantity!!.replace("KG","")), // TODO handle KGs!!
            storeProvidedItemCode = null,
            description = it.description!!,
            totalPrice = floatEurosToCents(it.amount)!!,
            indexInsideReceipt = int
        )
    }
}