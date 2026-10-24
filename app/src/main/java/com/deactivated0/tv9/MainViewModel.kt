import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deactivated0.tv9.Gua
import com.deactivated0.tv9.R
import com.deactivated0.tv9.SP
import com.deactivated0.tv9.Utils
import com.deactivated0.tv9.Utils.getDateFormat
import com.deactivated0.tv9.models.EPGXmlParser
import com.deactivated0.tv9.models.PlaylistEntry
import com.deactivated0.tv9.models.SourceType
import com.deactivated0.tv9.models.TV
import com.deactivated0.tv9.models.TVGroupModel
import com.deactivated0.tv9.models.TVListModel
import com.deactivated0.tv9.models.TVModel
import com.deactivated0.tv9.requests.HttpClient
import com.deactivated0.tv9.showToast
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Type

class MainViewModel : ViewModel() {
    val FILE_NAME = "channels.txt"
    private var timeFormat = if (SP.displaySeconds) "HH:mm:ss" else "HH:mm"

    private lateinit var appDirectory: File
    var listModel: List<TVModel> = emptyList()
    val groupModel = TVGroupModel()

    private val _channelsOk = MutableLiveData<Boolean>()
    val channelsOk: LiveData<Boolean>
        get() = _channelsOk

    fun setDisplaySeconds(displaySeconds: Boolean) {
        timeFormat = if (displaySeconds) "HH:mm:ss" else "HH:mm"
        SP.displaySeconds = displaySeconds
    }

    fun getTime(): String {
        return getDateFormat(timeFormat)
    }

    fun updateEPG() {
        val epgUrl = SP.epg
        if (!epgUrl.isNullOrEmpty()) {
            viewModelScope.launch {
                updateEPG(epgUrl)
            }
        }
    }

    fun updateConfig() {
        if (SP.configAutoLoad) {
            val active = SP.getPlaylistById(SP.activePlaylistId)
            if (active != null && active.url.startsWith("http")) {
                viewModelScope.launch {
                    update(active.url)
                    val epgUrl = SP.epg
                    if (!epgUrl.isNullOrEmpty()) {
                        updateEPG(epgUrl)
                    }
                }
                return
            }
            SP.config?.let {
                if (it.startsWith("http")) {
                    viewModelScope.launch {
                        update(it)
                        val epgUrl = SP.epg
                        if (!epgUrl.isNullOrEmpty()) {
                            updateEPG(epgUrl)
                        }
                    }
                }
            }
        }
    }

    fun init(context: Context) {
        groupModel.addTVListModel(TVListModel("My Favorites", 0))
        groupModel.addTVListModel(TVListModel("All Channels", 1))
        appDirectory = context.filesDir

        val cachedJson = SP.channelListJson
        if (!cachedJson.isNullOrEmpty()) {
            try {
                val list = Utils.gson.fromJson<List<TV>?>(cachedJson, TV_LIST_TYPE)
                if (list != null) {
                    list2Channel(list)
                } else {
                    list2Channel(emptyList())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                list2Channel(emptyList())
            }
        } else {
            val file = File(appDirectory, FILE_NAME)
            if (file.exists()) {
                try {
                    val str = file.readText()
                    if (str.isNotBlank()) {
                        str2List(str)
                    } else {
                        list2Channel(emptyList())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    file.deleteOnExit()
                    list2Channel(emptyList())
                }
            } else {
                val activePlaylist = SP.getPlaylistById(SP.activePlaylistId) ?: SP.getPlaylists().firstOrNull()
                if (activePlaylist != null) {
                    loadPlaylist(activePlaylist)
                } else {
                    list2Channel(emptyList())
                }
            }
        }
        _channelsOk.value = true
    }

    /**
     * Activates and loads a saved PlaylistEntry (either from inline content, local file://, or remote http(s)://).
     */
    fun loadPlaylist(entry: PlaylistEntry) {
        SP.activePlaylistId = entry.id
        if (entry.content.isNotBlank()) {
            if (str2List(entry.content)) {
                if (::appDirectory.isInitialized) {
                    File(appDirectory, FILE_NAME).writeText(entry.content)
                }
                SP.config = entry.url.ifBlank { "local://${entry.name}" }
                R.string.channel_import_success.showToast()
            } else {
                R.string.channel_import_error.showToast()
            }
        } else if (entry.url.isNotBlank()) {
            val formatted = Utils.formatUrl(entry.url)
            SP.config = formatted
            parseUri(Uri.parse(formatted))
        }
    }

    private suspend fun updateEPG(epg: String) {
        try {
            withContext(Dispatchers.IO) {
                val request = okhttp3.Request.Builder().url(epg).build()
                HttpClient.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) {
                            val res = EPGXmlParser().parse(body.byteStream())
                            withContext(Dispatchers.Main) {
                                for (m in listModel) {
                                    res[m.tv.name]?.let { m.setEpg(it) }
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "EPG status error: ${response.code()}")
                    }
                    Unit
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "EPG request error: ${e.message}")
        }
    }

    suspend fun update(serverUrl: String) {
        Log.i(TAG, "request $serverUrl")
        try {
            withContext(Dispatchers.IO) {
                val request = okhttp3.Request.Builder().url(serverUrl).build()
                HttpClient.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val str = response.body()!!.string()
                        withContext(Dispatchers.Main) {
                            if (str2List(str)) {
                                SP.config = serverUrl
                                R.string.channel_import_success.showToast()
                            } else {
                                R.string.channel_import_error.showToast()
                            }
                        }
                    } else {
                        Log.e(TAG, "Request status ${response.code()}")
                        R.string.channel_status_error.showToast()
                    }
                }
            }
        } catch (e: JsonSyntaxException) {
            e.printStackTrace()
            Log.e("JSON Parse Error", e.toString())
            R.string.channel_format_error.showToast()
        } catch (e: NullPointerException) {
            e.printStackTrace()
            Log.e("Null Pointer Error", e.toString())
            R.string.channel_read_error.showToast()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Request error $e")
            R.string.channel_request_error.showToast()
        }
    }

