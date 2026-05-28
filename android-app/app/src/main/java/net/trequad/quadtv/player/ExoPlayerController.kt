package net.trequad.quadtv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class ExoPlayerController(
    context: Context
) : QuadTvPlayer {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    override fun play(request: StreamPlaybackRequest) {
        player.setMediaItem(MediaItem.fromUri(request.url))
        player.prepare()
        player.play()
    }

    override fun stop() {
        player.stop()
    }

    override fun release() {
        player.release()
    }
}
