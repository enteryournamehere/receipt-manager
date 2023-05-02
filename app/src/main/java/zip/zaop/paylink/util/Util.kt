package zip.zaop.paylink.util

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter


fun convertCentsToString(cents: Int, separator: String = ",", withEuro: Boolean = true): String {
    val negative = cents < 0;
    val cents1 = kotlin.math.abs(cents)
    val euros = cents1 / 100
    val remainder = cents1 % 100
    val remainderString = remainder.toString().padStart(2, '0')
    return (if (withEuro) "€" else "") + (if (negative) "-" else "") + "$euros$separator$remainderString"
}

fun convertDateTimeString(dateTimeString: String): String {
    val offsetDateTime = OffsetDateTime.parse(dateTimeString)
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'at' HH:mm")
    return offsetDateTime.format(formatter)
}