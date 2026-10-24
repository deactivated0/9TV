package com.deactivated0.tv9

import android.content.Context
import android.content.SharedPreferences
import com.deactivated0.tv9.models.PlaylistEntry
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

object SP {
    // If Change channel with up and down in reversed order or not
    private const val KEY_CHANNEL_REVERSAL = "channel_reversal"

    // If use channel num to select channel or not
    private const val KEY_CHANNEL_NUM = "channel_num"

    private const val KEY_TIME = "time"

    // If start app on device boot or not
    private const val KEY_BOOT_STARTUP = "boot_startup"

    // Position in list of the selected channel item
    private const val KEY_POSITION = "position"

    private const val KEY_POSITION_GROUP = "position_group"

    private const val KEY_POSITION_SUB = "position_sub"

    private const val KEY_REPEAT_INFO = "repeat_info"

    private const val KEY_CONFIG = "config"

    private const val KEY_CONFIG_AUTO_LOAD = "config_auto_load"

    private const val KEY_CHANNEL = "channel"

    private const val KEY_DEFAULT_LIKE = "default_like"

    private const val KEY_DISPLAY_SECONDS = "display_seconds"

    private const val KEY_SHOW_ALL_CHANNELS = "show_all_channels"

    private const val KEY_COMPACT_MENU = "compact_menu"

    private const val KEY_LIKE = "like"

    private const val KEY_PROXY = "proxy"

    private const val KEY_EPG = "epg"

    private const val KEY_VERSION = "version"

    private const val KEY_LOG_TIMES = "log_times"

    private const val KEY_CHANNEL_LIST_JSON = "channel_list_json"

    // Feature keys
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_ASPECT_RATIO_MODE = "aspect_ratio_mode"
    private const val KEY_RESOLUTION_MODE = "resolution_mode"
    private const val KEY_PREFER_SOFTWARE_DECODER = "prefer_software_decoder"
    private const val KEY_SHOW_STREAM_HUD = "show_stream_hud"

    // Multi-playlist management keys
    private const val KEY_PLAYLISTS_JSON = "playlists_json"
    private const val KEY_ACTIVE_PLAYLIST_ID = "active_playlist_id"

    // Empty default config URL — no preloaded channels
    const val DEFAULT_CONFIG_URL = ""
    const val DEFAULT_EPG = ""
    const val DEFAULT_CHANNEL = 0
    const val DEFAULT_SHOW_ALL_CHANNELS = false
    const val DEFAULT_COMPACT_MENU = true
    const val DEFAULT_DISPLAY_SECONDS = false
    const val DEFAULT_LOG_TIMES = 10
    const val DEFAULT_POSITION_GROUP = 1
    const val DEFAULT_POSITION = 0
    const val DEFAULT_DARK_MODE = true
    const val DEFAULT_ASPECT_RATIO_MODE = 0 // 0=16:9, 1=4:3, 2=Fit, 3=Stretch, 4=Zoom
    const val DEFAULT_RESOLUTION_MODE = 0 // 0=Auto (Native), 1=360p, 2=480p, 3=720p, 4=1080p, 5=Max
    const val DEFAULT_PREFER_SOFTWARE_DECODER = false
    const val DEFAULT_SHOW_STREAM_HUD = true

    const val ASPECT_RATIO_16_9 = 0
    const val ASPECT_RATIO_4_3 = 1
    const val ASPECT_RATIO_FIT = 2
    const val ASPECT_RATIO_STRETCH = 3
    const val ASPECT_RATIO_ZOOM = 4
    const val ASPECT_RATIO_COUNT = 5

    const val RESOLUTION_AUTO = 0
    const val RESOLUTION_360P = 1
    const val RESOLUTION_480P = 2
    const val RESOLUTION_720P = 3
    const val RESOLUTION_1080P = 4
    const val RESOLUTION_MAX = 5
    const val RESOLUTION_COUNT = 6

    private val PLAYLIST_LIST_TYPE: Type =
        object : TypeToken<MutableList<PlaylistEntry>>() {}.type

    private lateinit var sp: SharedPreferences

