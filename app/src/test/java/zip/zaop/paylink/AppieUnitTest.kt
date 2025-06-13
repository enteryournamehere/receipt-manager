package zip.zaop.paylink

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import zip.zaop.paylink.network.AppieReceiptDetailsResponse
import zip.zaop.paylink.network.asDatabaseModel

@RunWith(RobolectricTestRunner::class)
class AppieUnitTest {
    private val JsonCool = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun appie_bonus() {
        val json = """
            {
              "receiptUiItems": [
                {
                  "type": "ah-logo",
                  "style": "AH"
                },
                {
                  "type": "text",
                  "value": "Albert Heijn",
                  "alignment": "CENTER",
                  "isBold": false
                },
                {
                  "type": "text",
                  "value": "Address",
                  "alignment": "CENTER",
                  "isBold": false
                },
                {
                  "type": "text",
                  "value": "phone number",
                  "alignment": "CENTER",
                  "isBold": false
                },
                {
                  "type": "spacer"
                },
                {
                  "type": "products-header"
                },
                {
                  "type": "divider",
                  "centerText": null
                },
                {
                  "type": "product",
                  "quantity": null,
                  "description": "BONUSKAART",
                  "price": null,
                  "amount": "xx1234",
                  "indicator": null
                },
                {
                  "type": "product",
                  "quantity": null,
                  "description": "AIRMILES NR. *",
                  "price": null,
                  "amount": "xx2345",
                  "indicator": null
                },
                {
                  "type": "product",
                  "quantity": "2",
                  "description": "LINDT 78%",
                  "price": "3,49",
                  "amount": "6,98",
                  "indicator": "B"
                },
                {
                  "type": "product",
                  "quantity": "1",
                  "description": "DELI CHOCO",
                  "price": null,
                  "amount": "3,49",
                  "indicator": ""
                },
                {
                  "type": "product",
                  "quantity": "1",
                  "description": "AH QUICHE",
                  "price": null,
                  "amount": "3,99",
                  "indicator": "B"
                },
                {
                  "type": "product",
                  "quantity": "1",
                  "description": "AH QUICHE",
                  "price": null,
                  "amount": "3,99",
                  "indicator": "B"
                },
                {
                  "type": "divider",
                  "centerText": null
                },
                {
                  "type": "subtotal",
                  "quantity": "5",
                  "text": "SUBTOTAAL",
                  "amount": "18,45"
                },
                {
                  "type": "spacer"
                },
                {
                  "type": "product",
                  "quantity": "BONUS",
                  "description": "ALLEAHQUICHE",
                  "price": null,
                  "amount": "-3,99",
                  "indicator": null
                },
                {
                  "type": "product",
                  "quantity": "BONUS",
                  "description": "ALLELINDTEXC",
                  "price": null,
                  "amount": "-1,75",
                  "indicator": null
                },
                {
                  "type": "spacer"
                },
                {
                  "type": "total",
                  "label": "UW VOORDEEL",
                  "price": "5,74"
                },
                {
                  "type": "product",
                  "quantity": null,
                  "description": "Waarvan",
                  "price": null,
                  "amount": null,
                  "indicator": null
                },
                {
                  "type": "product",
                  "quantity": null,
                  "description": "BONUS BOX",
                  "price": null,
                  "amount": "0,00",
                  "indicator": null
                },
                {
                  "type": "divider",
                  "centerText": null
                },
                {
                  "type": "spacer"
                },
                {
                  "type": "total",
                  "label": "TOTAAL",
                  "price": "12,71"
                },
                {
                  "type": "text",
                  "value": "SPAARACTIES:",
                  "alignment": "LEFT",
                  "isBold": true
                },
                {
                  "type": "product",
                  "quantity": "1",
                  "description": "eSPAARZEGEL",
                  "price": null,
                  "amount": null,
                  "indicator": null
                },
                {
                  "type": "product",
                  "quantity": "5",
                  "description": "MIJN AH MILES",
                  "price": null,
                  "amount": null,
                  "indicator": null
                },
                {
                  "type": "spacer"
                },
                {
                  "type": "text",
                  "value": "BETAALD MET:",
                  "alignment": "LEFT",
                  "isBold": false
                },
                {
                  "type": "product",
                  "quantity": null,
                  "description": "PINNEN",
                  "price": null,
                  "amount": "12,71",
                  "indicator": null
                },
                {
                  "type": "spacer"
                },
                {
                  "type": "four-text-column",
                  "first": "POI: 12345678",
                  "second": null,
                  "third": "",
                  "fourth": null
                },
                {
                  "type": "four-text-column",
                  "first": "KLANTTICKET",
                  "second": null,
                  "third": "Terminal",
                  "fourth": "ABC123"
                },
                {
                  "type": "four-text-column",
                  "first": "Merchant",
                  "second": "1234567",
                  "third": "Periode",
                  "fourth": "1234"
                },
                {
                  "type": "four-text-column",
                  "first": "Transactie",
                  "second": "12341234",
                  "third": "PAR: xxx",
                  "fourth": null
                },
                {
                  "type": "four-text-column",
                  "first": "F",
                  "second": null,
                  "third": "Maestro",
                  "fourth": null
                },
                {
                  "type": "four-text-column",
                  "first": "(A00000000xxxxx)",
                  "second": null,
                  "third": "MAESTRO",
                  "fourth": null
                },
                {
                  "type": "four-text-column",
                  "first": "Kaart",
                  "second": "123456xxxxxxxxx1234",
                  "third": "Kaartserienummer",
                  "fourth": "1"
                },
                {
                  "type": "four-text-column",
                  "first": "BETALING",
                  "second": null,
                  "third": "Datum",
                  "fourth": "01/01/2025 12:00"
                },
                {
                  "type": "four-text-column",
                  "first": "Autorisatiecode",
                  "second": "ABC123",
                  "third": "Totaal",
                  "fourth": "12,71 EUR"
                },
                {
                  "type": "four-text-column",
                  "first": "Contactless",
                  "second": null,
                  "third": "Leesmethode CHIP",
                  "fourth": null
                },
                {
                  "type": "spacer"
                },
                {
                  "type": "vat",
                  "left": "BTW",
                  "center": "OVER",
                  "right": "EUR"
                },
                {
                  "type": "vat",
                  "left": "9%",
                  "center": "11,66",
                  "right": "1,05"
                },
                {
                  "type": "vat",
                  "left": "TOTAAL",
                  "center": "11,66",
                  "right": "1,05"
                },
                {
                  "type": "spacer"
                },
                {
                  "type": "tech-info",
                  "store": 1,
                  "lane": 1,
                  "transaction": 1,
                  "operator": null,
                  "dateTime": "2025-01-01T12:00:00Z"
                },
                {
                  "type": "spacer"
                },
                {
                  "type": "text",
                  "value": "Vragen over je kassabon?",
                  "alignment": "CENTER",
                  "isBold": false
                },
                {
                  "type": "text",
                  "value": "Onze kassamedewerkers",
                  "alignment": "CENTER",
                  "isBold": false
                },
                {
                  "type": "text",
                  "value": "helpen je graag.",
                  "alignment": "CENTER",
                  "isBold": false
                },
                {
                  "type": "spacer"
                }
              ],
              "storeId": 1,
              "transactionMoment": "2025-01-01T12:00:00Z"
            }
        """.trimIndent()
        val receipt = JsonCool.decodeFromString<AppieReceiptDetailsResponse>(json)
        val items = receipt.receiptUiItems.asDatabaseModel(0)

        assertEquals(1, items.count { it.description == "LINDT 78%" && it.unitPrice == 349 && it.quantity == 2f && it.totalPrice == 698 && it.totalDiscount == 0 })
        assertEquals(2, items.count { it.description == "AH QUICHE" && it.unitPrice == 399 && it.quantity == 1f && it.totalPrice == 399 && it.totalDiscount == 0 })
        assertEquals(1, items.count { it.description == "DELI CHOCO" && it.unitPrice == 349 && it.quantity == 1f && it.totalPrice == 349 && it.totalDiscount == 0 })
        assertEquals(1, items.count { it.description == "ALLEAHQUICHE" && it.totalPrice == -399})
        assertEquals(1, items.count { it.description == "ALLELINDTEXC" && it.totalPrice == -175})
    }
}