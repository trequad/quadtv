package net.trequad.quadtv.player

class PlaybackFailureHandler {
    fun alternateEngine(failedEngine: PlayerEngine): PlayerEngine {
        return when (failedEngine) {
            PlayerEngine.EXOPLAYER -> PlayerEngine.VLC
            PlayerEngine.VLC -> PlayerEngine.EXOPLAYER
        }
    }

    fun shouldOfferRetryWithAlternate(failedEngine: PlayerEngine, error: Throwable? = null): Boolean {
        return true
    }
}
