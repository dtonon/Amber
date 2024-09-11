package com.greenart7c3.nostrsigner.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.Biometrics
import com.greenart7c3.nostrsigner.ui.actions.ConnectOrbotDialog
import com.greenart7c3.nostrsigner.ui.actions.LogoutDialog
import com.greenart7c3.nostrsigner.ui.components.HyperlinkText
import com.greenart7c3.nostrsigner.ui.components.IconRow
import com.greenart7c3.nostrsigner.ui.components.TextSpinner
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    navController: NavController,
) {
    var logoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var checked by remember { mutableStateOf(account.useProxy) }
    var disconnectTorDialog by remember { mutableStateOf(false) }
    var conectOrbotDialogOpen by remember { mutableStateOf(false) }
    val proxyPort = remember { mutableStateOf(account.proxyPort.toString()) }
    var allowNewConnections by remember { mutableStateOf(account.allowNewConnections) }

    val scope = rememberCoroutineScope()

    if (logoutDialog) {
        LogoutDialog(
            onCancel = {
                logoutDialog = false
            },
            onConfirm = {
                logoutDialog = false
                accountStateViewModel.logOff(account.keyPair.pubKey.toNpub())
            },
        )
    }

    Column(
        modifier
            .verticalScroll(rememberScrollState()),
    ) {
        if (Biometrics.isFingerprintAvailable(context)) {
            Box(
                Modifier
                    .padding(8.dp),
            ) {
                IconRow(
                    title = stringResource(R.string.security),
                    icon = Icons.Default.Security,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = {
                        navController.navigate(Route.Security.route)
                    },
                )
            }
        }

        Box(
            Modifier
                .padding(8.dp),
        ) {
            IconRow(
                title = stringResource(R.string.backup_keys),
                icon = Icons.Default.Key,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    navController.navigate(Route.AccountBackup.route)
                },
            )
        }

        Box(
            Modifier
                .padding(8.dp),
        ) {
            IconRow(
                title = stringResource(R.string.language),
                icon = Icons.Default.Language,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    navController.navigate(Route.Language.route)
                },
            )
        }

        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR != "offline") {
            Box(
                Modifier
                    .padding(8.dp),
            ) {
                IconRow(
                    title = stringResource(R.string.notification_type),
                    icon = Icons.Default.Notifications,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = {
                        navController.navigate(Route.NotificationType.route)
                    },
                )
            }

            Box(
                Modifier
                    .padding(8.dp),
            ) {
                IconRow(
                    title = if (checked) {
                        stringResource(R.string.disconnect_from_your_orbot_setup)
                    } else {
                        stringResource(R.string.connect_via_tor_short)
                    },
                    icon = R.drawable.ic_tor,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onLongClick = {
                        conectOrbotDialogOpen = true
                    },
                    onClick = {
                        if (checked) {
                            disconnectTorDialog = true
                        } else {
                            conectOrbotDialogOpen = true
                        }
                    },
                )
            }
        }

        Box(
            Modifier
                .padding(8.dp),
        ) {
            IconRow(
                title = stringResource(R.string.default_relays),
                icon = Icons.Default.Hub,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    navController.navigate(Route.DefaultRelays.route)
                },
            )
        }

        Box(
            Modifier
                .padding(8.dp),
        ) {
            IconRow(
                title = if (allowNewConnections) stringResource(R.string.disable_listening_for_new_connections) else stringResource(R.string.enable_listening_for_new_connections),
                icon = Icons.Default.SurroundSound,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    allowNewConnections = !allowNewConnections
                    account.allowNewConnections = allowNewConnections
                    LocalPreferences.saveToEncryptedStorage(context, account)
                },
            )
        }

        Box(
            Modifier
                .padding(8.dp),
        ) {
            IconRow(
                title = stringResource(R.string.logs),
                icon = Icons.Default.FilterList,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    navController.navigate(Route.Logs.route)
                },
            )
        }

        Box(
            Modifier
                .padding(8.dp),
        ) {
            IconRow(
                title = stringResource(R.string.sign_policy),
                icon = Icons.Default.Draw,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    navController.navigate(Route.SignPolicy.route)
                },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        HyperlinkText(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            fullText = "v${BuildConfig.VERSION_NAME}-${BuildConfig.FLAVOR}\n\n${context.getString(
                R.string.support_development,
            )}\n\n${context.getString(R.string.source_code)}",
            hyperLinks = mutableMapOf(
                stringResource(R.string.source_code) to stringResource(R.string.amber_github_uri),
                stringResource(R.string.support_development) to stringResource(R.string.support_development_uri),
            ),
            textStyle = TextStyle(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.inverseSurface,
            ),
            linkTextColor = MaterialTheme.colorScheme.primary,
            linkTextDecoration = TextDecoration.Underline,
            fontSize = 18.sp,
        )
    }

    if (conectOrbotDialogOpen) {
        ConnectOrbotDialog(
            onClose = { conectOrbotDialogOpen = false },
            onPost = {
                conectOrbotDialogOpen = false
                disconnectTorDialog = false
                checked = true
                LocalPreferences.updateProxy(context, true, proxyPort.value.toInt())
            },
            onError = {
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.could_not_connect_to_tor),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            proxyPort,
        )
    }

    if (disconnectTorDialog) {
        AlertDialog(
            title = { Text(text = stringResource(R.string.do_you_really_want_to_disable_tor_title)) },
            text = { Text(text = stringResource(R.string.do_you_really_want_to_disable_tor_text)) },
            onDismissRequest = { disconnectTorDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        disconnectTorDialog = false
                        checked = false
                        LocalPreferences.updateProxy(context, false, proxyPort.value.toInt())
                    },
                ) {
                    Text(text = stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { disconnectTorDialog = false },
                ) {
                    Text(text = stringResource(R.string.no))
                }
            },
        )
    }
}

@Composable
fun SettingsRow(
    name: Int,
    description: Int,
    selectedItems: ImmutableList<TitleExplainer>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.weight(2.0f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(name),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TextSpinner(
            label = "",
            placeholder = selectedItems[selectedIndex].title,
            options = selectedItems,
            onSelect = onSelect,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                .weight(1f),
        )
    }
}

enum class NotificationType(val screenCode: Int, val resourceId: Int) {
    PUSH(0, R.string.push_notifications),
    DIRECT(1, R.string.direct_connection),
}

enum class BiometricsTimeType(val screenCode: Int, val resourceId: Int) {
    EVERY_TIME(0, R.string.every_time),
    ONE_MINUTE(1, R.string.one_minute),
    FIVE_MINUTES(2, R.string.five_minutes),
    TEN_MINUTES(3, R.string.ten_minutes),
}

fun parseBiometricsTimeType(screenCode: Int): BiometricsTimeType {
    return when (screenCode) {
        BiometricsTimeType.ONE_MINUTE.screenCode -> BiometricsTimeType.ONE_MINUTE
        BiometricsTimeType.FIVE_MINUTES.screenCode -> BiometricsTimeType.FIVE_MINUTES
        BiometricsTimeType.TEN_MINUTES.screenCode -> BiometricsTimeType.TEN_MINUTES
        else -> {
            BiometricsTimeType.EVERY_TIME
        }
    }
}

fun parseNotificationType(screenCode: Int): NotificationType {
    return when (screenCode) {
        NotificationType.PUSH.screenCode -> NotificationType.PUSH
        NotificationType.DIRECT.screenCode -> NotificationType.DIRECT
        else -> {
            NotificationType.PUSH
        }
    }
}
