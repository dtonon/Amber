/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.greenart7c3.nostrsigner.relays

import android.util.Log
import com.greenart7c3.nostrsigner.checkNotInMainThread
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import java.util.UUID
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Nostr Client manages multiple personae the user may switch between. Events are received and
 * published through multiple relays. Events are stored with their respective persona.
 */
object Client : RelayPool.Listener {
    private var listeners = setOf<Listener>()
    private var relays = emptyArray<Relay>()
    private var subscriptions = mapOf<String, List<TypedFilter>>()

    @Synchronized
    fun addRelays(relays: Array<Relay>) {
        if (isSameRelaySetConfig(relays)) return
        RelayPool.disconnect()
        RelayPool.unregister(this)
        RelayPool.unloadRelays()
        RelayPool.register(this)
        RelayPool.loadRelays(relays.toList())
        Client.relays = relays
    }

    @Synchronized
    fun reconnect(
        relays: Array<Relay>?,
        onlyIfChanged: Boolean = false,
    ) {
        Log.d("Relay", "Relay Pool Reconnecting to ${relays?.size} relays")
        checkNotInMainThread()

        if (onlyIfChanged) {
            if (!isSameRelaySetConfig(relays)) {
                if (Client.relays.isNotEmpty()) {
                    RelayPool.disconnect()
                    RelayPool.unregister(this)
                    RelayPool.unloadRelays()
                }

                if (relays != null) {
                    RelayPool.register(this)
                    RelayPool.loadRelays(relays.toList())
                    RelayPool.requestAndWatch()
                    Client.relays = relays
                }
            }
        } else {
            if (Client.relays.isNotEmpty()) {
                RelayPool.disconnect()
                RelayPool.unregister(this)
                RelayPool.unloadRelays()
            }

            if (relays != null) {
                RelayPool.register(this)
                RelayPool.loadRelays(relays.toList())
                RelayPool.requestAndWatch()
                Client.relays = relays
            }
        }
    }

    private fun isSameRelaySetConfig(newRelayConfig: Array<Relay>?): Boolean {
        if (relays.size != newRelayConfig?.size) return false

        relays.forEach { oldRelayInfo ->
            val newRelayInfo = newRelayConfig.find { it.url == oldRelayInfo.url } ?: return false

            if (!oldRelayInfo.isSameRelayConfig(newRelayInfo)) return false
        }

        return true
    }

    fun sendFilter(
        subscriptionId: String = UUID.randomUUID().toString().substring(0..10),
        filters: List<TypedFilter> = listOf(),
    ) {
        checkNotInMainThread()

        subscriptions = subscriptions + Pair(subscriptionId, filters)
        RelayPool.sendFilter(subscriptionId)
    }

    fun sendFilterOnlyIfDisconnected(
        subscriptionId: String = UUID.randomUUID().toString().substring(0..10),
        filters: List<TypedFilter> = listOf(),
    ) {
        checkNotInMainThread()

        subscriptions = subscriptions + Pair(subscriptionId, filters)
        RelayPool.connectAndSendFiltersIfDisconnected()
    }

