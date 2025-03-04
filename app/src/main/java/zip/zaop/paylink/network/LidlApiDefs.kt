package zip.zaop.paylink.network

import android.util.Xml
import kotlinx.serialization.Serializable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import zip.zaop.paylink.database.DatabaseReceipt
import zip.zaop.paylink.database.DatabaseReceiptItem
import java.io.IOException
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

fun NetworkLidlReceiptDetails.asDatabaseModel(receiptId: Int): List<DatabaseReceiptItem> {
    val parser: XmlPullParser = Xml.newPullParser()
    parser.setInput(this.htmlPrintedReceipt.byteInputStream(), null)
    // need to be OK with unquoted attributes like "<style type=text/css>"
    parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true);
    parser.nextTag()
    val articles: MutableList<DatabaseReceiptItem> = mutableListOf()
    val seenIds: MutableSet<String> = mutableSetOf()
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            continue
        }
        if (parser.name == "head") {
            skip(parser)
        } else if (parser.name == "span") {
            // each receipt item has 6+ spans with the same ID
            val id = parser.getAttributeValue(null, "id") ?: continue
            if (id in seenIds) {
                continue
            }
            seenIds.add(id)
            val s = parseArticle(parser, receiptId, articles.size)

            if (s != null) {
                println(s)
                articles.add(s)
            }
        }
    }

    return articles
}

private fun parseArticle(parser: XmlPullParser, receiptId: Int, indexInsideReceipt: Int): DatabaseReceiptItem? {
    parser.require(XmlPullParser.START_TAG, null, "span")
    val attrs: MutableMap<String, String> = mutableMapOf()
    for (i in 0 until parser.attributeCount) {
        val name = parser.getAttributeName(i)
        val value = parser.getAttributeValue(i)
        attrs[name] = value
    }

    if (attrs["class"]?.contains("article") != true) {
        return null
    }

    val quantityString = when (attrs["data-art-quantity"] ) {
        null -> "1"
        else -> attrs["data-art-quantity"]!!
    }
    val quantityFloat = parseFloat(quantityString.replace(",", "."))
    val unitPrice = floatEurosToCents(attrs["data-unit-price"])!!
    return DatabaseReceiptItem(
        item_id = 0,
        receiptId = receiptId,
        unitPrice = unitPrice,
        quantity = quantityFloat, // TODO handle KGs!!
        storeProvidedItemCode = attrs["data-art-id"],
        description = attrs["data-art-description"]!!,
        totalPrice = (quantityFloat * unitPrice).roundToInt(), // TODO: take value from receipt HTML, and handle discounts
        indexInsideReceipt = indexInsideReceipt,
        hasBeenSentToWbw = false,
    )
}

@Throws(XmlPullParserException::class, IOException::class)
private fun skip(parser: XmlPullParser) {
    if (parser.eventType != XmlPullParser.START_TAG) {
        throw IllegalStateException()
    }
    var depth = 1
    while (depth != 0) {
        when (parser.next()) {
            XmlPullParser.END_TAG -> depth--
            XmlPullParser.START_TAG -> depth++
        }
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
    val hasHtmlDocument: Boolean,
    val isHtml: Boolean,
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
    val couponsUsed: List<UsedCoupon>,
    val isFavorite: Boolean,
    val date: String,
    val totalAmount: Float,
    val store: StoreInfo,
    val languageCode: String,
    val htmlPrintedReceipt: String,
    val printedReceiptState: String,
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
data class UsedCoupon(
//    val couponId: String,
    val title: String,
    val discount: String,
//    val footerTitle: String,
//    val footerDescription: String,
    val block2Description: String,
//    val image: String
)