    // In-memory write-through caches to eliminate repeated SharedPreferences map lookups & boxing
    private var cachedChannelReversal = false
    private var cachedChannelNum = true
    private var cachedTime = true
    private var cachedBootStartup = false
    private var cachedPositionGroup = DEFAULT_POSITION_GROUP
    private var cachedPosition = DEFAULT_POSITION
    private var cachedPositionSub = 0
    private var cachedRepeatInfo = true
    private var cachedConfig: String? = DEFAULT_CONFIG_URL
    private var cachedConfigAutoLoad = false
    private var cachedChannel = DEFAULT_CHANNEL
    private var cachedCompactMenu = DEFAULT_COMPACT_MENU
    private var cachedShowAllChannels = DEFAULT_SHOW_ALL_CHANNELS
    private var cachedDefaultLike = false
    private var cachedDisplaySeconds = DEFAULT_DISPLAY_SECONDS
    private var cachedProxy: String? = ""
    private var cachedEpg: String? = DEFAULT_EPG
    private var cachedVersion: String? = ""
    private var cachedLogTimes = DEFAULT_LOG_TIMES
    private var cachedChannelListJson: String? = ""
    private var cachedDarkMode = DEFAULT_DARK_MODE
    private var cachedAspectRatioMode = DEFAULT_ASPECT_RATIO_MODE
    private var cachedResolutionMode = DEFAULT_RESOLUTION_MODE
    private var cachedPreferSoftwareDecoder = DEFAULT_PREFER_SOFTWARE_DECODER
    private var cachedShowStreamHud = DEFAULT_SHOW_STREAM_HUD
    private var cachedActivePlaylistId: String = ""
    private val cachedPlaylists = ArrayList<PlaylistEntry>()

    // Dual-set cache: Int HashSet for O(1) zero-allocation lookups, String HashSet for SharedPreferences persistence
    private val likedIdsInt = HashSet<Int>()
    private val likedIdsStr = HashSet<String>()