    fun reset(context: Context) {
        SP.channelListJson = ""
        SP.config = ""
        SP.clearPlaylists()
        context.deleteFile(FILE_NAME)
        list2Channel(emptyList())
    }

    fun parseUri(uri: Uri) {
        if (uri.scheme == "file") {
            val file = uri.toFile()
            Log.i(TAG, "file $file")
            val str = if (file.exists()) {
                file.readText()
            } else {
                R.string.file_not_exist.showToast()
                return
            }

            try {
                if (str2List(str)) {
                    SP.config = uri.toString()
                    R.string.channel_import_success.showToast()
                } else {
                    R.string.channel_import_error.showToast()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                file.deleteOnExit()
                R.string.channel_read_error.showToast()
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                update(uri.toString())
            }
        }
    }

    fun str2List(str: String): Boolean {
        var string = str
        if (GUA_INSTANCE.verify(str)) {
            string = GUA_INSTANCE.decode(str)
        }
        if (string.isBlank()) {
            return false
        }

        val list: List<TV>

        when (string.trimStart().firstOrNull()) {
            '[' -> {
                try {
                    list = Utils.gson.fromJson(string, TV_LIST_TYPE)
                    SP.channelListJson = string
                    Log.i(TAG, "Import channels ${list.size}")
                } catch (e: Exception) {
                    Log.i(TAG, "parse error $string")
                    Log.i(TAG, e.message, e)
                    return false
                }
            }

            '#' -> {
                val tvMap = LinkedHashMap<String, TV>()
                val urisMap = LinkedHashMap<String, MutableList<String>>()
                var pendingKey: String? = null
                var hasValidHeader = false

                for (line in string.lineSequence()) {
                    val trimmedLine = line.trim()
                    if (trimmedLine.isEmpty()) continue

                    if (trimmedLine.startsWith("#EXTM3U")) {
                        hasValidHeader = true
                        val foundEpg = fastExtractAttribute(trimmedLine, "x-tvg-url")
                            .ifEmpty { fastExtractAttribute(trimmedLine, "url-tvg") }
                        if (foundEpg.isNotEmpty()) {
                            SP.epg = foundEpg
                        }
                    } else if (trimmedLine.startsWith("#EXTINF")) {
                        hasValidHeader = true
                        val commaIdx = trimmedLine.lastIndexOf(',')
                        val headerPart = if (commaIdx >= 0) trimmedLine.substring(0, commaIdx) else trimmedLine
                        val title = if (commaIdx >= 0) trimmedLine.substring(commaIdx + 1).trim() else ""
                        val name = fastExtractAttribute(headerPart, "tvg-name").ifEmpty { title }
                        val group = fastExtractAttribute(headerPart, "group-title")
                        val logo = fastExtractAttribute(headerPart, "tvg-logo")

                        val key = "$group\u0000$name"
                        pendingKey = key
                        if (!tvMap.containsKey(key)) {
                            val uriList = ArrayList<String>(2)
                            urisMap[key] = uriList
                            tvMap[key] = TV(
                                -1,
                                name,
                                name.ifEmpty { title },
                                "",
                                logo,
                                "",
                                uriList,
                                emptyMap(),
                                group,
                                SourceType.UNKNOWN,
                                emptyList(),
                            )
                        }
                    } else if (!trimmedLine.startsWith("#")) {
                        if (pendingKey != null) {
                            urisMap[pendingKey]?.add(trimmedLine)
                            pendingKey = null
                        }
                    }
                }

                if (!hasValidHeader) {
                    Log.e(TAG, "Parse error, not start with #EXTM3U")
                    "Failed to import channels: invalid M3U file".showToast()
                    return false
                }

                list = ArrayList(tvMap.values)
                SP.channelListJson = Utils.gson.toJson(list)
                Log.i(TAG, "Import channels ${list.size}")
            }

            else -> {
                var group = "Channels"
                val tvMap = LinkedHashMap<String, MutableList<String>>()
                val groupMap = LinkedHashMap<String, Pair<String, String>>()
                for (line in string.lineSequence()) {
                    val trimmedLine = line.trim()
                    if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                        if (trimmedLine.contains("#genre#")) {
                            val commaIdx = trimmedLine.indexOf(',')
                            group = if (commaIdx >= 0) trimmedLine.substring(0, commaIdx).trim() else trimmedLine
                        } else if (trimmedLine.contains(',')) {
                            val commaIdx = trimmedLine.indexOf(',')
                            val title = trimmedLine.substring(0, commaIdx).trim()
                            val key = "$group\u0000$title"
                            var uriList = tvMap[key]
                            if (uriList == null) {
                                uriList = ArrayList(2)
                                tvMap[key] = uriList
                                groupMap[key] = Pair(group, title)
                            }
                            val urisPart = trimmedLine.substring(commaIdx + 1)
                            var startIdx = 0
                            while (startIdx < urisPart.length) {
                                val nextComma = urisPart.indexOf(',', startIdx)
                                val endIdx = if (nextComma >= 0) nextComma else urisPart.length
                                val u = urisPart.substring(startIdx, endIdx).trim()
                                if (u.isNotEmpty()) uriList.add(u)
                                if (nextComma < 0) break
                                startIdx = nextComma + 1
                            }
                        } else if (trimmedLine.startsWith("http://") ||
                            trimmedLine.startsWith("https://") ||
                            trimmedLine.startsWith("rtsp://") ||
                            trimmedLine.startsWith("rtp://")
                        ) {
                            val title = "Channel ${tvMap.size + 1}"
                            val key = "$group\u0000$title"
                            val uriList = ArrayList<String>(1)
                            uriList.add(trimmedLine)
                            tvMap[key] = uriList
                            groupMap[key] = Pair(group, title)
                        }
                    }
                }
                if (tvMap.isEmpty()) {
                    "Failed to import channels: unrecognized playlist format".showToast()
                    return false
                }
                val l = ArrayList<TV>(tvMap.size)
                for ((key, uris) in tvMap) {
                    val (grp, ttl) = groupMap[key]!!
                    l.add(
                        TV(
                            -1,
                            ttl,
                            ttl,
                            "",
                            "",
                            "",
                            uris,
                            emptyMap(),
                            grp,
                            SourceType.UNKNOWN,
                            emptyList(),
                        )
                    )
                }
                list = l
                SP.channelListJson = Utils.gson.toJson(list)
                Log.i(TAG, "Import channels ${list.size}")
            }
        }

        list2Channel(list)

        return true
    }

