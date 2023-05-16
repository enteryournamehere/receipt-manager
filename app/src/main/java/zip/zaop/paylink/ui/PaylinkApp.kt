package zip.zaop.paylink.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import zip.zaop.paylink.BonnetjesViewModel
import zip.zaop.paylink.FullInfo
import zip.zaop.paylink.database.LinkablePlatform
import zip.zaop.paylink.domain.Receipt
import zip.zaop.paylink.domain.ReceiptItem
import zip.zaop.paylink.ui.theme.PaylinkTheme
import zip.zaop.paylink.util.convertCentsToString
import zip.zaop.paylink.util.convertDateTimeString

@Composable
fun TopBar(
    status: String,
    onClickHandler: (LinkablePlatform) -> Unit
) {
    Row(
        modifier = Modifier.padding(all = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(status, modifier = Modifier.weight(1f))
        Button(
            onClick = {
                onClickHandler(LinkablePlatform.LIDL)
            },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(Icons.Rounded.Refresh, "get latest bonnetjes icon")
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(text = "lidl")
        }
        Spacer(modifier = Modifier.width(6.dp))
        Button(
            onClick = {
                onClickHandler(LinkablePlatform.APPIE)
            },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(Icons.Rounded.Refresh, "get latest bonnetjes icon")
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(text = "ah")
        }
        Spacer(modifier = Modifier.width(6.dp))
        Button(
            onClick = {
                onClickHandler(LinkablePlatform.JUMBO)
            },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            Icon(Icons.Rounded.Refresh, "get latest bonnetjes icon")
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(text = "jumbo")
        }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyApp(
    applicationContext: Context,
) {
    val showToast = { text: String ->
        Toast.makeText(
            applicationContext,
            text,
            Toast.LENGTH_SHORT
        ).show()
    }

    val navController = rememberNavController()

    val items = listOf(
        Screen.Receipts,
        Screen.Accounts,
    )

    val intent = LocalContext.current.findActivity()!!.intent

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->

                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,

                        onClick = {
                            navController.navigate(screen.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                // on the back stack as users select items
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = "${screen.text} icon",
                            )
                        }
                    )
                }
            }
        },
        content = { padding ->
            NavHost(
                navController,
                startDestination = Screen.Receipts.route,
                Modifier.padding(padding)
            ) {
                composable(Screen.Receipts.route) {
                    Surface { BonnetjesComposable(showToast = showToast, intent = intent) }
                }
                composable(Screen.Accounts.route) {
                    Surface(Modifier.fillMaxSize()) { AccountsComposable() }
                }
            }
        }
    )
}

@Composable
private fun BonnetjesComposable(
    showToast: (String) -> Unit,
    intent: Intent,
    bonnetjesViewModel: BonnetjesViewModel = viewModel(),
) {
    bonnetjesViewModel.start(intent)
    val uiState by bonnetjesViewModel.uiState.collectAsState()
    val receipts by bonnetjesViewModel.receiptsPlus.collectAsState(initial = listOf())

    Column {
        TopBar(uiState.status) {
            bonnetjesViewModel.getBonnetjes(it)
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(receipts) { receipt ->
                BonnetjeCard(
                    receipt,
                    onExpandClicked = {
                        val platform = when (it.store) {
                            "lidl" -> LinkablePlatform.LIDL
                            "appie" -> LinkablePlatform.APPIE
                            "jumbo" -> LinkablePlatform.JUMBO
                            else -> null
                        }
                        if (platform != null) {
                            bonnetjesViewModel.fetchReceiptInfo(
                                platform,
                                it
                            )
                        }
                    },
                    onItemSelected = { receipty, index, selected ->
                        bonnetjesViewModel.select(receipty, index, selected)
                    },
                )
            }
        }
        val clipboardManager = LocalClipboardManager.current
        if (uiState.selectedCount > 0)
            BottomActionBar(
                uiState.selectedCount,
                uiState.selectedAmount,
                showToast
            ) {
                clipboardManager.setText(AnnotatedString(bonnetjesViewModel.getSelectedAmountToCopy()))

                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
                    showToast("Copied to clipboard")
            }
    }
}

sealed class Screen(val route: String, val text: String, val icon: ImageVector) {
    object Receipts : Screen("profile", "fssfddsf", Icons.Rounded.ReceiptLong)
    object Accounts : Screen("friendslist", "bbbbbb", Icons.Rounded.ManageAccounts)
}


