package zip.zaop.paylink.network

import kotlinx.serialization.Serializable
import zip.zaop.paylink.database.DatabaseReceipt
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// https://loyalty-app.jumbo.com/api/user/profile
// JumboUserProfile
@Serializable
data class Name(
    val givenName: String,
    val middleName: String,
    val familyName: String
)

@Serializable
data class AvgProfiling(
    val isAllowed: Boolean
)

@Serializable
data class TermsAndConditions(
    val general: Boolean
)

@Serializable
data class StorePreferences(
    val complexNumber: String
)

@Serializable
data class JumboUserProfile(
    val customerId: String,
    val creationDate: String,
    val type: String,
    val name: Name,
    val birthDate: String,
    val email: String,
    val avgProfiling: AvgProfiling,
    val termsAndConditions: TermsAndConditions,
    val storePreferences: StorePreferences,
    val lprCustomerStatus: String,
    val loyaltyCard: LoyaltyCard
)

// https://loyalty-app.jumbo.com/api/receipt/customer/overviews
// List<ReceiptListItem>
@Serializable
data class JumboReceiptListItem(
    val pointBalance: Int,
    val purchaseEndOn: String,
    val receiptSource: String,
    val store: Store,
    val transactionId: String
)

fun convertTimestamp(timestamp: String): String {
    val inputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val parsedTimestamp = LocalDateTime.parse(timestamp, inputFormat)

    // also accounts for daylight savings
    val cetTimezone = ZoneId.of("Europe/Paris")
    val utcTimestamp = parsedTimestamp.atZone(cetTimezone).toInstant()

    return utcTimestamp.toString()
}

fun List<JumboReceiptListItem>.asDatabaseModel(): List<DatabaseReceipt> {
    return this.map {
        DatabaseReceipt(
            id = 0,
            store = "jumbo",
            date = convertTimestamp(it.purchaseEndOn),
            storeProvidedId = it.transactionId,
            totalAmount = 0, // not provided by the overview API endpoint :-)
        )
    }
}

@Serializable
data class Store(
    val id: Int,
    val name: String
)

// https://loyalty-app.jumbo.com/api/receipt/156ioyowlf-ddbccc54-f0dc-11ed-b1a7-ac190a7f0000.json
@Serializable
data class JumboReceipt(
    val customerDetails: CustomerDetails,
    val id: String,
    val purchaseEndOn: String,
    val purchaseStartOn: String,
    val receiptImage: ReceiptImage,
    val receiptSource: String,
    val store: Store,
    val transactionId: String
)

@Serializable
data class CustomerDetails(
    val customerId: String,
    val loyaltyCard: LoyaltyCard
)

@Serializable
data class LoyaltyCard(
    val number: String
)

@Serializable
data class ReceiptImage(
    val image: String, // json hohoho
    val receiptPoints: ReceiptPoints,
    val type: String
)

@Serializable
data class ReceiptPoints(
    val earned: Int,
    val newBalance: Int,
    val oldBalance: Int,
    val redeemed: Int?
)

// the VERY funny "image data"
@Serializable
data class StupidImageData(
    val document: DocumentDetails,
)

@Serializable
data class DocumentDetails(
    val numberOfDocuments: String,
    val device: String,
    val codePage: String,
    val documents: List<Document>
)

@Serializable
data class Document(
    val codepage: String,
    val printSections: List<PrintSection>
)

@Serializable
data class PrintSection(
    val layout: String,
    val sectionId: String,
    val textObjects: List<TextObject>,
    val barcodeObject: BarcodeObject?,
    val printCommands: List<PrintCommand>
)

@Serializable
data class TextObject(
    val outputOptions: String,
    val textLines: List<TextLine>
)
// start reading after the SECOND "==========================================" (first & only text in first & only textline)
// stop reading before the        "Totaal                        " (first text in first & only textline)

@Serializable
data class TextLine(
    val linePrintAttributes: List<LinePrintAttribute>,
    val texts: List<Text>
)

@Serializable
data class LinePrintAttribute(
    val align: String,
    val cpl: String
)

@Serializable
data class Text(
    val text: String,
    val cpl: String?, // chars per line?
    val printAttributes: List<PrintAttribute>
)

@Serializable
data class PrintAttribute(
    val italic: Boolean?,
    val bold: Boolean?,
    val underline: Boolean?,
    val doubleHeight: Boolean?,
    val doubleWidth: Boolean?
)

@Serializable
data class BarcodeObject(
    val id: String,
    val barcodeType: String,
    val height: String,
    val width: String,
    val align: String,
    val outputOptions: String
)

@Serializable
data class PrintCommand(
    val command: String,
    val cmdData: String
)