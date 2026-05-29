package net.trequad.quadtv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class ExoPlayerController(
    context: Context
) : QuadTvPlayer {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var attachedPlayerView: PlayerView? = null

    override fun attachSurface(surface: PlayerRenderSurface) {
        attachedPlayerView?.player = null
        surface.playerView.player = null
        surface.playerView.player = player
        attachedPlayerView = surface.playerView
    }

    override fun play(request: StreamPlaybackRequest) {
        player.setMediaItem(MediaItem.fromUri(request.url))
        player.prepare()
        player.play()
    }

    override fun stop() {
        player.stop()
    }

    override fun release() {
        attachedPlayerView?.player = null
        attachedPlayerView = null
        player.release()
    }
}
