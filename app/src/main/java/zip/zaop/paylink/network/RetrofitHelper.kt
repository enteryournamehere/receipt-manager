package zip.zaop.paylink.network

import android.content.Context
import com.franmontiel.persistentcookiejar.ClearableCookieJar
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

fun newHttpClient(context: Context, extraHeaders: Map<String, String>): OkHttpClient {
    val cookieJar: ClearableCookieJar =
        PersistentCookieJar(SetCookieCache(), SharedPrefsCookiePersistor(context))

    val logging = HttpLoggingInterceptor()
    logging.setLevel(HttpLoggingInterceptor.Level.NONE)
    var builder = OkHttpClient.Builder()
    builder = builder.addNetworkInterceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
        for ((key, value) in extraHeaders.entries) {
            requestBuilder.addHeader(key, value)
        }
        val request = requestBuilder.build()
        chain.proceed(request)
    }
    return builder
        .cookieJar(cookieJar)
        .addInterceptor(logging)
        .build()
}

@OptIn(ExperimentalSerializationApi::class)
private val JsonCool = Json { ignoreUnknownKeys = true; explicitNulls = false }

fun <T> newRetrofit(
    context: Context,
    baseUrl: String,
    service: Class<T>,
    extraHeaders: Map<String, String> = emptyMap()
): T {
    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(
            @OptIn(ExperimentalSerializationApi::class)
            JsonCool.asConverterFactory("application/json".toMediaType())
        )
        .client(newHttpClient(context, extraHeaders))
        .build()

    return retrofit.create(service)
}