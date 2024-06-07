package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.relays.Client
import com.greenart7c3.nostrsigner.relays.Relay
import com.greenart7c3.nostrsigner.ui.actions.ActivityDialog
import com.greenart7c3.nostrsigner.ui.actions.DeleteDialog
import com.greenart7c3.nostrsigner.ui.actions.EditRelaysDialog
import com.greenart7c3.nostrsigner.ui.actions.QrCodeDialog
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun EditPermission(
    modifier: Modifier,
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    selectedPackage: String,
    navController: NavController,
    database: AppDatabase,
) {
    val clipboardManager = LocalClipboardManager.current
    val localAccount = LocalPreferences.loadFromEncryptedStorage(account.keyPair.pubKey.toNpub())!!
    val permissions = remember {
        mutableStateListOf<ApplicationPermissionsEntity>()
    }
    var applicationData by remember {
        mutableStateOf(ApplicationEntity(selectedPackage, "", emptyList(), "", "", "", "", true, "", false))
    }
    var textFieldvalue by remember(applicationData.name) {
        mutableStateOf(TextFieldValue(applicationData.name))
    }

    var wantsToDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var checked by remember {
        mutableStateOf(applicationData.useSecret)
    }
    val secret = if (checked) "&secret=${applicationData.secret}" else ""
    var bunkerUri by remember {
        mutableStateOf("bunker://${account.keyPair.pubKey.toHexKey()}?relay=wss://relay.nsec.app$secret")
    }
    var editRelaysDialog by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            permissions.addAll(database.applicationDao().getAllByKey(selectedPackage).sortedBy { "${it.type}-${it.kind}" })
            applicationData = database.applicationDao().getByKey(selectedPackage)!!.application
            checked = applicationData.useSecret
            val relays = applicationData.relays.joinToString(separator = "&") { "relay=$it" }
            val localSecret = if (checked) "&secret=${applicationData.secret}" else ""
            bunkerUri = "bunker://${account.keyPair.pubKey.toHexKey()}?$relays$localSecret"
        }
    }

    if (editRelaysDialog) {
        EditRelaysDialog(
            applicationData = applicationData,
            onClose = {
                editRelaysDialog = false
            },
        ) { relays2 ->
            applicationData =
                applicationData.copy(
                    relays = relays2.map { it },
                )
            val relays = applicationData.relays.joinToString(separator = "&") { "relay=$it" }
            bunkerUri = "bunker://${account.keyPair.pubKey.toHexKey()}?$relays$secret"
            editRelaysDialog = false
        }
    }

    if (wantsToDelete) {
        DeleteDialog(
            onCancel = {
                wantsToDelete = false
            },
        ) {
            scope.launch(Dispatchers.IO) {
                database
                    .applicationDao()
                    .delete(applicationData)

                val relays = mutableListOf<Relay>()
                database.applicationDao().getAllApplications().forEach {
                    it.application.relays.forEach { url ->
                        if (url.isNotBlank()) {
                            if (!relays.any { relay -> relay.url == url }) {
                                relays.add(Relay(url))
                            }
                        }
                    }
                }
                Client.addRelays(relays.toTypedArray())
                if (LocalPreferences.getNotificationType() == NotificationType.DIRECT) {
                    Client.reconnect(relays.toTypedArray())
                }
            }

            scope.launch(Dispatchers.Main) {
                navController.popBackStack()
                accountStateViewModel.switchUser(localAccount.keyPair.pubKey.toNpub(), Route.Permissions.route)
            }
        }
    }

    var showDialog by remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current

    var showActivityDialog by remember {
        mutableStateOf(false)
    }

    if (showActivityDialog) {
        ActivityDialog(
            database = database,
            key = selectedPackage,
            onClose = {
                showActivityDialog = false
            },
        )
    }

    if (showDialog) {
        QrCodeDialog(
            content = bunkerUri,
        ) {
            showDialog = false
        }
    }

    Column(
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.permissions),
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
        )

        if (!applicationData.isConnected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    bunkerUri,
                    Modifier
                        .weight(0.9f)
                        .padding(8.dp),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(bunkerUri))
                    },
                ) {
                    Icon(Icons.Default.ContentPaste, stringResource(R.string.copy_to_clipboard))
                }
                IconButton(
                    onClick = {
                        showDialog = true
                    },
                ) {
                    Icon(Icons.Default.QrCode, stringResource(R.string.show_qr_code))
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable {
                        checked = !checked
                        val relays = applicationData.relays.joinToString(separator = "&") { "relay=$it" }
                        val localSecret = if (checked) "&secret=${applicationData.secret}" else ""
                        bunkerUri = "bunker://${account.keyPair.pubKey.toHexKey()}?$relays$localSecret"
                    },
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "Use secret to connect to the application",
                )
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        checked = !checked
                    },
                )
            }
        }
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            value = textFieldvalue.text,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
            onValueChange = {
                textFieldvalue = TextFieldValue(it)
            },
            label = {
                Text("Name")
            },
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (applicationData.secret.isNotEmpty() || applicationData.relays.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(
                        onClick = {
                            editRelaysDialog = true
                        },
                        Modifier.padding(6.dp),
                    ) {
                        Text(stringResource(R.string.relays))
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(
                    onClick = {
                        showActivityDialog = true
                    },
                    Modifier.padding(6.dp),
                ) {
                    Text(stringResource(R.string.activity))
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
        ) {
            itemsIndexed(permissions, { index, _ -> index }) { _, permission ->
                val localPermission =
                    Permission(
                        permission.type.toLowerCase(Locale.current),
                        permission.kind,
                    )

                val message =
                    if (permission.type == "SIGN_EVENT") {
                        stringResource(R.string.sign, localPermission.toLocalizedString(context))
                    } else {
                        localPermission.toLocalizedString(context)
                    }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(vertical = 15.dp, horizontal = 25.dp)
                        .fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .clickable {
                                val localPermissions =
                                    permissions.map {
                                        if (it.id == permission.id) {
                                            it.copy(acceptable = !permission.acceptable)
                                        } else {
                                            it.copy()
                                        }
                                    }
                                permissions.clear()
                                permissions.addAll(localPermissions)
                            },
                    ) {
                        Text(
                            text = message,
                            fontSize = 18.sp,
                        )
                        Switch(
                            checked = permission.acceptable,
                            onCheckedChange = {
                                val localPermissions =
                                    permissions.map {
                                        if (it.id == permission.id) {
                                            it.copy(acceptable = !permission.acceptable)
                                        } else {
                                            it.copy()
                                        }
                                    }
                                permissions.clear()
                                permissions.addAll(localPermissions)
                            },
                        )
                    }
                    IconButton(
                        content = {
                            Icon(
                                Icons.Default.Delete,
                                stringResource(R.string.remove_permission),
                                modifier = Modifier
                                    .fillMaxHeight(),
                                tint = Color.Red,
                            )
                        },
                        onClick = {
                            permissions.remove(permission)
                        },
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth(),
            Arrangement.Center,
        ) {
            Button(
                onClick = {
                    wantsToDelete = true
                },
                Modifier.padding(6.dp),
            ) {
                Text(stringResource(R.string.delete_application))
            }
        }

        Row(
            Modifier
                .fillMaxWidth(),
            Arrangement.Center,
        ) {
            Button(
                onClick = {
                    navController.popBackStack()
                },
                Modifier.padding(6.dp),
            ) {
                Text(stringResource(id = R.string.cancel))
            }
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val localApplicationData =
                            applicationData.copy(
                                name = textFieldvalue.text,
                                useSecret = checked,
                            )
                        database.applicationDao().delete(applicationData)
                        database.applicationDao().insertApplicationWithPermissions(
                            ApplicationWithPermissions(
                                localApplicationData,
                                permissions,
                            ),
                        )
                        val relays = mutableListOf<Relay>()
                        database.applicationDao().getAllApplications().forEach {
                            it.application.relays.forEach { url ->
                                if (url.isNotBlank()) {
                                    if (!relays.any { relay -> relay.url == url }) {
                                        relays.add(Relay(url))
                                    }
                                }
                            }
                        }
                        Client.addRelays(relays.toTypedArray())
                        if (LocalPreferences.getNotificationType() == NotificationType.DIRECT) {
                            Client.reconnect(relays.toTypedArray())
                        }

                        scope.launch(Dispatchers.Main) {
                            navController.popBackStack()
                            accountStateViewModel.switchUser(localAccount.keyPair.pubKey.toNpub(), Route.Permissions.route)
                        }
                    }
                },
                Modifier.padding(6.dp),
            ) {
                Text(stringResource(id = R.string.confirm))
            }
        }
    }
}