    private fun list2Channel(list: List<TV>) {
        groupModel.initTVGroup()

        val activePlaylistName = SP.getPlaylistById(SP.activePlaylistId)?.name
            ?: SP.getPlaylists().firstOrNull()?.name
            ?: "Channels"

        val map = LinkedHashMap<String, MutableList<TVModel>>()
        for (v in list) {
            val groupKey = if (v.group.isBlank()) activePlaylistName else v.group
            var groupList = map[groupKey]
            if (groupList == null) {
                groupList = ArrayList()
                map[groupKey] = groupList
            }
            groupList.add(TVModel(v))
        }

        val listModelNew = ArrayList<TVModel>(list.size)
        val favList = groupModel.getFavoritesList()
        favList?.initTVList()

        var groupIndex = 2
        var id = 0
        for ((k, groupItems) in map) {
            val listTVModel = TVListModel(k, groupIndex)
            for (listIndex in 0 until groupItems.size) {
                val v1 = groupItems[listIndex]
                v1.tv.id = id
                val liked = SP.getLike(id)
                v1.setLike(liked)
                v1.setGroupIndex(groupIndex)
                v1.listIndex = listIndex
                listModelNew.add(v1)
                if (liked) {
                    favList?.addTVModel(v1)
                }
                id++
            }
            listTVModel.setTVListModel(groupItems)
            groupModel.addTVListModel(listTVModel)
            groupIndex++
        }

        val allPlaylists = SP.getPlaylists()
        for (p in allPlaylists) {
            if (p.id != SP.activePlaylistId) {
                val plModel = TVListModel("📺 ${p.name}", groupIndex)
                groupModel.addTVListModel(plModel)
                groupIndex++
            }
        }

        listModel = listModelNew

        // All channels
        (groupModel.tvGroup.value as List<TVListModel>)[1].setTVListModel(listModel)

        groupModel.setChange()
    }

    companion object {
        private const val TAG = "MainViewModel"
        private val GUA_INSTANCE = Gua()
        private val TV_LIST_TYPE: Type = object : TypeToken<List<TV>>() {}.type

        fun fastExtractAttribute(line: String, attrName: String): String {
            val key = "$attrName=\""
            var idx = line.indexOf(key)
            var quoteChar = '"'
            if (idx < 0) {
                val keySingle = "$attrName='"
                idx = line.indexOf(keySingle)
                quoteChar = '\''
                if (idx < 0) return ""
            }
            val start = idx + attrName.length + 2
            val end = line.indexOf(quoteChar, start)
            return if (end > start) line.substring(start, end).trim() else ""
        }
    }
}