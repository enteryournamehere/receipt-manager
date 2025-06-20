package zip.zaop.paylink.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import zip.zaop.paylink.AccountsViewModel
import zip.zaop.paylink.database.DatabaseAuthState
import zip.zaop.paylink.database.LinkablePlatform

@Composable
fun AccountsComposable(
    accountsViewModel: AccountsViewModel = viewModel(),
) {
    val uiState by accountsViewModel.uiState.collectAsState()

    if (uiState.alertInfo?.shown == true) {
        Alert(
            "Error",
            uiState.alertInfo?.content ?: "no further information"
        ) { accountsViewModel.closeAlertDialog() }
    }

    if (uiState.wbwLoginState.visible) {
        WbwLoginPage(
            onCancel = { accountsViewModel.hideWbwLoginScreen() },
            onKeyboardDone = { accountsViewModel.submitWbwLogin() },
            onUsernameChanged = { accountsViewModel.updateWbwUsername(it) },
            onPasswordChanged = { accountsViewModel.updateWbwPassword(it) },
            username = uiState.wbwLoginState.username,
            password = uiState.wbwLoginState.password
        )
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(IntrinsicSize.Min)
        ) {
            Spacer(Modifier.height(20.dp))
            ConnectAccountList(
                title = "Lidl",
                icon = Icons.Rounded.ShoppingBasket,
                connections = uiState.connections[LinkablePlatform.LIDL] ?: emptyList(),
                onAdd = { accountsViewModel.doLidlLogin() },
                onRemove = { accountsViewModel.unlinkAccount(it.id, LinkablePlatform.LIDL) }
            )
            ConnectAccountList(
                title = "Albert Heijn",
                icon = Icons.Rounded.ShoppingCart,
                connections = uiState.connections[LinkablePlatform.APPIE] ?: emptyList(),
                onAdd = { accountsViewModel.doAppieLogin() },
                onRemove = { accountsViewModel.unlinkAccount(it.id, LinkablePlatform.APPIE) }
            )
            ConnectAccountList(
                title = "Jumbo",
                icon = Icons.Rounded.PedalBike,
                connections = uiState.connections[LinkablePlatform.JUMBO] ?: emptyList(),
                onAdd = { accountsViewModel.doJumboLogin() },
                onRemove = { accountsViewModel.unlinkAccount(it.id, LinkablePlatform.JUMBO) }
            )
            ConnectAccountButton(
                "WieBetaaltWat",
                Icons.Rounded.DoneAll,
                (uiState.connections[LinkablePlatform.WBW]?.size ?: 0) > 0,
                {
                    accountsViewModel.startWbwLogin()
                })
            if ((uiState.connections[LinkablePlatform.WBW]?.size ?: 0) > 0) {
                Button(onClick = { accountsViewModel.getWbwListStuff() }) {
                    Text("refresh wbw lists")
                }
            }

            val launcher =
                rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.sqlite3")) { uri ->
                    if (uri != null) {
                        accountsViewModel.exportDatabase(uri)
                    }
                }

            Button(onClick = { launcher.launch("test.db") }) {
                Text(text = "Export database")
            }
            Button(onClick = { accountsViewModel.clearReceipts() }) {
                Text(text = "Clear stored receipts")
            }
        }
    }
}

@Composable
fun ConnectAccountButton(
    title: String,
    icon: ImageVector,
    isConnected: Boolean,
    onClickHandler: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = if (isConnected) CardDefaults.cardColors() else CardDefaults.outlinedCardColors(),
        border = if (isConnected) null else CardDefaults.outlinedCardBorder(),
        modifier = modifier
            .width(250.dp)
            .padding(5.dp)

    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, title)
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text(title, Modifier.weight(1f))
            IconButton(onClick = { onClickHandler() }) {
                Icon(if (isConnected) Icons.Rounded.Check else Icons.Rounded.Add, "GO")
            }
        }
    }
}

@Composable
fun ConnectAccountList(
    title: String,
    icon: ImageVector,
    connections: List<DatabaseAuthState>,
    onAdd: () -> Unit,
    onRemove: (DatabaseAuthState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected = connections.isNotEmpty()
    Card(
        colors = if (isConnected) CardDefaults.cardColors() else CardDefaults.outlinedCardColors(),
        border = if (isConnected) null else CardDefaults.outlinedCardBorder(),
        modifier = modifier
            .width(300.dp)
            .padding(5.dp)

    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, title)
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(title, Modifier.weight(1f))
                IconButton(onClick = { onAdd() }) {
                    Icon(Icons.Rounded.Add, "Add")
                }
            }
            connections.forEach { state ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp)
                ) {
                    Text("Account ${state.id}", Modifier.weight(1f))
                    IconButton(onClick = { onRemove(state) }) {
                        Icon(Icons.Rounded.Delete, "Remove")
                    }
                }
            }
        }
    }
}
