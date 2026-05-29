package net.trequad.quadtv.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class VlcPlayerController(
    context: Context
) : QuadTvPlayer {
    private val libVLC = LibVLC(context, arrayListOf("--network-caching=1500"))
    private val mediaPlayer = MediaPlayer(libVLC)
    private var attachedSurfaceView: SurfaceView? = null

    override fun attachSurface(surface: PlayerRenderSurface) {
        if (attachedSurfaceView != null) {
            mediaPlayer.vlcVout.detachViews()
        }
        mediaPlayer.vlcVout.setVideoView(surface.vlcSurfaceView)
        mediaPlayer.vlcVout.attachViews()
        attachedSurfaceView = surface.vlcSurfaceView
    }

    override fun play(request: StreamPlaybackRequest) {
        val media = Media(libVLC, Uri.parse(request.url))
        media.setHWDecoderEnabled(true, false)
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    override fun stop() {
        mediaPlayer.stop()
    }

    override fun release() {
        if (attachedSurfaceView != null) {
            mediaPlayer.vlcVout.detachViews()
            attachedSurfaceView = null
        }
        mediaPlayer.release()
        libVLC.release()
    }
}
