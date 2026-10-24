package com.deactivated0.tv9.models

import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.ParserException
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.deactivated0.tv9.AppLogger
import com.deactivated0.tv9.SP
import java.io.FileNotFoundException
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class TVModel(var tv: TV) : ViewModel() {
    private var _position: MutableLiveData<Int>? = null
    val position: LiveData<Int>
        get() {
            if (_position == null) _position = MutableLiveData(0)
            return _position!!
        }

    var retryTimes = 0
    var retryMaxTimes = 3
    var programUpdateTime = 0L

    private var _groupIndex = 0
    val groupIndex: Int
        get() = if (SP.showAllChannels || _groupIndex == 0) _groupIndex else _groupIndex - 1

    fun setGroupIndex(index: Int) {
        _groupIndex = index
    }

    fun getGroupIndexInAll(): Int {
        return _groupIndex
    }

    var listIndex = 0

    private val sources: ArrayList<SourceType> = ArrayList(5)
    private var sourceIndex = -1
    private var sourceBuilt = false

    private var _errInfo: MutableLiveData<String>? = null
    val errInfo: LiveData<String>
        get() {
            if (_errInfo == null) _errInfo = MutableLiveData("")
            return _errInfo!!
        }

    fun setErrInfo(info: String) {
        if (_errInfo == null) _errInfo = MutableLiveData(info)
        else _errInfo!!.value = info
        onChannelError?.invoke(this, info)
    }

    private var _epg: MutableLiveData<MutableList<EPG>>? = null
    val epg: LiveData<MutableList<EPG>>
        get() {
            if (_epg == null) _epg = MutableLiveData()
            return _epg!!
        }

    fun setEpg(epg: MutableList<EPG>) {
        if (_epg == null) _epg = MutableLiveData(epg)
        else _epg!!.value = epg
    }

    private var _program: MutableLiveData<MutableList<Program>>? = null
    val program: LiveData<MutableList<Program>>
        get() {
            if (_program == null) _program = MutableLiveData()
            return _program!!
        }

    fun getVideoIndexValue(): Int {
        val vIdx = _videoIndex?.value ?: max(0, tv.uris.size - 1)
        val count = tv.uris.size
        if (count <= 0) return 0
        return min(max(vIdx, 0), count - 1)
    }

    fun getUriCount(): Int = tv.uris.size

    fun getSourceLineLabel(): String {
        val total = tv.uris.size
        if (total <= 0) return "1/1"
        return "${getVideoIndexValue() + 1}/$total"
    }

    /**
     * Cycles to the next stream URL in tv.uris and rebuilds the MediaItem/MediaSource.
     * @return true if there are multiple URIs and the index changed.
     */
    fun nextVideoUri(): Boolean {
        val total = tv.uris.size
        if (total <= 1) return false
        val nextIdx = (getVideoIndexValue() + 1) % total
        if (_videoIndex == null) _videoIndex = MutableLiveData(nextIdx)
        else _videoIndex!!.value = nextIdx
        sourceBuilt = false
        buildSource()
        return true
    }

    /**
     * Attempts to advance to the next backup URI before giving up on this channel during playback failure.
     * @return true if another backup URI is available to try; false if all URIs have been tried.
     */
    fun tryNextUriOnError(): Boolean {
        val total = tv.uris.size
        if (total <= 1) return false
        if (retryTimes < total - 1) {
            retryTimes++
            val nextIdx = (getVideoIndexValue() + 1) % total
            if (_videoIndex == null) _videoIndex = MutableLiveData(nextIdx)
            else _videoIndex!!.value = nextIdx
            sourceBuilt = false
            buildSource()
            return true
        }
        retryTimes = 0
        return false
    }

    fun getVideoUrl(): String? {
        val uris = tv.uris
        if (uris.isEmpty()) {
            return null
        }
        val index = getVideoIndexValue()
        return uris[index]
    }

    private var _like: MutableLiveData<Boolean>? = null
    val like: LiveData<Boolean>
        get() {
            if (_like == null) _like = MutableLiveData(SP.getLike(tv.id))
            return _like!!
        }

    fun setLike(liked: Boolean) {
        if (_like != null) {
            if (_like!!.value != liked) {
                _like!!.value = liked
            }
        } else if (SP.getLike(tv.id) != liked) {
            _like = MutableLiveData(liked)
        }
        onLikeChanged?.invoke(this, liked)
    }

    private var _ready: MutableLiveData<Boolean>? = null
    val ready: LiveData<Boolean>
        get() {
            if (_ready == null) _ready = MutableLiveData()
            return _ready!!
        }

    fun setReady() {
        if (_ready == null) _ready = MutableLiveData(true)
        else _ready!!.value = true
        onChannelReady?.invoke(this)
    }

    private var _videoIndex: MutableLiveData<Int>? = null
    val videoIndex: LiveData<Int>
        get() {
            if (_videoIndex == null) _videoIndex = MutableLiveData(max(0, tv.uris.size - 1))
            return _videoIndex!!
        }

    private var userAgent = ""

    private lateinit var _mediaItem: MediaItem

    fun getMediaItem(): MediaItem {
        ensureSourceBuilt()
        if (::_mediaItem.isInitialized) {
            return _mediaItem
        } else {
            val url = getVideoUrl() ?: ""
            _mediaItem = buildMediaItem(url)
            return _mediaItem
        }
    }

    private fun buildMediaItem(uri: String): MediaItem {
        return MediaItem.Builder()
            .setUri(uri)
            .setLiveConfiguration(SHARED_LIVE_CONFIG)
            .build()
    }

    private lateinit var httpDataSource: DefaultHttpDataSource.Factory

    init {
        sources.add(SourceType.UNKNOWN)
    }

    fun update(t: TV) {
        tv = t
        sourceBuilt = false
    }

    private fun ensureSourceBuilt() {
        if (!sourceBuilt) {
            buildSource()
        }
    }

    @OptIn(UnstableApi::class)
    fun buildSource() {
        sourceBuilt = true
        val url = getVideoUrl() ?: return
        val uri = Uri.parse(url) ?: return
        val path = uri.path ?: return
        val scheme = uri.scheme ?: return

        val headers = tv.headers
        if (!headers.isNullOrEmpty()) {
            val factory = DefaultHttpDataSource.Factory()
                .setKeepPostFor302Redirects(true)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setDefaultRequestProperties(headers)
            for ((key, value) in headers) {
                if (key.equals("user-agent", ignoreCase = true)) {
                    userAgent = value
                    break
                }
            }
            httpDataSource = factory
        } else {
            httpDataSource = SHARED_HTTP_DATA_SOURCE_FACTORY
        }

        _mediaItem = buildMediaItem(url)

        val lowerPath = path.lowercase(Locale.ROOT)
        val lowerScheme = scheme.lowercase(Locale.ROOT)
        if (lowerPath.endsWith(".m3u8")) {
            addSource(SourceType.HLS)
        } else if (lowerPath.endsWith(".mpd")) {
            addSource(SourceType.DASH)
        } else if (lowerScheme == "rtsp" || lowerScheme == "rtp") {
            addSource(SourceType.RTSP)
        } else {
            val ext = lowerPath.substringAfterLast('.', "")
            if (ext.isNotEmpty() && (videoExtensions.contains(ext) || videoExtensions.contains(".$ext"))) {
                addSource(SourceType.PROGRESSIVE)
            } else {
                Log.w(TAG, "URL SourceType UNKNOWN: $url")
                addSource(SourceType.UNKNOWN)
            }
        }

        nextSource()
    }

    private fun addSource(sourceType: SourceType) {
        sources.clear()
        sources.add(sourceType)

        for (i in ALL_SOURCE_TYPES) {
            if (i != sourceType) {
                sources.add(i)
            }
        }
        sourceIndex = -1
    }

    fun getSourceType(): SourceType {
        return tv.sourceType
    }

    fun getSourceTypeCurrent(): SourceType {
        ensureSourceBuilt()
        return sources[max(0, min(sourceIndex, sources.size - 1))]
    }

    fun nextSource() {
        if (sources.isNotEmpty()) {
            sourceIndex = (sourceIndex + 1) % sources.size
        }
    }

    @OptIn(UnstableApi::class)
    fun getSource(): MediaSource? {
        ensureSourceBuilt()
        if (sources.isEmpty()) {
            return null
        }
        if (!::_mediaItem.isInitialized) {
            return null
        }
        sourceIndex = max(0, min(sourceIndex, sources.size - 1))

        val isDefaultHttp = !::httpDataSource.isInitialized || httpDataSource === SHARED_HTTP_DATA_SOURCE_FACTORY
        return when (sources[sourceIndex]) {
            SourceType.HLS -> {
                if (isDefaultHttp) SHARED_HLS_FACTORY.createMediaSource(_mediaItem)
                else HlsMediaSource.Factory(httpDataSource)
                    .setAllowChunklessPreparation(false)
                    .setLoadErrorHandlingPolicy(SHARED_RETRY_POLICY)
                    .createMediaSource(_mediaItem)
            }
            SourceType.RTSP -> if (userAgent.isEmpty()) {
                RtspMediaSource.Factory().createMediaSource(_mediaItem)
            } else {
                RtspMediaSource.Factory().setUserAgent(userAgent).createMediaSource(_mediaItem)
            }

            SourceType.DASH -> {
                if (isDefaultHttp) SHARED_DASH_FACTORY.createMediaSource(_mediaItem)
                else DashMediaSource.Factory(httpDataSource)
                    .setLoadErrorHandlingPolicy(SHARED_RETRY_POLICY)
                    .createMediaSource(_mediaItem)
            }
            SourceType.PROGRESSIVE -> {
                if (isDefaultHttp) SHARED_PROGRESSIVE_FACTORY.createMediaSource(_mediaItem)
                else ProgressiveMediaSource.Factory(httpDataSource)
                    .setLoadErrorHandlingPolicy(SHARED_RETRY_POLICY)
                    .createMediaSource(_mediaItem)
            }

            else -> null
        }
    }

    @OptIn(UnstableApi::class)
    class ContinuousLiveLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy(Int.MAX_VALUE) {
        override fun getMinimumLoadableRetryCount(dataType: Int): Int = Int.MAX_VALUE

        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val exception = loadErrorInfo.exception
            AppLogger.w("TVModel", "Load error in policy: ${exception.javaClass.simpleName} - ${exception.message}")
            if (exception is FileNotFoundException ||
                exception is ParserException ||
                (exception is HttpDataSource.InvalidResponseCodeException && (exception.responseCode == 404 || exception.responseCode == 410))
            ) {
                return 500L
            }
            val delay = super.getRetryDelayMsFor(loadErrorInfo)
            return if (delay == -9223372036854775807L) 1000L else min(delay, 2000L)
        }
    }

    fun confirmSourceType() {
        if (sourceIndex in 0 until sources.size) {
            tv.sourceType = sources[sourceIndex]
        }
    }

    companion object {
        private const val TAG = "TVModel"
        var onLikeChanged: ((TVModel, Boolean) -> Unit)? = null
        var onChannelReady: ((TVModel) -> Unit)? = null
        var onChannelError: ((TVModel, String) -> Unit)? = null

        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 4.4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/34.0.1847.114 Mobile Safari/537.36"
        private const val LIVE_TARGET_OFFSET_MS = 8_000L
        private const val LIVE_MIN_OFFSET_MS = 2_000L
        private const val LIVE_MAX_OFFSET_MS = 300_000L
        private const val LIVE_MIN_PLAYBACK_SPEED = 0.97f
        private const val LIVE_MAX_PLAYBACK_SPEED = 1.03f

        val videoExtensions = setOf(
            ".flv", ".mp4", ".avi", ".mkv", ".mov", ".mpeg", "wmv", "webm",
            "flv", "mp4", "avi", "mkv", "mov", "mpeg", ".wmv", ".webm"
        )

        private val ALL_SOURCE_TYPES = arrayOf(
            SourceType.PROGRESSIVE,
            SourceType.HLS,
            SourceType.RTSP,
            SourceType.DASH,
            SourceType.UNKNOWN
        )

        private val SHARED_LIVE_CONFIG: MediaItem.LiveConfiguration =
            MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(LIVE_TARGET_OFFSET_MS)
                .setMinOffsetMs(LIVE_MIN_OFFSET_MS)
                .setMaxOffsetMs(LIVE_MAX_OFFSET_MS)
                .setMinPlaybackSpeed(LIVE_MIN_PLAYBACK_SPEED)
                .setMaxPlaybackSpeed(LIVE_MAX_PLAYBACK_SPEED)
                .build()

        @OptIn(UnstableApi::class)
        private val SHARED_HTTP_DATA_SOURCE_FACTORY: DefaultHttpDataSource.Factory =
            DefaultHttpDataSource.Factory()
                .setUserAgent(DEFAULT_USER_AGENT)
                .setKeepPostFor302Redirects(true)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)

        @OptIn(UnstableApi::class)
        val SHARED_RETRY_POLICY: ContinuousLiveLoadErrorHandlingPolicy by lazy {
            ContinuousLiveLoadErrorHandlingPolicy()
        }

        @OptIn(UnstableApi::class)
        private val SHARED_HLS_FACTORY: HlsMediaSource.Factory by lazy {
            HlsMediaSource.Factory(SHARED_HTTP_DATA_SOURCE_FACTORY)
                .setAllowChunklessPreparation(false)
                .setLoadErrorHandlingPolicy(SHARED_RETRY_POLICY)
        }

        @OptIn(UnstableApi::class)
        private val SHARED_DASH_FACTORY: DashMediaSource.Factory by lazy {
            DashMediaSource.Factory(SHARED_HTTP_DATA_SOURCE_FACTORY)
                .setLoadErrorHandlingPolicy(SHARED_RETRY_POLICY)
        }

        @OptIn(UnstableApi::class)
        private val SHARED_PROGRESSIVE_FACTORY: ProgressiveMediaSource.Factory by lazy {
            ProgressiveMediaSource.Factory(SHARED_HTTP_DATA_SOURCE_FACTORY)
                .setLoadErrorHandlingPolicy(SHARED_RETRY_POLICY)
        }
    }
}
