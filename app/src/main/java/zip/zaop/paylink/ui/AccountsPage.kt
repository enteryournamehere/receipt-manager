package zip.zaop.paylink.ui

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
import zip.zaop.paylink.database.LinkablePlatform


@Composable
fun AccountsComposable(
    accountsViewModel: AccountsViewModel = viewModel(),
) {
    val uiState by accountsViewModel.uiState.collectAsState()
    if (uiState.wbwLoginState.visible) {
        WbwLoginPage(
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
            ConnectAccountButton(
                "Lidl",
                Icons.Rounded.ShoppingBasket,
                uiState.connections[LinkablePlatform.LIDL] ?: false,
                {
                    accountsViewModel.doLidlLogin()
                })
            ConnectAccountButton(
                "Albert Heijn",
                Icons.Rounded.ShoppingCart,
                uiState.connections[LinkablePlatform.APPIE] ?: false,
                {
                    accountsViewModel.doAppieLogin()
                })
            ConnectAccountButton(
                "Jumbo",
                Icons.Rounded.PedalBike,
                uiState.connections[LinkablePlatform.JUMBO] ?: false,
                {
                    accountsViewModel.doJumboLogin()
                })
            ConnectAccountButton(
                "WieBetaaltWat",
                Icons.Rounded.DoneAll,
                uiState.connections[LinkablePlatform.WBW] ?: false,
                {
                    accountsViewModel.startWbwLogin()
                })
            Button(onClick = {accountsViewModel.getWbwListStuff()}) {
                Text("refresh wbw lists")
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
    modifier: Modifier = Modifier
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
