package ai.factoredui.compose.media

private const val NO_SEEK_TIME = -1.0

@Suppress("UNUSED_PARAMETER")
private fun jsOpenAudio(
    source: String,
    onReady: (Double) -> Unit,
    onTime: (Double) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onEnded: () -> Unit,
    onFailed: (JsString) -> Unit,
    onBlocked: () -> Unit,
): JsAny = js(
    """
    (() => {
      const audio = new Audio();
      audio.preload = 'auto';
      audio.setAttribute('playsinline', '');
      audio.__blocked = onBlocked;
      audio.__failed = onFailed;
      const session = () => ('mediaSession' in navigator) ? navigator.mediaSession : null;
      const position = () => {
        const media = session();
        if (!media || !isFinite(audio.duration) || audio.duration <= 0) return;
        try { media.setPositionState({ duration: audio.duration, playbackRate: audio.playbackRate || 1, position: Math.min(audio.currentTime, audio.duration) }); } catch (e) {}
      };
      audio.addEventListener('loadedmetadata', () => { if (isFinite(audio.duration)) onReady(audio.duration); });
      audio.addEventListener('durationchange', () => { if (isFinite(audio.duration)) position(); });
      audio.addEventListener('timeupdate', () => { onTime(audio.currentTime); position(); });
      audio.addEventListener('playing', () => { const media = session(); if (media) media.playbackState = 'playing'; onPlay(); });
      audio.addEventListener('pause', () => { const media = session(); if (media) media.playbackState = 'paused'; if (!audio.ended) onPause(); });
      audio.addEventListener('ended', () => { const media = session(); if (media) media.playbackState = 'paused'; onEnded(); });
      audio.addEventListener('error', () => onFailed(String((audio.error && audio.error.message) || 'the audio could not be loaded')));
      audio.src = source;
      return audio;
    })()
    """,
)

@Suppress("UNUSED_PARAMETER")
private fun jsPlayAudio(handle: JsAny): Unit = js(
    """
    handle.play().catch((e) => {
      if (e && e.name === 'NotAllowedError') handle.__blocked();
      else if (!(e && e.name === 'AbortError')) handle.__failed(String((e && e.message) || e));
    })
    """,
)

@Suppress("UNUSED_PARAMETER")
private fun jsPauseAudio(handle: JsAny): Unit = js("handle.pause()")

@Suppress("UNUSED_PARAMETER")
private fun jsSeekAudio(handle: JsAny, seconds: Double): Unit = js("handle.currentTime = seconds")

@Suppress("UNUSED_PARAMETER")
private fun jsRateAudio(handle: JsAny, rate: Double): Unit = js("handle.playbackRate = rate")

@Suppress("UNUSED_PARAMETER")
private fun jsDescribeAudio(title: String, artist: String, album: String, artwork: String, actions: JsString, onAction: (JsString, Double) -> Unit): Unit = js(
    """
    (() => {
      if (!('mediaSession' in navigator)) return;
      const media = navigator.mediaSession;
      try {
        media.metadata = new MediaMetadata({ title: title, artist: artist, album: album, artwork: artwork ? [{ src: artwork }] : [] });
      } catch (e) {}
      for (const name of ['play', 'pause', 'previoustrack', 'nexttrack', 'seekbackward', 'seekforward', 'seekto', 'stop']) {
        try { media.setActionHandler(name, null); } catch (e) {}
      }
      for (const name of String(actions).split(',').filter((n) => n.length > 0)) {
        try { media.setActionHandler(name, (details) => onAction(name, details && details.seekTime != null ? details.seekTime : -1)); } catch (e) {}
      }
    })()
    """,
)

@Suppress("UNUSED_PARAMETER")
private fun jsCloseAudio(handle: JsAny): Unit = js(
    """
    (() => {
      handle.pause();
      handle.removeAttribute('src');
      handle.load();
      if ('mediaSession' in navigator) {
        try { navigator.mediaSession.metadata = null; navigator.mediaSession.playbackState = 'none'; } catch (e) {}
        for (const name of ['play', 'pause', 'previoustrack', 'nexttrack', 'seekbackward', 'seekforward', 'seekto', 'stop']) {
          try { navigator.mediaSession.setActionHandler(name, null); } catch (e) {}
        }
      }
    })()
    """,
)

internal class WebAudioEngine : AudioEngine {
    private var handle: JsAny? = null
    private var listener: AudioListener? = null

    override fun open(source: String, listener: AudioListener) {
        handle?.let(::jsCloseAudio)
        this.listener = listener
        handle = jsOpenAudio(
            source,
            { seconds -> listener.onReady(seconds) },
            { seconds -> listener.onTime(seconds) },
            { listener.onPlaying(true) },
            { listener.onPlaying(false) },
            { listener.onEnded() },
            { message -> listener.onFailed(message.toString()) },
            { listener.onBlocked() },
        )
    }

    override fun play() {
        handle?.let(::jsPlayAudio)
    }

    override fun pause() {
        handle?.let(::jsPauseAudio)
    }

    override fun seekTo(seconds: Double) {
        handle?.let { jsSeekAudio(it, seconds) }
    }

    override fun setRate(rate: Double) {
        handle?.let { jsRateAudio(it, rate) }
    }

    override fun describe(description: AudioDescription) {
        val heard = listener ?: return
        jsDescribeAudio(
            description.title.orEmpty(), description.artist.orEmpty(), description.album.orEmpty(), description.artwork.orEmpty(),
            description.actions.joinToString(",") { it.wire }.toJsString(),
        ) { name, seekTime -> MediaAction.named(name.toString())?.let { heard.onMediaAction(it, seekTime.takeIf { time -> time != NO_SEEK_TIME }) } }
    }

    override fun close() {
        handle?.let(::jsCloseAudio)
        handle = null
    }
}
