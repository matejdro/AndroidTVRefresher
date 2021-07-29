package com.matejdro.androidtvrefresher

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.coroutineScope
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

class ListenerService : NotificationListenerService(), LifecycleOwner {
    private val lifecycle = LifecycleRegistry(this)

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
        try {
            val api = getOrCreateApi() ?: return@executeDebouncing
            val token = config.token ?: return@executeDebouncing

            api.triggerService(
                "homeassistant",
                "update_entity",
                mapOf(
                    "entity_id" to " media_player.androidtv"
                ),
                "Bearer $token"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mediaCallbacks = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
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
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycle
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

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, ListenerService::class.java)
        }
    }
}