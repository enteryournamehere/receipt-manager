package zip.zaop.paylink

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import zip.zaop.paylink.domain.Receipt
import zip.zaop.paylink.network.NetworkLidlReceiptItem
import zip.zaop.paylink.ui.theme.PaylinkTheme
import androidx.compose.runtime.livedata.observeAsState
import zip.zaop.paylink.domain.ReceiptItem
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class BonnetjesActivity : ComponentActivity() {
    private val TAG = "COMPLETE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val meep: BonnetjesViewModel by viewModels()
        meep.create(this)

        Log.i(TAG, "creating activiy")

        setContent {
            PaylinkTheme {
                MyApp(modifier = Modifier.fillMaxSize())
            }
        }

    }

    override fun onStart() {
        super.onStart()

        val meep: BonnetjesViewModel by viewModels()
        meep.create(this)
        meep.start(intent)
    }

    @Preview(widthDp = 320)
    @Composable
    fun MyApp(
        modifier: Modifier = Modifier,
        bonnetjesViewModel: BonnetjesViewModel = viewModel(),
    ) {
        val uiState by bonnetjesViewModel.uiState.collectAsState()
        val receipts by bonnetjesViewModel.receipts.collectAsState(initial = listOf())
        Surface(modifier) {
            Column {
                Row(
                    modifier = Modifier.padding(all = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(uiState.status, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Button(onClick = {
                        bonnetjesViewModel.getBonnetjes()
                    }) {
                        Text(text = stringResource(R.string.fetch_receipts))
                    }
                }
                LazyColumn {
//                    items(receipts, key = { it.date }) { bonnetje ->
//                        TempNewBonCard(bonnetje)
//                    }
                    items(receipts) { bonnetje -> // TODO: it.id here is duplicate 0
                        BonnetjeCard(
                            bonnetje,
                            onExpandClicked = { bonnetjesViewModel.fetchReceiptInfo(it) })
                    }
                }

            }
        }
    }

    @Composable
    fun TempNewBonCard(data: Receipt, modifier: Modifier = Modifier) {
        Text(data.date)
    }


    @Composable
    private fun ItemCard(data: ReceiptItem) {
        var selected by remember { mutableStateOf(false) }
        val backgroundColor: Color by animateColorAsState(targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
        Row(
            modifier = Modifier
                .padding(bottom = 7.dp)
                .clip(RoundedCornerShape(size = 20.dp))
                .background(backgroundColor)
                .selectable(selected = selected, enabled = true, onClick = { selected = !selected })
                .padding(all = 10.dp)
        ) {
            Text(
                data.description, modifier = Modifier.weight(1f),
                color = if (!selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primaryContainer
            )
            Text(
                data.totalPrice.toString(), modifier = Modifier,
                color = if (!selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primaryContainer
            )
        }
    }

    private val previewListItem = NetworkLidlReceiptItem(
        currentUnitPrice = "String",
        quantity = "String",
        isWeight = false,
        originalAmount = "String",
        extendedAmount = "5 euro",
        description = "Among us",
        taxGroup = "String",
        taxGroupName = "String",
        codeInput = "String",
    )

    private val previewData = BonnetjeUiState(
        id = "test",
        amount = "4,84",
        items = listOf(previewListItem),
        date = "28/04/2023 at 17:02"
    )

//    @Preview
//    @Composable
//    private fun BonnetjeCardPreview() {
//        BonnetjeCard(data = previewData, previewForceOpen = true)
//    }


    fun convertDateTimeString(dateTimeString: String): String {
        val offsetDateTime = OffsetDateTime.parse(dateTimeString)
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'at' HH:mm")
        return offsetDateTime.format(formatter)
    }

    @Composable
    private fun BonnetjeCard(
        data: Receipt, onExpandClicked: (Int) -> Unit = {},
        previewForceOpen: Boolean = false
    ) {
        var expanded by rememberSaveable { mutableStateOf(previewForceOpen) }
        var niceTimeString = remember { convertDateTimeString(data.date) }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
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
                Row {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(12.dp)
                    ) {
                        Text(text = niceTimeString)
                        Text(
                            text = "€" + 0, // TODO: sum price of items, or just put it in the dabababas
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                    IconButton(onClick = {
                        expanded = !expanded; if (data.items.isNullOrEmpty()) onExpandClicked(data.id)
                    }) {
                        if (expanded) {
                            Icon(Icons.Rounded.ExpandLess, "show_less")
                        } else {
                            Icon(Icons.Rounded.ExpandMore, "show_more")
                        }

                    }
                }
                if (expanded) {
                    if (data.items.isNullOrEmpty()) {

                        Text(
                            "Loading...",
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    } else {
                        Column {
                            data.items.forEach { item ->
                                ItemCard(item)
                            }
                        }
                    }
                }

            }
        }
    }
}
