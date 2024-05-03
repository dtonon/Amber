package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.relays.Client
import com.greenart7c3.nostrsigner.relays.Relay
import com.greenart7c3.nostrsigner.service.ConnectivityService
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.NotificationDataSource
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@SuppressLint("StateFlowValueCalledInComposition", "UnrememberedMutableState")
@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    intent: Intent,
    packageName: String?,
    appName: String?,
    flow: MutableStateFlow<List<IntentData>>,
) {
    val accountState by accountStateViewModel.accountContent.collectAsState()
    val intents by flow.collectAsState(initial = emptyList())

    Column {
        Crossfade(
            targetState = accountState,
            animationSpec = tween(durationMillis = 100),
            label = "AccountScreen",
        ) { state ->
            when (state) {
                is AccountState.LoggedOff -> {
                    LoginPage(accountStateViewModel)
                }
                is AccountState.LoggedIn -> {
                    var intentData by remember {
                        mutableStateOf<IntentData?>(null)
                    }
                    LaunchedEffect(intent, intents) {
                        IntentUtils.getIntentData(intent, packageName, intent.getStringExtra("route"), state.account) {
                            intentData = it
                        }
                        val data =
                            intents.firstOrNull {
                                it.currentAccount.isNotBlank()
                            }

                        data?.bunkerRequest?.let {
                            if (it.currentAccount.isNotBlank()) {
                                if (LocalPreferences.currentAccount() != it.currentAccount) {
                                    accountStateViewModel.switchUser(it.currentAccount, null)
                                }
                            }
                        }
                    }

                    if (intentData != null) {
                        if (intents.none { item -> item.id == intentData!!.id }) {
                            flow.value = listOf(intentData!!)
                        }
                    }

                    val newIntents =
                        intents.ifEmpty {
                            if (intentData == null) {
                                listOf()
                            } else {
                                listOf(intentData)
                            }
                        }.mapNotNull { it }
                    val database = NostrSigner.instance.getDatabase(state.account.keyPair.pubKey.toNpub())
                    val localRoute = mutableStateOf(newIntents.firstNotNullOfOrNull { it.route } ?: state.route)
                    val scope = rememberCoroutineScope()

                    SideEffect {
                        scope.launch(Dispatchers.IO) {
                            val relays = mutableListOf<Relay>()
                            LocalPreferences.allSavedAccounts().forEach { acc ->
                                val db = NostrSigner.instance.getDatabase(acc.npub)
                                db.applicationDao().getAllApplications().forEach {
                                    it.application.relays.forEach { url ->
                                        if (url.isNotBlank()) {
                                            if (!relays.any { relay -> relay.url == url }) {
                                                relays.add(Relay(url))
                                            }
                                        }
                                    }
                                }
                            }

                            delay(1000)
                            Client.addRelays(relays.toTypedArray())
                            @Suppress("KotlinConstantConditions")
                            if (LocalPreferences.getNotificationType() == NotificationType.DIRECT && BuildConfig.FLAVOR != "offline") {
                                NostrSigner.instance.applicationContext.startService(
                                    Intent(NostrSigner.instance.applicationContext, ConnectivityService::class.java),
                                )
                                Client.reconnect(relays.toTypedArray())
                                NotificationDataSource.start()
                            }
                        }
                    }

                    MainScreen(state.account, accountStateViewModel, newIntents, packageName, appName, localRoute, database)
                }
            }
        }
    }
}