    /**
     * The method must be invoked as early as possible(At least before using the keys)
     */
    fun init(context: Context) {
        val appContext = context.applicationContext ?: context
        sp = appContext.getSharedPreferences(
            appContext.getString(R.string.app_name),
            Context.MODE_PRIVATE
        )

        // Warm up in-memory caches once on startup
        cachedChannelReversal = sp.getBoolean(KEY_CHANNEL_REVERSAL, false)
        cachedChannelNum = sp.getBoolean(KEY_CHANNEL_NUM, true)
        cachedTime = sp.getBoolean(KEY_TIME, true)
        cachedBootStartup = sp.getBoolean(KEY_BOOT_STARTUP, false)
        cachedPositionGroup = sp.getInt(KEY_POSITION_GROUP, DEFAULT_POSITION_GROUP)
        cachedPosition = sp.getInt(KEY_POSITION, DEFAULT_POSITION)
        cachedPositionSub = sp.getInt(KEY_POSITION_SUB, 0)
        cachedRepeatInfo = sp.getBoolean(KEY_REPEAT_INFO, true)
        cachedConfig = sp.getString(KEY_CONFIG, DEFAULT_CONFIG_URL)
        cachedConfigAutoLoad = sp.getBoolean(KEY_CONFIG_AUTO_LOAD, false)
        cachedChannel = sp.getInt(KEY_CHANNEL, DEFAULT_CHANNEL)
        cachedCompactMenu = sp.getBoolean(KEY_COMPACT_MENU, DEFAULT_COMPACT_MENU)
        cachedShowAllChannels = sp.getBoolean(KEY_SHOW_ALL_CHANNELS, DEFAULT_SHOW_ALL_CHANNELS)
        cachedDefaultLike = sp.getBoolean(KEY_DEFAULT_LIKE, false)
        cachedDisplaySeconds = sp.getBoolean(KEY_DISPLAY_SECONDS, DEFAULT_DISPLAY_SECONDS)
        cachedProxy = sp.getString(KEY_PROXY, "")
        cachedEpg = sp.getString(KEY_EPG, DEFAULT_EPG)
        cachedVersion = sp.getString(KEY_VERSION, "")
        cachedLogTimes = sp.getInt(KEY_LOG_TIMES, DEFAULT_LOG_TIMES)
        cachedChannelListJson = sp.getString(KEY_CHANNEL_LIST_JSON, "")
        cachedDarkMode = sp.getBoolean(KEY_DARK_MODE, DEFAULT_DARK_MODE)
        cachedAspectRatioMode = sp.getInt(KEY_ASPECT_RATIO_MODE, DEFAULT_ASPECT_RATIO_MODE)
            .coerceIn(0, ASPECT_RATIO_COUNT - 1)
        cachedResolutionMode = sp.getInt(KEY_RESOLUTION_MODE, DEFAULT_RESOLUTION_MODE)
            .coerceIn(0, RESOLUTION_COUNT - 1)
        cachedPreferSoftwareDecoder =
            sp.getBoolean(KEY_PREFER_SOFTWARE_DECODER, DEFAULT_PREFER_SOFTWARE_DECODER)
        cachedShowStreamHud = sp.getBoolean(KEY_SHOW_STREAM_HUD, DEFAULT_SHOW_STREAM_HUD)
        cachedActivePlaylistId = sp.getString(KEY_ACTIVE_PLAYLIST_ID, "") ?: ""

        cachedPlaylists.clear()
        val playlistsRaw = sp.getString(KEY_PLAYLISTS_JSON, "")
        if (!playlistsRaw.isNullOrEmpty()) {
            try {
                val parsed: List<PlaylistEntry>? =
                    Utils.gson.fromJson(playlistsRaw, PLAYLIST_LIST_TYPE)
                if (parsed != null) {
                    cachedPlaylists.addAll(parsed)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        likedIdsInt.clear()
        likedIdsStr.clear()
        sp.getStringSet(KEY_LIKE, null)?.forEach { s ->
            likedIdsStr.add(s)
            s.toIntOrNull()?.let { likedIdsInt.add(it) }
        }
    }

    fun getPlaylists(): List<PlaylistEntry> = cachedPlaylists

    fun getPlaylistById(id: String): PlaylistEntry? {
        for (i in 0 until cachedPlaylists.size) {
            val p = cachedPlaylists[i]
            if (p.id == id) return p
        }
        return null
    }

    var activePlaylistId: String
        get() = cachedActivePlaylistId
        set(value) {
            if (cachedActivePlaylistId != value) {
                cachedActivePlaylistId = value
                sp.edit().putString(KEY_ACTIVE_PLAYLIST_ID, value).apply()
            }
        }

    fun savePlaylist(entry: PlaylistEntry): PlaylistEntry {
        val actualId = entry.id.ifBlank { "pl_${System.currentTimeMillis()}" }
        val normalized = entry.copy(
            id = actualId,
            name = entry.name.ifBlank {
                if (entry.url.isNotBlank()) entry.url.substringAfterLast('/').ifBlank { "Playlist" }
                else "Local Playlist"
            }
        )
        var replaced = false
        for (i in 0 until cachedPlaylists.size) {
            if (cachedPlaylists[i].id == actualId) {
                cachedPlaylists[i] = normalized
                replaced = true
                break
            }
        }
        if (!replaced) {
            cachedPlaylists.add(normalized)
        }
        persistPlaylists()
        return normalized
    }

    fun deletePlaylist(id: String): Boolean {
        var removed = false
        val it = cachedPlaylists.iterator()
        while (it.hasNext()) {
            if (it.next().id == id) {
                it.remove()
                removed = true
            }
        }
        if (removed) {
            if (cachedActivePlaylistId == id) {
                cachedActivePlaylistId = cachedPlaylists.firstOrNull()?.id ?: ""
                sp.edit().putString(KEY_ACTIVE_PLAYLIST_ID, cachedActivePlaylistId).apply()
            }
            persistPlaylists()
        }
        return removed
    }

    fun clearPlaylists() {
        cachedPlaylists.clear()
        cachedActivePlaylistId = ""
        sp.edit()
            .remove(KEY_PLAYLISTS_JSON)
            .remove(KEY_ACTIVE_PLAYLIST_ID)
            .apply()
    }

    private fun persistPlaylists() {
        val json = Utils.gson.toJson(cachedPlaylists)
        sp.edit().putString(KEY_PLAYLISTS_JSON, json).apply()
    }

    var channelReversal: Boolean
        get() = cachedChannelReversal
        set(value) {
            if (cachedChannelReversal != value) {
                cachedChannelReversal = value
                sp.edit().putBoolean(KEY_CHANNEL_REVERSAL, value).apply()
            }
        }

    var channelNum: Boolean
        get() = cachedChannelNum
        set(value) {
            if (cachedChannelNum != value) {
                cachedChannelNum = value
                sp.edit().putBoolean(KEY_CHANNEL_NUM, value).apply()
            }
        }

    var time: Boolean
        get() = cachedTime
        set(value) {
            if (cachedTime != value) {
                cachedTime = value
                sp.edit().putBoolean(KEY_TIME, value).apply()
            }
        }

    var bootStartup: Boolean
        get() = cachedBootStartup
        set(value) {
            if (cachedBootStartup != value) {
                cachedBootStartup = value
                sp.edit().putBoolean(KEY_BOOT_STARTUP, value).apply()
            }
        }

    var positionGroup: Int
        get() = cachedPositionGroup
        set(value) {
            if (cachedPositionGroup != value) {
                cachedPositionGroup = value
                sp.edit().putInt(KEY_POSITION_GROUP, value).apply()
            }
        }

    var position: Int
        get() = cachedPosition
        set(value) {
            if (cachedPosition != value) {
                cachedPosition = value
                sp.edit().putInt(KEY_POSITION, value).apply()
            }
        }

    var positionSub: Int
        get() = cachedPositionSub
        set(value) {
            if (cachedPositionSub != value) {
                cachedPositionSub = value
                sp.edit().putInt(KEY_POSITION_SUB, value).apply()
            }
        }

    var repeatInfo: Boolean
        get() = cachedRepeatInfo
        set(value) {
            if (cachedRepeatInfo != value) {
                cachedRepeatInfo = value
                sp.edit().putBoolean(KEY_REPEAT_INFO, value).apply()
            }
        }

    var config: String?
        get() = cachedConfig
        set(value) {
            if (cachedConfig != value) {
                cachedConfig = value ?: DEFAULT_CONFIG_URL
                sp.edit().putString(KEY_CONFIG, value).apply()
            } else if (value == null) {
                cachedConfig = DEFAULT_CONFIG_URL
                sp.edit().putString(KEY_CONFIG, null).apply()
            }
        }

    var configAutoLoad: Boolean
        get() = cachedConfigAutoLoad
        set(value) {
            if (cachedConfigAutoLoad != value) {
                cachedConfigAutoLoad = value
                sp.edit().putBoolean(KEY_CONFIG_AUTO_LOAD, value).apply()
            }
        }

    var channel: Int
        get() = cachedChannel
        set(value) {
            if (cachedChannel != value) {
                cachedChannel = value
                sp.edit().putInt(KEY_CHANNEL, value).apply()
            }
        }

    var compactMenu: Boolean
        get() = cachedCompactMenu
        set(value) {
            if (cachedCompactMenu != value) {
                cachedCompactMenu = value
                sp.edit().putBoolean(KEY_COMPACT_MENU, value).apply()
            }
        }

    var showAllChannels: Boolean
        get() = cachedShowAllChannels
        set(value) {
            if (cachedShowAllChannels != value) {
                cachedShowAllChannels = value
                sp.edit().putBoolean(KEY_SHOW_ALL_CHANNELS, value).apply()
            }
        }

    var defaultLike: Boolean
        get() = cachedDefaultLike
        set(value) {
            if (cachedDefaultLike != value) {
                cachedDefaultLike = value
                sp.edit().putBoolean(KEY_DEFAULT_LIKE, value).apply()
            }
        }

    var displaySeconds: Boolean
        get() = cachedDisplaySeconds
        set(value) {
            if (cachedDisplaySeconds != value) {
                cachedDisplaySeconds = value
                sp.edit().putBoolean(KEY_DISPLAY_SECONDS, value).apply()
            }
        }

    var darkMode: Boolean
        get() = cachedDarkMode
        set(value) {
            if (cachedDarkMode != value) {
                cachedDarkMode = value
                sp.edit().putBoolean(KEY_DARK_MODE, value).apply()
            }
        }

    var aspectRatioMode: Int
        get() = cachedAspectRatioMode
        set(value) {
            val normalized = ((value % ASPECT_RATIO_COUNT) + ASPECT_RATIO_COUNT) % ASPECT_RATIO_COUNT
            if (cachedAspectRatioMode != normalized) {
                cachedAspectRatioMode = normalized
                sp.edit().putInt(KEY_ASPECT_RATIO_MODE, normalized).apply()
            }
        }

    fun getAspectRatioLabel(mode: Int = cachedAspectRatioMode): String {
        return when (mode) {
            ASPECT_RATIO_16_9 -> "16:9"
            ASPECT_RATIO_4_3 -> "4:3"
            ASPECT_RATIO_FIT -> "Fit"
            ASPECT_RATIO_STRETCH -> "Stretch"
            ASPECT_RATIO_ZOOM -> "Zoom"
            else -> "16:9"
        }
    }

    var resolutionMode: Int
        get() = cachedResolutionMode
        set(value) {
            val normalized = ((value % RESOLUTION_COUNT) + RESOLUTION_COUNT) % RESOLUTION_COUNT
            if (cachedResolutionMode != normalized) {
                cachedResolutionMode = normalized
                sp.edit().putInt(KEY_RESOLUTION_MODE, normalized).apply()
            }
        }

    fun getResolutionLabel(mode: Int = cachedResolutionMode): String {
        return when (mode) {
            RESOLUTION_AUTO -> "Auto"
            RESOLUTION_360P -> "360p"
            RESOLUTION_480P -> "480p"
            RESOLUTION_720P -> "720p"
            RESOLUTION_1080P -> "1080p"
            RESOLUTION_MAX -> "Max"
            else -> "Auto"
        }
    }

    var preferSoftwareDecoder: Boolean
        get() = cachedPreferSoftwareDecoder
        set(value) {
            if (cachedPreferSoftwareDecoder != value) {
                cachedPreferSoftwareDecoder = value
                sp.edit().putBoolean(KEY_PREFER_SOFTWARE_DECODER, value).apply()
            }
        }

    var showStreamHud: Boolean
        get() = cachedShowStreamHud
        set(value) {
            if (cachedShowStreamHud != value) {
                cachedShowStreamHud = value
                sp.edit().putBoolean(KEY_SHOW_STREAM_HUD, value).apply()
            }
        }

    fun getLike(id: Int): Boolean {
        return likedIdsInt.contains(id)
    }

    fun setLike(id: Int, liked: Boolean) {
        val changed = if (liked) {
            likedIdsStr.add(id.toString())
            likedIdsInt.add(id)
        } else {
            likedIdsStr.remove(id.toString())
            likedIdsInt.remove(id)
        }
        if (changed) {
            sp.edit().putStringSet(KEY_LIKE, HashSet(likedIdsStr)).apply()
        }
    }

    fun deleteLike() {
        if (likedIdsInt.isNotEmpty() || likedIdsStr.isNotEmpty()) {
            likedIdsInt.clear()
            likedIdsStr.clear()
        }
        sp.edit().remove(KEY_LIKE).apply()
    }

    var proxy: String?
        get() = cachedProxy
        set(value) {
            if (cachedProxy != value) {
                cachedProxy = value
                sp.edit().putString(KEY_PROXY, value).apply()
            }
        }

    var epg: String?
        get() = cachedEpg
        set(value) {
            if (cachedEpg != value) {
                cachedEpg = value
                sp.edit().putString(KEY_EPG, value).apply()
            }
        }

    var version: String?
        get() = cachedVersion
        set(value) {
            if (cachedVersion != value) {
                cachedVersion = value
                sp.edit().putString(KEY_VERSION, value).apply()
            }
        }

    var logTimes: Int
        get() = cachedLogTimes
        set(value) {
            if (cachedLogTimes != value) {
                cachedLogTimes = value
                sp.edit().putInt(KEY_LOG_TIMES, value).apply()
            }
        }

    var channelListJson: String?
        get() = cachedChannelListJson
        set(value) {
            if (cachedChannelListJson != value) {
                cachedChannelListJson = value
                sp.edit().putString(KEY_CHANNEL_LIST_JSON, value).apply()
            }
        }
}
