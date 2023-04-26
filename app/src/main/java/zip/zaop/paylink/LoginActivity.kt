package zip.zaop.paylink

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity


class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        Log.i("TEST", "oh no 1111")

        val webView: WebView = findViewById(R.id.webview)
//        WebStorage.getInstance().deleteAllData()
        webView.webViewClient = MyWebViewClient()
        CookieManager.getInstance().removeAllCookies { value ->
            // Handle the result of the removeAllCookies() method here
            if (value) {
                // The cookies were removed successfully
            } else {
                // There was an error while removing the cookies
            }
        }
        webView.settings.javaScriptEnabled = true
//        webView.loadUrl("https://example.com")
        webView.loadUrl("https://accounts.lidl.com/connect/authorize?client_id=LidlPlusNativeClient&scope=openid%20profile%20offline_access%20lpprofile%20lpapis&response_type=code&redirect_uri=com.lidlplus.app%3A%2F%2Fcallback&code_challenge=1sHqkhuZZ0EUnNI2ixfqJqA1QXgf9HnLBia8IDbQw60&code_challenge_method=S256&Country=NL&language=NL-NL")
        Log.i("TEST", "Haha hi")
        Log.i("TEST", "oh no")
    }

//    fun getTokens(code: String) {
//
//    }
}

private class MyWebViewClient : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        Log.v("TEST", url ?: "beepy")
        // let webview load it
        if (url?.startsWith("https://accounts.lidl.com") == true) return false;
        // we are interested in com.lidlplus.app://callback
        if (url?.startsWith("com.lidlplus.app") == true) {
            val peepy = Uri.parse(url).getQueryParameter("code")
            Log.v("TEST", peepy?: "FAKE")
        }

        return true
    }
}
