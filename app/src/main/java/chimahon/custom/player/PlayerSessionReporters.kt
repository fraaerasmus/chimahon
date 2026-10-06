package chimahon.custom.player

import android.media.session.MediaSession
import android.media.session.PlaybackState
import eu.kanade.tachiyomi.animesource.model.Video

/**
 * What the player tells the outside world about playback: the system media session's
 * playing, paused and stopped state, and a Jellyfin server when the stream comes from one.
 * `PlayerActivity` calls in from four places and holds nothing else of this.
 */
class PlayerSessionReporters {

    @Volatile
    private var jellyfin: JellyfinPlaybackReporter? = null

    /** Every load is a new play session on the server: next episode, quality switch. */
    fun onFileLoaded(video: Video?, paused: Boolean, positionSeconds: () -> Float) {
        jellyfin?.stop(atLastReported = true)
        jellyfin = video
            ?.let { JellyfinPlaybackReporter.parseTarget(it.videoUrl, it.headers) }
            ?.let { target -> JellyfinPlaybackReporter(target, positionSeconds) }
            ?.also { it.start(paused = paused) }
    }

    fun onPauseChanged(mediaSession: MediaSession?, paused: Boolean) {
        mediaSession?.publish(if (paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING, speed = 1f)
        jellyfin?.setPaused(paused)
    }

    /** Before the session is released, so whoever shows it sees the stop. */
    fun publishStopped(mediaSession: MediaSession) {
        mediaSession.publish(PlaybackState.STATE_STOPPED, speed = 0f)
    }

    fun release() {
        jellyfin?.stop()
        jellyfin = null
    }

    private fun MediaSession.publish(state: Int, speed: Float) {
        setPlaybackState(
            PlaybackState.Builder(controller.playbackState)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, speed)
                .build(),
        )
    }
}
