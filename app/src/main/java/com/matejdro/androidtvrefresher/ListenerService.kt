package com.matejdro.androidtvrefresher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

class ListenerService : NotificationListenerService(), LifecycleOwner {
    override val lifecycle = LifecycleRegistry(this)

    private var previousController: MediaController? = null
    private var previousState: Int = PlaybackState.STATE_STOPPED

    private lateinit var config: Config

    private var api: HomeAssistantApi? = null

    private val debouncer by lazy(LazyThreadSafetyMode.NONE) {
        Debouncer(
            triggerFirstImmediately = true,
            scope = lifecycle.coroutineScope
        )
    }

    override fun onListenerConnected() {
        config = Config(this)

        val mediaProvider = ActiveMediaSessionProvider(applicationContext)

        lifecycle.currentState = Lifecycle.State.RESUMED

        mediaProvider.observe(this, this::onMediaUpdate)

        registerReceiver(refreshBroadcastReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerReceiver(refreshBroadcastReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private fun onMediaUpdate(newController: MediaController?) {
        if (newController === previousController) {
            return
        }

        previousController?.unregisterCallback(mediaCallbacks)
        previousState = 0

        if (newController != null) {
            newController.registerCallback(mediaCallbacks)

            mediaCallbacks.onPlaybackStateChanged(newController.playbackState)

            previousController = newController
        }
    }

    private fun refresh() = debouncer.executeDebouncing {
        Log.d("AndroidTvRefresher", "Refresh")

        try {
            val api = getOrCreateApi() ?: throw IllegalArgumentException("Could not create an API instance. Was the host set?")
            val token = config.token ?: throw IllegalArgumentException("Token was not set")

            api.triggerService(
                "homeassistant",
                "update_entity",
                mapOf(
                    "entity_id" to " media_player.androidtv"
                ),
                "Bearer $token"
            )
            Log.d("AndroidTvRefresher", "Refresh done")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mediaCallbacks = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            Log.d("AndroidTvRefresher", "State changed: $state")

            if (state != null && state.state != previousState) {
                previousState = state.state
                refresh()
            }
        }
    }

    override fun onListenerDisconnected() {
        lifecycle.currentState = Lifecycle.State.CREATED
    }

    override fun onDestroy() {
        super.onDestroy()

        lifecycle.currentState = Lifecycle.State.DESTROYED
        unregisterReceiver(refreshBroadcastReceiver)
    }

    private fun getOrCreateApi(): HomeAssistantApi? {
        this.api?.let { return it }

        val url = config.url ?: return null

        val api: HomeAssistantApi = Retrofit.Builder()
            .baseUrl("$url/api/")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create()

        this.api = api;
        return api;
    }

    private val refreshBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            lifecycleScope.launch {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    // Android needs some time to report fully off to HA. Wait before transmitting.
                    delay(500)
                }

                refresh()
            }
        }
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, ListenerService::class.java)
        }
    }
}
