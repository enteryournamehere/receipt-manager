package zip.zaop.paylink

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.openid.appauth.*
import net.openid.appauth.AuthorizationService.TokenResponseCallback
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview

import zip.zaop.paylink.BonnetjesViewModel.*;
import zip.zaop.paylink.ui.theme.PaylinkTheme

const val TICKET_URL = "https://tickets.lidlplus.com/api/v1/NL/list/1"

class BonnetjesActivity : ComponentActivity() {
    private val TAG = "COMPLETE";


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val meep: BonnetjesViewModel by viewModels()

        meep.create(this)


        Log.i(TAG, "creating activiy")

        setContent {
            PaylinkTheme {
                MyApp()
            }
        }

    }
    /*
    //    fun makeRequest(uri: Uri, accessToken: String) {
    //        mExecutor!!.submit {
    //            try {
    //                val conn: HttpURLConnection =
    //                    DefaultConnectionBuilder.INSTANCE.openConnection(
    //                        uri
    //                    )
    //                conn.setRequestProperty("Authorization", "Bearer $accessToken")
    //                conn.setRequestProperty("App-Version", "999.99.9")
    //                conn.setRequestProperty("Operating-System", "iOS")
    //                conn.setRequestProperty("App", "com.lidl.eci.lidl.plus")
    //                conn.setRequestProperty("Accept-Language", "NL")
    //
    //                val response: String = conn.inputStream.source().buffer()
    //                    .readString(Charset.forName("UTF-8"))
    //
    //                val test = JSONObject(response)
    //
    //                Log.i(TAG, "RESPONSED!!!! $response")
    //
    //                runOnUiThread { displayBonnetjes(test) }
    //            }catch (ioEx: IOException) {
    //                Log.e(TAG, "Network error when querying userinfo endpoint", ioEx);
    //            } catch (jsonEx: JSONException) {
    //                Log.e(TAG, "Failed to parse userinfo response");
    //            }
    //        }
    //    }*/

    override fun onStart() {
        super.onStart()


        val meep: BonnetjesViewModel by viewModels()
        meep.create(this)

        meep.start(intent)


    }

    @Preview(widthDp = 320)
    @Composable
    fun MyApp(
        bonnetjesViewModel: BonnetjesViewModel = viewModel(),
        modifier: Modifier = Modifier
    ) {
        val uiState by bonnetjesViewModel.uiState.collectAsState()
        Surface(modifier) {
            Column {
                Row(
                    modifier = Modifier.padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(uiState.status, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Button(onClick = {
                        bonnetjesViewModel.getBonnetjes()
                    }) {
                        Text(text = "fetch receipts")
                    }
                }
                LazyColumn {
                    items(uiState.bonnetjes) { bonnetje ->
                        BonnetjeCard(bonnetje)
                    }
                }

            }
        }
    }

    @Composable
    private fun BonnetjeCard(data: BonnetjeUiState) {
        var expanded by rememberSaveable { mutableStateOf(false) }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
            ) {
                Row() {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(12.dp)
                    ) {
                        Text(text = data.date)
                        Text(
                            text = "€" + data.amount,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        if (expanded) {
                            Icon(Icons.Rounded.ExpandLess, "show_less")
                        } else {
                            Icon(Icons.Rounded.ExpandMore, "show_more")
                        }

                    }
                }
                if (expanded)
                    Text(
                        "According to all known laws of aviation, there is no way a bee should be able to fly. The bee, of course, flies anyway.",
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
            }
        }
    }
}