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

fun NetworkLidlReceiptList.asDatabaseModel(accountId: Long): List<DatabaseReceipt> {
    return tickets.map {
        DatabaseReceipt(
            id = 0,
            accountId = accountId,
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
    var currentArticle: DatabaseReceiptItem? = null
    var currentDiscount = 0

    var candidateAttributes: Map<String, String>? = null

    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            XmlPullParser.START_TAG -> {
                if (parser.name == "span") {
                    candidateAttributes = null

                    // each receipt item has 6+ spans with the same ID
                    val id = parser.getAttributeValue(null, "id")
                    val attrs = (0 until parser.attributeCount).associate {
                        parser.getAttributeName(it) to parser.getAttributeValue(it)
                    }

                    if (attrs["class"]?.contains("discount") == true) {
                        currentDiscount = parseDiscount(parser)
                        continue
                    }

                    if (id != null && id !in seenIds &&
                        attrs["class"]?.contains("css_bold") == true &&
                        attrs["data-art-description"] != null
                    ) {
                        candidateAttributes = attrs
                        seenIds.add(id)
                    }
                } else if (parser.name == "head") {
                    skip(parser)
                }
            }
            XmlPullParser.TEXT -> {
                if (candidateAttributes != null) {
                    val text = parser.text.trim()
                    val descriptionAttr = candidateAttributes["data-art-description"]

                    if (text.isNotEmpty() && descriptionAttr?.startsWith(text) == true) {
                        val newArticle = buildItemFromAttributes(candidateAttributes, receiptId)

                        if (newArticle != null) {
                            if (currentArticle != null) {
                                val finalPreviousArticle = currentArticle.copy(
                                    totalDiscount = currentDiscount,
                                    indexInsideReceipt = articles.size
                                )
                                articles.add(finalPreviousArticle)
                                currentDiscount = 0
                            }
                            currentArticle = newArticle
                        }
                    }
                    candidateAttributes = null
                }
            }
        }
    }

    if (currentArticle != null) {
        val finalArticle = currentArticle.copy(
            totalDiscount = currentDiscount,
            indexInsideReceipt = articles.size
        )
        articles.add(finalArticle)
    }
    return articles
}

private fun buildItemFromAttributes(attrs: Map<String, String>, receiptId: Int): DatabaseReceiptItem? {
    if (attrs["class"]?.contains("article") != true) {
        return null
    }

    val quantityString = attrs["data-art-quantity"] ?: "1"
    val quantityFloat = parseFloat(quantityString.replace(",", "."))
    val unitPrice = floatEurosToCents(attrs["data-unit-price"])

    if (unitPrice == null) {
        return null
    }

    return DatabaseReceiptItem(
        item_id = 0,
        receiptId = receiptId,
        unitPrice = unitPrice,
        quantity = quantityFloat,
        storeProvidedItemCode = attrs["data-art-id"],
        description = attrs["data-art-description"]!!,
        totalPrice = (quantityFloat * unitPrice).roundToInt(),
        indexInsideReceipt = -1,
        hasBeenSentToWbw = false,
        totalDiscount = 0,
    )
}

private fun parseDiscount(parser: XmlPullParser): Int {
    parser.require(XmlPullParser.START_TAG, null, "span")
    val txt = parser.nextText();
    return try {
        (-txt.replace(",", ".").toFloat() * 100).roundToInt()
    }
    catch (e: NumberFormatException) {
        0
    }
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

