package zip.zaop.paylink

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import zip.zaop.paylink.domain.Receipt
import zip.zaop.paylink.domain.ReceiptItem
import zip.zaop.paylink.ui.theme.PaylinkTheme
import zip.zaop.paylink.util.convertCentsToString
import zip.zaop.paylink.util.convertDateTimeString

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

    @Preview
    @Composable
    fun TopBaa(
        status: String = "hi",
        onClickHandler: () -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = Modifier.padding(all = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(status, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    onClickHandler()
                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(Icons.Rounded.Refresh, "get latest bonnetjes icon")
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.fetch_receipts))
            }
        }
    }


    @Composable
    fun MyApp(
        modifier: Modifier = Modifier,
        bonnetjesViewModel: BonnetjesViewModel = viewModel(),
    ) {
        val uiState by bonnetjesViewModel.uiState.collectAsState()
        val receipts by bonnetjesViewModel.receiptsPlus.collectAsState(initial = listOf())

        Surface(modifier) {
            Column {
                TopBaa(uiState.status, { bonnetjesViewModel.getBonnetjes() })
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(receipts) { receipt ->
                        BonnetjeCard(
                            receipt,
                            onExpandClicked = { bonnetjesViewModel.fetchReceiptInfo(it) },
                            onItemSelected = { receipty, index, selected ->
                                bonnetjesViewModel.select(receipty, index, selected)
                            },
                        )
                    }
                }
                val clipboardManager = LocalClipboardManager.current
                if (uiState.selectedCount > 0)
                    BottomActionBar(uiState.selectedCount, uiState.selectedAmount) {
                        clipboardManager.setText(AnnotatedString(bonnetjesViewModel.getSelectedAmountToCopy()))

                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
                            Toast.makeText(
                                applicationContext,
                                "Copied to clipboard",
                                Toast.LENGTH_SHORT
                            ).show()
                    }
            }
        }
    }

    @Preview
    @Preview(uiMode = UI_MODE_NIGHT_YES)
    @Composable
    private fun BottomBarPreview() {
        PaylinkTheme {
            Surface {
                BottomActionBar()
            }
        }
    }

    @Composable
    private fun BottomActionBar(
        selectedCount: Int = 0,
        selectedAmount: Int = 0,
        onCopyClicked: () -> Unit = {}
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .fillMaxWidth()
                .padding(all = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$selectedCount selected",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(
                onClick = { onCopyClicked() },
                colors = ButtonDefaults.buttonColors(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(Icons.Rounded.ContentCopy, "Copy total")
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    convertCentsToString(
                        selectedAmount,
                        withEuro = false
                    ),
                )
            }
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Button(
                onClick = {
                    Toast.makeText(applicationContext, "Not yet implemented", Toast.LENGTH_SHORT)
                        .show()
                },
                colors = ButtonDefaults.buttonColors(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(Icons.Rounded.ArrowOutward, "send to wbw")
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text("To WBW")
            }
        }
    }

    @Composable
    private fun ItemCard(
        data: ReceiptItem,
        selected: Boolean,
        showSelectionIcon: Boolean,
        onSelectionChanged: (Boolean) -> Unit
    ) {
        val backgroundColor: Color by animateColorAsState(
            targetValue =
            if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
        )
        Row(
            modifier = Modifier
                .padding(bottom = 7.dp)
                .clip(RoundedCornerShape(size = 20.dp))
                .background(backgroundColor)
                .selectable(selected = selected, enabled = true, onClick = {
                    onSelectionChanged(!selected)
                })
                .padding(all = 10.dp)
                .defaultMinSize(minHeight = 24.dp)
        ) {
            val contentColor =
                if (!selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            AnimatedVisibility(visible = showSelectionIcon) {
                Icon(
                    if (selected) Icons.Rounded.CheckCircleOutline
                    else Icons.Rounded.RadioButtonUnchecked,
                    "Selected",
                    tint = contentColor
                )
            }
            if (showSelectionIcon)
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(
                data.description, modifier = Modifier.weight(1f),
                color = contentColor
            )
            Text(
                remember { convertCentsToString(data.totalPrice) }, modifier = Modifier,
                color = contentColor
            )
        }
    }

    private val previewData = FullInfo(
        receipt = Receipt(
            id = 1,
            items = listOf(
                ReceiptItem(
                    unitPrice = 500,
                    quantity = 1f,
                    description = "Vijf euro",
                    storeProvidedItemCode = null,
                    totalPrice = 500,
                    indexInsideReceipt = 0,
                    id = 2432234
                ), ReceiptItem(
                    unitPrice = 195,
                    quantity = 3.1f,
                    description = "Dure dingen",
                    storeProvidedItemCode = null,
                    totalPrice = 3894,
                    indexInsideReceipt = 1,
                    id = 66425
                )
            ),
            date = "2023-04-28T17:55:04+00:00",
            storeProvidedId = "220006738220230428206050",
            store = "lidl",
            totalAmount = 4394
        ), selectedItems = setOf(0)
    )


    @Preview
    @Preview(uiMode = UI_MODE_NIGHT_YES)
    @Composable
    private fun CardPreview() {
        PaylinkTheme {
            Surface {
                BonnetjeCard(data = previewData, previewForceOpen = true)
            }
        }
    }

    @Composable
    private fun BonnetjeCard(
        data: FullInfo, onExpandClicked: (Receipt) -> Unit = {},
        previewForceOpen: Boolean = false,
        onItemSelected: (Receipt, ReceiptItem, Boolean) -> Unit = { r, i, b -> },
    ) {
        var expanded by rememberSaveable { mutableStateOf(previewForceOpen) }

        Card(
            colors = CardDefaults.elevatedCardColors(),
            elevation = CardDefaults.elevatedCardElevation(),
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
                        Text(text = remember { convertDateTimeString(data.receipt.date) })
                        Text(
                            text = remember { convertCentsToString(data.receipt.totalAmount) }, // TODO: sum price of items, or just put it in the dabababas
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                    if (data.selectedItems.isNotEmpty()) {
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(data.selectedItems.size.toString())
                        }
                    }
                    IconButton(onClick = {
                        expanded = !expanded
                        if (data.receipt.items.isNullOrEmpty()) onExpandClicked(data.receipt)
                    }) {
                        if (expanded) {
                            Icon(Icons.Rounded.ExpandLess, "show_less")
                        } else {
                            Icon(Icons.Rounded.ExpandMore, "show_more")
                        }

                    }
                }
                if (expanded) {
                    if (data.receipt.items.isNullOrEmpty()) {
                        Text(
                            "Loading...",
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    } else {
                        Column {
                            data.receipt.items.forEach { item ->
                                val isSelected =
                                    data.selectedItems.contains(item.indexInsideReceipt)

                                ItemCard(item, selected = isSelected, showSelectionIcon = data.selectedItems.isNotEmpty()) {
                                    onItemSelected(data.receipt, item, it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
