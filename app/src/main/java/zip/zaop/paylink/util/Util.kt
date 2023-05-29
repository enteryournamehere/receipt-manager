package zip.zaop.paylink.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
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

sealed class ErrorResponse{
    data class Custom<T>(val data: T): ErrorResponse()
    data class Text(val data: String): ErrorResponse()
}

inline fun <reified T> parseHttpException(e: HttpException): ErrorResponse {
    @OptIn(ExperimentalSerializationApi::class)
    val jsonThingy = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val responseString = e.response()?.errorBody()?.string();
    if (responseString != null) {
        return try {
            ErrorResponse.Custom<T>(jsonThingy.decodeFromString(responseString))
        } catch (e: Exception) {
            ErrorResponse.Text(responseString)
        }
    }
    return ErrorResponse.Text("")
}