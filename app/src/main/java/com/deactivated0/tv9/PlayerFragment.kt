package com.deactivated0.tv9

import android.media.MediaCodec
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.AspectRatioFrameLayout
import com.deactivated0.tv9.databinding.PlayerBinding
import com.deactivated0.tv9.models.TVModel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class PlayerFragment : Fragment() {
    private var _binding: PlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    @OptIn(UnstableApi::class)
    private var trackSelector: DefaultTrackSelector? = null
    @OptIn(UnstableApi::class)
    private var allocator: DefaultAllocator? = null

    private var tvModel: TVModel? = null
    private var currentVideoSize: VideoSize = VideoSize.UNKNOWN

    private lateinit var mainActivity: MainActivity

    var metadata = Metadata()
        private set

    private var desiredPlayWhenReady = false
    private var recreateCount = 0
    private var lastRecreateTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var playGeneration = 0
    private var startupTimeoutRunnable: Runnable? = null
    private var streamRetryCount = 0
    private var consecutiveRecoveryAttempts = 0

    private val watchdogRunnable = object : Runnable {
        private var lastPosition = -1L
        private var stuckTicks = 0
        private var bufferingTicks = 0

        fun reset() {
            lastPosition = -1L
            stuckTicks = 0
            bufferingTicks = 0
        }

        override fun run() {
            val p = player
            val model = tvModel
            if (p != null && desiredPlayWhenReady && model != null) {
                val state = p.playbackState
                val isPlaying = p.isPlaying
                val pos = p.currentPosition

                if (state == Player.STATE_BUFFERING) {
                    if (startupTimeoutRunnable == null) {
                        bufferingTicks++
                        if (bufferingTicks >= 5) { // 10s stuck buffering during live playback
                            AppLogger.w(TAG, "Watchdog: Buffering stall detected (>10s) on '${model.tv.title}', recovering...")
                            reset()
                            recoverPlayback()
                        }
                    } else {
                        bufferingTicks = 0
                    }
                } else {
                    bufferingTicks = 0
                }

                if (state == Player.STATE_READY && isPlaying) {
                    if (pos == lastPosition && pos > 0) {
                        stuckTicks++
                        if (stuckTicks >= 4) { // 8s stuck position
                            AppLogger.w(TAG, "Watchdog: Playback freeze detected (>8s at ${pos}ms) on '${model.tv.title}', recovering...")
                            reset()
                            recoverPlayback()
                        }
                    } else {
                        stuckTicks = 0
                        lastPosition = pos
                    }
                } else {
                    stuckTicks = 0
                    lastPosition = pos
                }

                if (state == Player.STATE_ENDED) {
                    AppLogger.w(TAG, "Watchdog: Stream ended on '${model.tv.title}', recovering...")
                    reset()
                    recoverPlayback()
                }
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun cancelStartupTimeout() {
        startupTimeoutRunnable?.let {
            handler.removeCallbacks(it)
            startupTimeoutRunnable = null
        }
    }

    private var selectedAudioIndex = -1
    private var selectedSubtitleIndex = -1 // -1 = Off, 0..N-1 = Track index

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        mainActivity = activity as MainActivity
        super.onActivityCreated(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)
        val playerView = _binding!!.playerView

        playerView.viewTreeObserver?.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            @OptIn(UnstableApi::class)
            @Suppress("DEPRECATION")
            override fun onGlobalLayout() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    playerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                } else {
                    playerView.viewTreeObserver.removeGlobalOnLayoutListener(this)
                }

                if (player == null) {
                    createPlayer()
                }

                (activity as MainActivity).ready(TAG)
                AppLogger.i(TAG, "Player ready")
            }
        })

        return _binding!!.root
    }

    @OptIn(UnstableApi::class)
    private fun createPlayer() {
        val context = requireContext()
        val renderersFactory = DefaultRenderersFactory(context)
        val extensionMode = if (SP.preferSoftwareDecoder) {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        } else {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        }
        renderersFactory.setExtensionRendererMode(extensionMode)
        renderersFactory.setEnableDecoderFallback(true)
        renderersFactory.setAllowedVideoJoiningTimeMs(5000)
        renderersFactory.setMediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
            val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                .getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
            if (SP.preferSoftwareDecoder) {
                decoders.sortedWith(compareByDescending { info ->
                    info.softwareOnly || info.name.contains("google", ignoreCase = true) || !info.hardwareAccelerated
                })
            } else {
                decoders
            }
        }

        val selector = DefaultTrackSelector(context)
        trackSelector = selector
        applyResolutionConstraint()

        val alloc = DefaultAllocator(true, 64 * 1024)
        allocator = alloc
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(alloc)
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setTargetBufferBytes(TARGET_BUFFER_BYTES)
            .setBackBuffer(0, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setLoadErrorHandlingPolicy(TVModel.ContinuousLiveLoadErrorHandlingPolicy())

        player = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        binding.playerView.player = player
        applyAspectRatio()
        player?.repeatMode = Player.REPEAT_MODE_OFF
        player?.playWhenReady = desiredPlayWhenReady
        player?.addAnalyticsListener(metadataListener)
        player?.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                currentVideoSize = videoSize
                applyAspectRatio()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) {
                    cancelStartupTimeout()
                    streamRetryCount = 0
                    consecutiveRecoveryAttempts = 0
                    hidePlayerLoading()
                    tvModel?.confirmSourceType()
                    tvModel?.setErrInfo("")
                    tvModel?.retryTimes = 0
                    AppLogger.i(TAG, "${tvModel?.tv?.title} Playback started/resumed")
                } else {
                    AppLogger.i(TAG, "${tvModel?.tv?.title} Playback stopped")
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    cancelStartupTimeout()
                    streamRetryCount = 0
                    consecutiveRecoveryAttempts = 0
                    hidePlayerLoading()
                } else if (playbackState == Player.STATE_BUFFERING) {
                    showPlayerLoading(immediate = false)
                } else if (playbackState == Player.STATE_ENDED) {
                    AppLogger.w(TAG, "Stream reached STATE_ENDED — auto-recovering live stream")
                    recoverPlayback()
                }
                super.onPlaybackStateChanged(playbackState)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    mainActivity.onPlayEnd()
                }
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            }

            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                applyResolutionConstraint()
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSelected(i)) {
                                val fmt = group.getTrackFormat(i)
                                AppLogger.i(TAG, "Active video track: ${fmt.width}x${fmt.height} @ ${fmt.bitrate} bps (${fmt.codecs})")
                            }
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                handlePlayerError(error)
            }
        })
    }

    /**
     * Applies the user-selected aspect ratio mode (16:9, 4:3, Fit, Stretch, Zoom) without reallocating views.
     */
    @OptIn(UnstableApi::class)
    fun applyAspectRatio() {
        val playerView = _binding?.playerView ?: return
        val contentFrame = playerView.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)

        val lp = playerView.layoutParams
        if (lp != null && (lp.width != ViewGroup.LayoutParams.MATCH_PARENT || lp.height != ViewGroup.LayoutParams.MATCH_PARENT)) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            playerView.layoutParams = lp
        }

        when (SP.aspectRatioMode) {
            SP.ASPECT_RATIO_16_9 -> {
                contentFrame?.setAspectRatio(16f / 9f)
                contentFrame?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            SP.ASPECT_RATIO_4_3 -> {
                contentFrame?.setAspectRatio(4f / 3f)
                contentFrame?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            SP.ASPECT_RATIO_FIT -> {
                val ratio = if (currentVideoSize.height > 0) {
                    currentVideoSize.width.toFloat() / currentVideoSize.height.toFloat()
                } else {
                    16f / 9f
                }
                contentFrame?.setAspectRatio(ratio)
                contentFrame?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            SP.ASPECT_RATIO_STRETCH -> {
                contentFrame?.setAspectRatio(0f)
                contentFrame?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
            SP.ASPECT_RATIO_ZOOM -> {
                contentFrame?.setAspectRatio(0f)
                contentFrame?.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        }
    }

    fun cycleAspectRatio(): String {
        SP.aspectRatioMode = (SP.aspectRatioMode + 1) % SP.ASPECT_RATIO_COUNT
        applyAspectRatio()
        val label = SP.getAspectRatioLabel()
        showShortToast("Aspect Ratio: $label")
        return label
    }

    fun cycleResolution(): String {
        SP.resolutionMode = (SP.resolutionMode + 1) % SP.RESOLUTION_COUNT
        val label = SP.getResolutionLabel()
        val toastDesc = if (SP.resolutionMode == SP.RESOLUTION_AUTO) "Auto (Native)" else label
        showShortToast("Resolution: $toastDesc")

        recreatePlayerAndRetryCurrent(isForced = true)
        return label
    }

    @OptIn(UnstableApi::class)
    fun applyResolutionConstraint() {
        val selector = trackSelector ?: return
        val dm = resources.displayMetrics
        val nativeW = max(dm.widthPixels, dm.heightPixels)
        val nativeH = min(dm.widthPixels, dm.heightPixels)

        val (minW, minH, maxW, maxH) = when (SP.resolutionMode) {
            SP.RESOLUTION_360P -> listOf(0, 0, 640, 360)
            SP.RESOLUTION_480P -> listOf(0, 0, 854, 480)
            SP.RESOLUTION_720P -> listOf(0, 0, 1280, 720)
            SP.RESOLUTION_1080P -> listOf(0, 0, 1920, 1080)
            SP.RESOLUTION_MAX -> listOf(0, 0, Int.MAX_VALUE, Int.MAX_VALUE)
            SP.RESOLUTION_AUTO -> {
                val aspect = nativeW.toFloat() / max(1, nativeH).toFloat()
                if (aspect < 1.6f && nativeH <= 480) {
                    listOf(0, 0, 854, 480)
                } else {
                    listOf(0, 0, nativeW, nativeH)
                }
            }
            else -> listOf(0, 0, nativeW, nativeH)
        }

        val maxBitrate = when (SP.resolutionMode) {
            SP.RESOLUTION_360P -> 1_200_000
            SP.RESOLUTION_480P -> 2_500_000
            SP.RESOLUTION_720P -> 6_000_000
            SP.RESOLUTION_1080P -> 15_000_000
            SP.RESOLUTION_MAX -> Int.MAX_VALUE
            SP.RESOLUTION_AUTO -> if (nativeH <= 480) 2_500_000 else if (nativeH <= 720) 6_000_000 else Int.MAX_VALUE
            else -> Int.MAX_VALUE
        }

        val paramsBuilder = selector.buildUponParameters()
            .setMinVideoSize(minW, minH)
            .setMaxVideoSize(maxW, maxH)
            .setMaxVideoBitrate(maxBitrate)
            .setViewportSize(nativeW, nativeH, false)
            .setExceedVideoConstraintsIfNecessary(true)
            .setAllowVideoMixedMimeTypeAdaptiveness(false)
            .setAllowVideoNonSeamlessAdaptiveness(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)

        if (nativeH <= 480 && Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT && SP.resolutionMode != SP.RESOLUTION_MAX) {
            paramsBuilder.setMaxVideoFrameRate(30)
        }

        val currentPlayer = player
        if (currentPlayer != null) {
            val tracks = currentPlayer.currentTracks
            var targetGroup: TrackGroup? = null
            var targetIndex = -1
            var bestScore = Long.MIN_VALUE

            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_VIDEO) continue
                val mediaGroup = group.mediaTrackGroup
                for (i in 0 until mediaGroup.length) {
                    val fmt = mediaGroup.getFormat(i)
                    val score = getTrackResolutionScore(fmt, SP.resolutionMode, nativeH)
                    if (score > bestScore) {
                        bestScore = score
                        targetGroup = mediaGroup
                        targetIndex = i
                    }
                }
            }

            val needsOverride = SP.resolutionMode != SP.RESOLUTION_AUTO ||
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ||
                (targetGroup != null && targetIndex >= 0 && targetGroup.getFormat(targetIndex).height <= 0)

            if (targetGroup != null && targetIndex >= 0 && needsOverride) {
                paramsBuilder.setOverrideForType(TrackSelectionOverride(targetGroup, targetIndex))
                val chosenFmt = targetGroup.getFormat(targetIndex)
                AppLogger.i(TAG, "Explicit track override: ${chosenFmt.width}x${chosenFmt.height} @ ${chosenFmt.bitrate} bps for ${SP.getResolutionLabel()}")
            } else {
                paramsBuilder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            }
        }

        selector.setParameters(paramsBuilder)
        AppLogger.i(TAG, "Applied resolution: ${SP.getResolutionLabel()} (range ${minW}x${minH}..${maxW}x${maxH}, maxBitrate $maxBitrate)")
    }

    private fun getTrackResolutionScore(fmt: Format, mode: Int, nativeH: Int): Long {
        val h = fmt.height
        val br = fmt.bitrate

        if (h > 0) {
            val targetH = when (mode) {
                SP.RESOLUTION_360P -> 360
                SP.RESOLUTION_480P -> 480
                SP.RESOLUTION_720P -> 720
                SP.RESOLUTION_1080P -> 1080
                SP.RESOLUTION_MAX -> 2160
                SP.RESOLUTION_AUTO -> nativeH
                else -> nativeH
            }
            return 100_000_000L - abs(h - targetH) * 10_000L
        }

        if (br > 0) {
            val targetBr = when (mode) {
                SP.RESOLUTION_360P -> 700_000
                SP.RESOLUTION_480P -> 1_500_000
                SP.RESOLUTION_720P -> 3_500_000
                SP.RESOLUTION_1080P -> 7_000_000
                SP.RESOLUTION_MAX -> 25_000_000
                SP.RESOLUTION_AUTO -> if (nativeH <= 480) 1_500_000 else if (nativeH <= 720) 3_500_000 else if (nativeH <= 1080) 7_000_000 else 25_000_000
                else -> 1_500_000
            }
            return 100_000_000L - abs(br - targetBr).toLong()
        }

        return Long.MIN_VALUE
    }

    /**
     * Rebuilds the ExoPlayer instance when the user toggles Hardware vs FFmpeg Software decoding.
     */
    fun updateDecoderMode() {
        val currentModel = tvModel
        releasePlayer()
        createPlayer()
        if (currentModel != null) {
            play(currentModel)
        }
    }

    /**
     * Cycles through available audio tracks in the current stream.
     */
    @OptIn(UnstableApi::class)
    fun cycleAudioTrack(): String {
        val currentPlayer = player ?: return "Default"
        val groups = currentPlayer.currentTracks.groups
        val audioTracks = ArrayList<Pair<Tracks.Group, Int>>(4)
        for (i in 0 until groups.size) {
            val g = groups[i]
            if (g.type == C.TRACK_TYPE_AUDIO) {
                for (t in 0 until g.length) {
                    if (g.isTrackSupported(t)) {
                        audioTracks.add(Pair(g, t))
                    }
                }
            }
        }
        if (audioTracks.isEmpty()) {
            showShortToast("Audio Track: Default (1/1)")
            return "Default (1/1)"
        }
        selectedAudioIndex = (selectedAudioIndex + 1) % audioTracks.size
        val (targetGroup, trackIndex) = audioTracks[selectedAudioIndex]
        val format = targetGroup.getTrackFormat(trackIndex)
        val lang = format.language?.takeIf { it.isNotBlank() && it != "und" }
            ?: format.label?.takeIf { it.isNotBlank() }
            ?: "Track ${selectedAudioIndex + 1}"
        val mime = format.sampleMimeType?.substringAfter('/')?.uppercase() ?: ""
        val label = "$lang${if (mime.isNotEmpty()) " ($mime)" else ""} [${selectedAudioIndex + 1}/${audioTracks.size}]"

        currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(targetGroup.mediaTrackGroup, trackIndex))
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()

        showShortToast("Audio: $label")
        return label
    }

    /**
     * Cycles through available subtitle tracks (including Off) in the current stream.
     */
    @OptIn(UnstableApi::class)
    fun cycleSubtitleTrack(): String {
        val currentPlayer = player ?: return "Off"
        val groups = currentPlayer.currentTracks.groups
        val textTracks = ArrayList<Pair<Tracks.Group, Int>>(4)
        for (i in 0 until groups.size) {
            val g = groups[i]
            if (g.type == C.TRACK_TYPE_TEXT) {
                for (t in 0 until g.length) {
                    if (g.isTrackSupported(t)) {
                        textTracks.add(Pair(g, t))
                    }
                }
            }
        }
        if (textTracks.isEmpty()) {
            showShortToast("Subtitles: None available")
            return "None"
        }
        // Cycle: -1 (Off) -> 0 -> 1 -> ... -> textTracks.size - 1 -> -1
        selectedSubtitleIndex++
        if (selectedSubtitleIndex >= textTracks.size) {
            selectedSubtitleIndex = -1
        }
        if (selectedSubtitleIndex == -1) {
            currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            showShortToast("Subtitles: Off")
            return "Off"
        } else {
            val (targetGroup, trackIndex) = textTracks[selectedSubtitleIndex]
            val format = targetGroup.getTrackFormat(trackIndex)
            val lang = format.language?.takeIf { it.isNotBlank() && it != "und" }
                ?: format.label?.takeIf { it.isNotBlank() }
                ?: "Sub ${selectedSubtitleIndex + 1}"
            val label = "$lang [${selectedSubtitleIndex + 1}/${textTracks.size}]"

            currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(targetGroup.mediaTrackGroup, trackIndex))
                .build()
            showShortToast("Subtitles: $label")
            return label
        }
    }

    @OptIn(UnstableApi::class)
    fun play(tvModel: TVModel) {
        this.tvModel = tvModel
        selectedAudioIndex = -1
        selectedSubtitleIndex = -1
        streamRetryCount = 0
        consecutiveRecoveryAttempts = 0
        watchdogRunnable.reset()
        metadata = Metadata()
        val currentGeneration = ++playGeneration
        if (player == null) {
            createPlayer()
        }
        player?.run {
            IgnoreSSLCertificate.ignore()

            val dataSource = tvModel.getSource()
            if (dataSource != null) {
                setMediaSource(dataSource)
            } else {
                setMediaItem(tvModel.getMediaItem())
            }

            prepare()
            playWhenReady = true
            desiredPlayWhenReady = true
            showPlayerLoading(immediate = true)
            cancelStartupTimeout()
            val timeoutTask = Runnable {
                handleStartupTimeout(currentGeneration)
            }
            startupTimeoutRunnable = timeoutTask
            handler.postDelayed(timeoutTask, STARTUP_TIMEOUT_MS)
        }
    }

    private fun handlePlayerError(error: PlaybackException) {
        hidePlayerLoading()
        AppLogger.e(
            TAG,
            "Playback error [${error.errorCode}] ${error.errorCodeName}: ${error.message}",
            error
        )

        // Instantly recover from live window desync or HLS playlist reset
        val isLiveDesync = error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
            error.cause is androidx.media3.exoplayer.source.BehindLiveWindowException ||
            error.cause is androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker.PlaylistResetException
        if (isLiveDesync) {
            AppLogger.w(TAG, "Live stream desync [${error.errorCodeName}], resyncing to live position")
            player?.seekToDefaultPosition()
            player?.prepare()
            return
        }

        if (isFatalCodecError(error)) {
            AppLogger.w(TAG, "Fatal codec error, switching to software decoder fallback and recreating player")
            SP.preferSoftwareDecoder = true
            recreatePlayerAndRetryCurrent()
            return
        }

        // Seamless auto-reconnect for any transient error during continuous live playback
        if (streamRetryCount < MAX_STREAM_RETRIES) {
            streamRetryCount++
            AppLogger.w(TAG, "Stream issue [${error.errorCode}] ${error.errorCodeName}, refreshing source ($streamRetryCount/$MAX_STREAM_RETRIES)...")
            recoverPlayback()
            return
        }

        // Before failing the channel, automatically try the next backup stream URI if available
        val model = tvModel
        if (model != null && model.tryNextUriOnError()) {
            streamRetryCount = 0
            AppLogger.i(TAG, "Switching to backup line ${model.getSourceLineLabel()}...")
            showShortToast("Switching to backup line ${model.getSourceLineLabel()}...")
            model.setReady()
            return
        }

        showPlaybackError(error)
        handleRecoverablePlayerError()
    }

    private fun recoverPlayback() {
        val model = tvModel ?: return
        watchdogRunnable.reset()
        consecutiveRecoveryAttempts++
        AppLogger.w(TAG, "Auto-recovering stream (attempt $consecutiveRecoveryAttempts) for '${model.tv.title}'")

        if (consecutiveRecoveryAttempts >= 3) {
            consecutiveRecoveryAttempts = 0
            if (model.tryNextUriOnError()) {
                AppLogger.i(TAG, "Switching to backup line ${model.getSourceLineLabel()}...")
                showShortToast("Switching to backup line ${model.getSourceLineLabel()}...")
                model.setReady()
                return
            }
            AppLogger.w(TAG, "Recreating player after repeated recovery attempts...")
            recreatePlayerAndRetryCurrent()
            return
        }

        showPlayerLoading(immediate = true)
        handler.postDelayed({
            try {
                val currentM = tvModel ?: return@postDelayed
                val dataSource = currentM.getSource()
                if (dataSource != null) {
                    player?.setMediaSource(dataSource)
                } else {
                    player?.setMediaItem(currentM.getMediaItem())
                }
                player?.seekToDefaultPosition()
                player?.prepare()
                player?.play()
            } catch (e: Throwable) {
                AppLogger.e(TAG, "Error in recoverPlayback", e)
                recreatePlayerAndRetryCurrent()
            }
        }, 500L)
    }

    private fun handleRecoverablePlayerError() {
        mainActivity.playNextAfterPlaybackError(tvModel)
    }

    private fun showPlaybackError(error: PlaybackException) {
        showShortToast("Error [${error.errorCode}] ${error.errorCodeName}")
    }

    private fun showShortToast(message: String) {
        val toast = Toast.makeText(requireContext().applicationContext, message, Toast.LENGTH_SHORT)
        toast.show()
        handler.postDelayed({ toast.cancel() }, ERROR_TOAST_MS)
    }

    private fun failStartup() {
        val model = tvModel
        if (model != null && model.tryNextUriOnError()) {
            showShortToast("Timeout — trying backup line ${model.getSourceLineLabel()}...")
            model.setReady()
            return
        }
        showShortToast("Playback startup timeout")
        releasePlayer()
        mainActivity.playNextAfterPlaybackError(tvModel)
    }

    private val showLoadingRunnable = Runnable {
        _binding?.playerLoading?.visibility = View.VISIBLE
    }

    private fun showPlayerLoading(immediate: Boolean = false) {
        handler.removeCallbacks(showLoadingRunnable)
        if (immediate) {
            _binding?.playerLoading?.visibility = View.VISIBLE
        } else {
            handler.postDelayed(showLoadingRunnable, 800)
        }
    }

    private fun hidePlayerLoading() {
        handler.removeCallbacks(showLoadingRunnable)
        _binding?.playerLoading?.visibility = View.GONE
    }

    private fun handleStartupTimeout(generation: Int) {
        startupTimeoutRunnable = null
        val currentPlayer = player ?: return
        if (generation != playGeneration || currentPlayer.isPlaying) {
            return
        }
        if (currentPlayer.playbackState == Player.STATE_BUFFERING ||
            currentPlayer.playbackState == Player.STATE_IDLE
        ) {
            Log.w(TAG, "Playback startup timeout")
            failStartup()
        }
    }

    @OptIn(UnstableApi::class)
    private fun isFatalCodecError(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        ) {
            return true
        }
        var cause = error.cause
        while (cause != null) {
            if (cause is MediaCodecRenderer.DecoderInitializationException ||
                cause is androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException ||
                cause is androidx.media3.exoplayer.video.MediaCodecVideoDecoderException ||
                cause is IllegalStateException
            ) {
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                cause is MediaCodec.CodecException
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun canRecreate(isForced: Boolean = false): Boolean {
        if (isForced) {
            recreateCount = 0
            return true
        }
        val now = System.currentTimeMillis()
        if (now - lastRecreateTime > RECREATE_WINDOW_MS) {
            recreateCount = 0
        }
        if (recreateCount >= MAX_RECREATE_COUNT) {
            return false
        }
        recreateCount++
        lastRecreateTime = now
        return true
    }

    fun recreatePlayerAndRetryCurrent(isForced: Boolean = false) {
        val currentModel = tvModel
        releasePlayer()
        if (canRecreate(isForced) && currentModel != null) {
            createPlayer()
            play(currentModel)
        } else {
            mainActivity.playNextAfterPlaybackError(tvModel)
        }
    }

    private fun releasePlayer() {
        cancelStartupTimeout()
        watchdogRunnable.reset()
        playGeneration++
        hidePlayerLoading()
        player?.clearVideoSurface()
        player?.release()
        _binding?.playerView?.player = null
        player = null
        trackSelector = null
        allocator = null
    }

    override fun onStart() {
        super.onStart()
        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
    }

    override fun onResume() {
        super.onResume()
        player?.playWhenReady = desiredPlayWhenReady
        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(watchdogRunnable)
        desiredPlayWhenReady = player?.playWhenReady ?: desiredPlayWhenReady
        player?.playWhenReady = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(watchdogRunnable)
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        _binding = null
    }

    @OptIn(UnstableApi::class)
    fun onTrimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            allocator?.trim()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onLowMemory() {
        super.onLowMemory()
        allocator?.trim()
        System.gc()
    }

    protected fun triggerMetadata(metadata: Metadata) {
        if (::mainActivity.isInitialized) {
            mainActivity.onMetadataUpdated(metadata)
        }
    }

    private val metadataListener = @UnstableApi object : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                videoMimeType = format.sampleMimeType ?: "",
                videoWidth = format.width,
                videoHeight = format.height,
                videoColor = format.colorInfo?.toLogString() ?: "",
                videoFrameRate = format.frameRate,
                videoBitrate = format.bitrate,
            )
            triggerMetadata(metadata)
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(videoDecoder = decoderName)
            triggerMetadata(metadata)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                audioMimeType = format.sampleMimeType ?: "",
                audioChannels = format.channelCount,
                audioSampleRate = format.sampleRate,
            )
            triggerMetadata(metadata)
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(audioDecoder = decoderName)
            triggerMetadata(metadata)
        }
    }

    /** Metadata */
    data class Metadata(
        /** Video encoding */
        val videoMimeType: String = "",
        /** Video width */
        val videoWidth: Int = 0,
        /** Video height */
        val videoHeight: Int = 0,
        /** Video color */
        val videoColor: String = "",
        /** Video frame rate */
        val videoFrameRate: Float = 0f,
        /** Video bitrate */
        val videoBitrate: Int = 0,
        /** Video decoder */
        val videoDecoder: String = "",

        /** Audio encoding */
        val audioMimeType: String = "",
        /** Audio channels */
        val audioChannels: Int = 0,
        /** Audio sample rate */
        val audioSampleRate: Int = 0,
        /** Audio decoder */
        val audioDecoder: String = "",
    ) {
        fun toCompactHudString(): String {
            val sb = StringBuilder(48)
            if (videoWidth > 0 && videoHeight > 0) {
                sb.append(videoWidth).append('x').append(videoHeight)
            }
            if (videoFrameRate > 0f) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(videoFrameRate.toInt()).append("fps")
            }
            if (videoMimeType.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append(" • ")
                sb.append(videoMimeType.substringAfter('/').uppercase())
            }
            if (audioMimeType.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append('/')
                sb.append(audioMimeType.substringAfter('/').uppercase())
            }
            if (videoDecoder.isNotEmpty()) {
                val isSw = videoDecoder.contains("ffmpeg", ignoreCase = true) ||
                        videoDecoder.contains("sw", ignoreCase = true) ||
                        videoDecoder.contains("c2.android", ignoreCase = true) ||
                        videoDecoder.contains("omx.google", ignoreCase = true)
                sb.append(if (isSw) " [SW]" else " [HW]")
            }
            return sb.toString()
        }
    }

    companion object {
        private const val TAG = "PlayerFragment"
        private const val WATCHDOG_INTERVAL_MS = 2_000L
        private const val MAX_RECREATE_COUNT = 3
        private const val RECREATE_WINDOW_MS = 10_000L
        private const val MAX_STREAM_RETRIES = 10
        private const val TARGET_BUFFER_BYTES = 16 * 1024 * 1024
        private const val MIN_BUFFER_MS = 15_000
        private const val MAX_BUFFER_MS = 30_000
        private const val BUFFER_FOR_PLAYBACK_MS = 2_500
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000
        private const val STARTUP_TIMEOUT_MS = 18_000L
        private const val ERROR_TOAST_MS = 1_000L
    }
}