    fun send(
        signedEvent: EventInterface,
        onLoading: (Boolean) -> Unit,
        relay: String? = null,
        feedTypes: Set<FeedType>? = null,
        relayList: List<Relay>? = null,
        onDone: (() -> Unit)? = null,
    ) {
        checkNotInMainThread()

        if (relayList != null) {
            RelayPool.sendToSelectedRelays(relayList, signedEvent, onLoading, onDone)
        } else if (relay == null) {
            RelayPool.send(signedEvent, onLoading, onDone)
        } else {
            val useConnectedRelayIfPresent = RelayPool.getRelays(relay)

            if (useConnectedRelayIfPresent.isNotEmpty()) {
                useConnectedRelayIfPresent.forEach {
                    it.onLoading = onLoading

                    it.send(signedEvent, onDone)
                }
            } else {
                /** temporary connection */
                newSporadicRelay(
                    relay,
                    feedTypes,
                    onConnected = { mRelay -> mRelay.send(signedEvent, null) },
                    onDone = onDone,
                    onLoading = onLoading,
                )
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun newSporadicRelay(
        url: String,
        feedTypes: Set<FeedType>?,
        onLoading: (Boolean) -> Unit,
        onConnected: (Relay) -> Unit,
        onDone: (() -> Unit)?,
    ) {
        val relay = Relay(url, activeTypes = feedTypes ?: emptySet())
        relay.onLoading = onLoading
        RelayPool.addRelay(relay)

        relay.connectAndRun(
            onOk = {
                GlobalScope.launch(Dispatchers.IO) {
                    if (onDone != null) {
                        onDone()
                    }
                }
            },
        ) {
            allSubscriptions().forEach { relay.sendFilter(requestId = it) }

            onConnected(relay)

            GlobalScope.launch(Dispatchers.IO) {
                delay(60000) // waits for a reply
                if (relay.isConnected()) {
                    relay.disconnect()
                    RelayPool.removeRelay(relay)

                    if (onDone != null) {
                        onDone()
                    }
                }
            }
        }
    }

    fun close(subscriptionId: String) {
        RelayPool.close(subscriptionId)
        subscriptions = subscriptions.minus(subscriptionId)
    }

    fun isActive(subscriptionId: String): Boolean {
        return subscriptions.contains(subscriptionId)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onEvent(
        event: Event,
        subscriptionId: String,
        relay: Relay,
        afterEOSE: Boolean,
    ) {
        // Releases the Web thread for the new payload.
        // May need to add a processing queue if processing new events become too costly.
        GlobalScope.launch(Dispatchers.Default) {
            listeners.forEach { it.onEvent(event, subscriptionId, relay, afterEOSE) }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onError(
        error: Error,
        subscriptionId: String,
        relay: Relay,
    ) {
        // Releases the Web thread for the new payload.
        // May need to add a processing queue if processing new events become too costly.
        GlobalScope.launch(Dispatchers.Default) {
            listeners.forEach { it.onError(error, subscriptionId, relay) }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onRelayStateChange(
        type: Relay.StateType,
        relay: Relay,
        channel: String?,
    ) {
        // Releases the Web thread for the new payload.
        // May need to add a processing queue if processing new events become too costly.
        GlobalScope.launch(Dispatchers.Default) {
            listeners.forEach { it.onRelayStateChange(type, relay, channel) }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onSendResponse(
        eventId: String,
        success: Boolean,
        message: String,
        relay: Relay,
    ) {
        // Releases the Web thread for the new payload.
        // May need to add a processing queue if processing new events become too costly.
        GlobalScope.launch(Dispatchers.Default) {
            listeners.forEach { it.onSendResponse(eventId, success, message, relay) }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onAuth(
        relay: Relay,
        challenge: String,
    ) {
        // Releases the Web thread for the new payload.
        // May need to add a processing queue if processing new events become too costly.
        GlobalScope.launch(Dispatchers.Default) { listeners.forEach { it.onAuth(relay, challenge) } }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onNotify(
        relay: Relay,
        description: String,
    ) {
        // Releases the Web thread for the new payload.
        // May need to add a processing queue if processing new events become too costly.
        GlobalScope.launch(Dispatchers.Default) {
            listeners.forEach { it.onNotify(relay, description) }
        }
    }

    fun subscribe(listener: Listener) {
        listeners = listeners.plus(listener)
    }

    fun isSubscribed(listener: Listener): Boolean {
        return listeners.contains(listener)
    }

    fun allSubscriptions(): Set<String> {
        return subscriptions.keys
    }

    fun getSubscriptionFilters(subId: String): List<TypedFilter> {
        return subscriptions[subId] ?: emptyList()
    }

    abstract class Listener {
        /** A new message was received */
        open fun onEvent(
            event: Event,
            subscriptionId: String,
            relay: Relay,
            afterEOSE: Boolean,
        ) = Unit

        /** A new or repeat message was received */
        open fun onError(
            error: Error,
            subscriptionId: String,
            relay: Relay,
        ) = Unit

        /** Connected to or disconnected from a relay */
        open fun onRelayStateChange(
            type: Relay.StateType,
            relay: Relay,
            subscriptionId: String?,
        ) = Unit

        /** When an relay saves or rejects a new event. */
        open fun onSendResponse(
            eventId: String,
            success: Boolean,
            message: String,
            relay: Relay,
        ) = Unit

        open fun onAuth(
            relay: Relay,
            challenge: String,
        ) = Unit

        open fun onNotify(
            relay: Relay,
            description: String,
        ) = Unit
    }
}
