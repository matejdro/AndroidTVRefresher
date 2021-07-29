
package com.matejdro.androidtvrefresher

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

class ActiveMediaSessionProvider constructor(private val context: Context) :
        androidx.lifecycle.LiveData<MediaController>(),
        MediaSessionManager.OnActiveSessionsChangedListener {

    private val mediaSessionManager: MediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    var currentController: MediaController? = null

    private val idlePlayers = ArrayList<OwnedPlaybackCallback>()

    private fun findPlayingMediaController() {
        val activeSessions = getActiveSessions()

        val newController = activeSessions.firstOrNull { it.isPlaying() }

        var reportedController = newController
        if (newController == null) {
            reportedController = currentController
        }

        removeCurrentController()
        currentController = newController

        idlePlayers.forEach(OwnedPlaybackCallback::unregister)
        idlePlayers.clear()

        if (currentController == null) {
            activeSessions.forEach {
                idlePlayers.add(OwnedPlaybackCallback((it)))
            }
        } else {
            currentController?.registerCallback(mediaCallback)
        }

        setReportedController(reportedController)
    }

    private fun getActiveSessions(): List<MediaController> {
        try {
            return mediaSessionManager.getActiveSessions(
                    ListenerService.getComponentName(context)
            )
        } catch (e: SecurityException) {
            return emptyList()
        }
    }

    private fun activate() {
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                    this,
                    ListenerService.getComponentName(context)
            )
        } catch (e: SecurityException) {
            // No notification access. Just ignore, MusicHandler should stop and restart us when
            // needed
        }

        updateControllerIfNeeded()
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        updateControllerIfNeeded()
    }

    override fun onActive() {
        activate()
    }

    override fun onInactive() {
        mediaSessionManager.removeOnActiveSessionsChangedListener(this)

        removeCurrentController()
        idlePlayers.forEach(OwnedPlaybackCallback::unregister)
        idlePlayers.clear()
    }

    fun updateControllerIfNeeded() {
        if (!isCurrentControllerActive() || currentController?.isPlaying() != true) {
            findPlayingMediaController()
        }
    }

    private fun isCurrentControllerActive(): Boolean {
        val currentController = currentController ?: return false

        return getActiveSessions().any { it.packageName == currentController.packageName }
    }

    private fun removeCurrentController() {
        currentController?.unregisterCallback(mediaCallback)
        currentController = null
    }

    private val mediaCallback: MediaController.Callback

    inner class OwnedPlaybackCallback(val controller: MediaController) : MediaController.Callback() {
        init {
            controller.registerCallback(this)
        }

        fun unregister() {
            controller.unregisterCallback(this)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (state?.isPlaying() == true) {
                updateControllerIfNeeded()
            }
        }

        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo?) {
            updateControllerIfNeeded()
        }
    }

    private fun setReportedController(mediaController: MediaController?) {
        if (mediaController !== value) {
            value = mediaController
        }
    }

    init {
        this.mediaCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                updateControllerIfNeeded()
            }

            override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                setReportedController(currentController)
            }
        }
    }
}

fun PlaybackState.isPlaying(): Boolean {
    val state = this.state
    return state != PlaybackState.STATE_NONE &&
            state != PlaybackState.STATE_PAUSED &&
            state != PlaybackState.STATE_STOPPED &&
            state != PlaybackState.STATE_ERROR
}


fun MediaController.isPlaying(): Boolean {
    return this.playbackState?.isPlaying() == true
}