@Composable
private fun BottomActionBar(
    selectedCount: Int,
    selectedAmount: Int,
    showToast: (String) -> Unit,
    onCopyClicked: () -> Unit,
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
                showToast("Not yet implemented")
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
private fun BonnetjeCard(
    data: FullInfo, onExpandClicked: (Receipt) -> Unit,
    onItemSelected: (Receipt, ReceiptItem, Boolean) -> Unit,
    /// Just for the preview
    previewForceOpen: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(previewForceOpen) }
    val clickHandler = {
        expanded = !expanded
        if (data.receipt.items.isNullOrEmpty()) onExpandClicked(data.receipt)
    }

    ElevatedCard(
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = clickHandler
            )
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
            CardHeader(data, clickHandler, expanded)
            if (expanded) CardItemList(data = data, onItemSelected = onItemSelected)
        }
    }
}

@Composable
private fun CardHeader(data: FullInfo, clickHandler: () -> Unit, expanded: Boolean) {
    Row(
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp)
        ) {
            Text(text = remember { convertDateTimeString(data.receipt.date) } + " (" + (
                    when (data.receipt.store) {
                        "lidl" -> "Lidl"
                        "appie" -> "Albert Heijn"
                        "jumbo" -> "Jumbo"
                        else -> "???"
                    }) + ")")
            Text(
                text = remember { convertCentsToString(data.receipt.totalAmount) },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
        if (data.selectedItems.isNotEmpty()) {
            // TODO: not _really_ supposed to be a button (bc ripple and accessibility)
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
        // Expand arrow button
        IconButton(onClick = clickHandler) {
            if (expanded) {
                Icon(Icons.Rounded.ExpandLess, "show_less")
            } else {
                Icon(Icons.Rounded.ExpandMore, "show_more")
            }
        }
    }
}

@Composable
private fun CardItemList(
    data: FullInfo,
    onItemSelected: (Receipt, ReceiptItem, Boolean) -> Unit
) {
    if (data.receipt.items.isNullOrEmpty()) {
        Text(
            "Loading...",
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    } else {
        Column(
            Modifier.clickable(remember { MutableInteractionSource() },
                indication = null, onClick = {})
        ) {
            data.receipt.items.forEach { item ->
                val isSelected =
                    data.selectedItems.contains(item.indexInsideReceipt)

                ItemCard(
                    item,
                    selected = isSelected,
                    showSelectionIcon = data.selectedItems.isNotEmpty()
                ) {
                    onItemSelected(data.receipt, item, it)
                }
            }
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
    val selectedColor = MaterialTheme.colorScheme.secondaryContainer;
    val unselectedColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.0f)
    val backgroundColor: Color by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor
    )
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .padding(bottom = 7.dp)
            .clip(RoundedCornerShape(size = 20.dp))
            .background(backgroundColor)
            .selectable(
                selected = selected,
                enabled = true,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onSelectionChanged(!selected)
                })
            .padding(all = 10.dp)
            .defaultMinSize(minHeight = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = showSelectionIcon) {
            Icon(
                if (selected) Icons.Rounded.CheckCircleOutline
                else Icons.Rounded.RadioButtonUnchecked,
                "Selected",
            )
        }
        if (showSelectionIcon)
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            data.description, modifier = Modifier.weight(1f),
        )
        Text(
            remember { convertCentsToString(data.totalPrice) }, modifier = Modifier,
        )
    }
}

@Preview
//    @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
fun TopBarPreview() {
    PaylinkTheme {
        Surface {
            TopBar("previewing") {}
        }
    }
}

@Preview
//    @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
fun Beepy() {
    PaylinkTheme {
        Surface {
            AccountsComposable()
        }
    }
}

@Preview
//    @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AccountButtonPreview() {
    PaylinkTheme {
        Surface {
            ConnectAccountButton(
                title = "Preview",
                icon = Icons.Rounded.Handyman,
                isConnected = true,
                onClickHandler = {})
        }
    }
}

@Preview
//    @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CardPreview() {
    val previewData = FullInfo(
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

    PaylinkTheme {
        Surface {
            BonnetjeCard(
                data = previewData,
                previewForceOpen = true,
                onExpandClicked = {},
                onItemSelected = { _, _, _ -> })
        }
    }
}

@Preview
//    @Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BottomBarPreview() {
    PaylinkTheme {
        Surface {
            BottomActionBar(4, 793, {}) {}
        }
    }
}