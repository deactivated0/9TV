package com.deactivated0.tv9

import MainViewModel
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.deactivated0.tv9.models.PlaylistEntry
import com.deactivated0.tv9.models.SourceType
import com.deactivated0.tv9.models.TV
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.IOException


class SimpleServer(private val context: Context, private val viewModel: MainViewModel) :
    NanoHTTPD(PORT) {
    private val handler = Handler(Looper.getMainLooper())
    private val cachedIndexHtml: String by lazy {
        loadHtmlFromResource(R.raw.index)
    }

    init {
        try {
            start()
            val host = PortUtil.lan()
            (context as MainActivity).setServer("$host:$PORT")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/api/settings" -> handleSettings()
            "/api/channels" -> handleChannelsFromFile(session)
            "/api/uri" -> handleChannelsFromUri(session)
            "/api/proxy" -> handleProxy(session)
            "/api/epg" -> handleEPG(session)
            "/api/channel" -> handleDefaultChannel(session)
            "/api/theme" -> handleTheme(session)
            "/api/aspect" -> handleAspectRatio(session)
            "/api/playlists" -> handleGetPlaylists()
            "/api/playlist/save" -> handleSavePlaylist(session)
            "/api/playlist/delete" -> handleDeletePlaylist(session)
            "/api/playlist/activate" -> handleActivatePlaylist(session)
            "/api/channel/save" -> handleSaveChannel(session)
            "/api/channel/delete" -> handleDeleteChannel(session)
            else -> handleStaticContent()
        }
    }

    data class RespChannel(
        val id: Int,
        val name: String,
        val title: String,
        val group: String,
        val logo: String,
        val uris: List<String>,
    )

    data class RespSettings(
        val channelUri: String,
        val channelDefault: Int,
        val proxy: String,
        val epg: String,
        val darkMode: Boolean,
        val aspectRatioMode: Int,
        val activePlaylistId: String,
        val playlists: List<PlaylistEntry>,
        val channels: List<RespChannel>,
    )

    private fun buildCurrentChannelList(): List<RespChannel> {
        val currentModels = viewModel.listModel
        val out = ArrayList<RespChannel>(currentModels.size)
        for (m in currentModels) {
            out.add(
                RespChannel(
                    id = m.tv.id,
                    name = m.tv.name,
                    title = m.tv.title,
                    group = m.tv.group,
                    logo = m.tv.logo,
                    uris = m.tv.uris,
                )
            )
        }
        return out
    }

    private fun handleSettings(): Response {
        val response: String
        try {
            val respSettings = RespSettings(
                channelUri = SP.config ?: "",
                channelDefault = SP.channel,
                proxy = SP.proxy ?: "",
                epg = SP.epg ?: "",
                darkMode = SP.darkMode,
                aspectRatioMode = SP.aspectRatioMode,
                activePlaylistId = SP.activePlaylistId,
                playlists = SP.getPlaylists(),
                channels = buildCurrentChannelList(),
            )
            response = Utils.gson.toJson(respSettings) ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                e.message
            )
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json", response)
    }

    private fun handleGetPlaylists(): Response {
        val payload = mapOf(
            "activePlaylistId" to SP.activePlaylistId,
            "playlists" to SP.getPlaylists(),
            "channels" to buildCurrentChannelList(),
        )
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            Utils.gson.toJson(payload)
        )
    }

    data class Req(
        var uri: String? = "",
        val proxy: String? = null,
        val epg: String? = null,
        val channel: Int? = null,
        val darkMode: Boolean? = null,
        val aspectRatioMode: Int? = null,
        val id: String? = null,
        val name: String? = null,
        val url: String? = null,
        val content: String? = null,
        val activate: Boolean? = null,
        val channelId: Int? = null,
        val title: String? = null,
        val group: String? = null,
        val logo: String? = null,
        val uris: List<String>? = null,
    )

    private fun handleSavePlaylist(session: IHTTPSession): Response {
        try {
            readBody(session)?.let { body ->
                val req = Utils.gson.fromJson(body, Req::class.java)
                val existing = if (!req.id.isNullOrBlank()) SP.getPlaylistById(req.id) else null
                val entry = PlaylistEntry(
                    id = req.id?.takeIf { it.isNotBlank() } ?: "pl_${System.currentTimeMillis()}",
                    name = req.name?.takeIf { it.isNotBlank() } ?: existing?.name ?: "Playlist",
                    url = req.url ?: existing?.url ?: "",
                    content = req.content ?: existing?.content ?: "",
                    enabled = true,
                )
                handler.post {
                    val saved = SP.savePlaylist(entry)
                    if (req.activate == true || SP.getPlaylists().size == 1 || SP.activePlaylistId == saved.id) {
                        (context as? MainActivity)?.playFirstOnNextChannelLoad()
                        viewModel.loadPlaylist(saved)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true}")
    }

    private fun handleDeletePlaylist(session: IHTTPSession): Response {
        try {
            readBody(session)?.let { body ->
                val req = Utils.gson.fromJson(body, Req::class.java)
                val targetId = req.id ?: ""
                if (targetId.isNotEmpty()) {
                    handler.post {
                        val wasActive = SP.activePlaylistId == targetId
                        SP.deletePlaylist(targetId)
                        if (wasActive) {
                            val nextActive = SP.getPlaylistById(SP.activePlaylistId)
                            if (nextActive != null) {
                                (context as? MainActivity)?.playFirstOnNextChannelLoad()
                                viewModel.loadPlaylist(nextActive)
                            } else {
                                viewModel.reset(context)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true}")
    }

    private fun handleActivatePlaylist(session: IHTTPSession): Response {
        try {
            readBody(session)?.let { body ->
                val req = Utils.gson.fromJson(body, Req::class.java)
                val targetId = req.id ?: ""
                if (targetId.isNotEmpty()) {
                    handler.post {
                        val entry = SP.getPlaylistById(targetId)
                        if (entry != null) {
                            (context as? MainActivity)?.playFirstOnNextChannelLoad()
                            viewModel.loadPlaylist(entry)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true}")
    }

    private fun handleSaveChannel(session: IHTTPSession): Response {
        try {
            readBody(session)?.let { body ->
                val req = Utils.gson.fromJson(body, Req::class.java)
                val title = req.title?.trim().orEmpty()
                val uris = req.uris?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                if (title.isNotEmpty() && uris.isNotEmpty()) {
                    handler.post {
                        val currentList = viewModel.listModel.map { it.tv }.toMutableList()
                        val chId = req.channelId ?: -1
                        val newTv = TV(
                            id = chId,
                            name = req.name?.trim()?.ifEmpty { title } ?: title,
                            title = title,
                            description = "",
                            logo = req.logo?.trim().orEmpty(),
                            image = "",
                            uris = uris,
                            headers = emptyMap(),
                            group = req.group?.trim()?.ifEmpty { "Custom" } ?: "Custom",
                            sourceType = SourceType.UNKNOWN,
                            child = emptyList(),
                        )
                        if (chId in 0 until currentList.size) {
                            currentList[chId] = newTv
                        } else {
                            currentList.add(newTv)
                        }
                        val json = Utils.gson.toJson(currentList)
                        viewModel.str2List(json)
                        File(context.filesDir, viewModel.FILE_NAME).writeText(json)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true}")
    }

    private fun handleDeleteChannel(session: IHTTPSession): Response {
        try {
            readBody(session)?.let { body ->
                val req = Utils.gson.fromJson(body, Req::class.java)
                val chId = req.channelId ?: -1
                if (chId >= 0) {
                    handler.post {
                        val currentList = viewModel.listModel.map { it.tv }.toMutableList()
                        if (chId in 0 until currentList.size) {
                            currentList.removeAt(chId)
                            val json = Utils.gson.toJson(currentList)
                            if (currentList.isEmpty()) {
                                SP.channelListJson = ""
                                File(context.filesDir, viewModel.FILE_NAME).delete()
                                viewModel.str2List("[]")
                            } else {
                                viewModel.str2List(json)
                                File(context.filesDir, viewModel.FILE_NAME).writeText(json)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true}")
    }

    private fun handleChannelsFromFile(session: IHTTPSession): Response {
        R.string.start_config_channel.showToast()
        try {
            readBody(session)?.let { body ->
                handler.post {
                    if (viewModel.str2List(body)) {
                        File(context.filesDir, viewModel.FILE_NAME).writeText(body)
                        val saved = SP.savePlaylist(
                            PlaylistEntry(
                                id = "pl_${System.currentTimeMillis()}",
                                name = "Uploaded Playlist (${SP.getPlaylists().size + 1})",
                                url = "file://uploaded",
                                content = body,
                                enabled = true,
                            )
                        )
                        SP.activePlaylistId = saved.id
                        SP.config = "file://"
                        (context as? MainActivity)?.playFirstAfterChannelLoad()
                        R.string.channel_import_success.showToast()
                    } else {
                        R.string.channel_import_error.showToast()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                e.message
            )
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "")
    }

    private fun handleChannelsFromUri(session: IHTTPSession): Response {
        R.string.start_config_channel.showToast()
        try {
            readBody(session)?.let { body ->
                val req = Utils.gson.fromJson(body, Req::class.java)
                if (!req.uri.isNullOrBlank()) {
                    val formatted = Utils.formatUrl(req.uri!!.trim())
                    val uri = Uri.parse(formatted)
                    Log.i(TAG, "uri $uri")
                    handler.post {
                        val saved = SP.savePlaylist(
                            PlaylistEntry(
                                id = "pl_${System.currentTimeMillis()}",
                                name = formatted.substringAfterLast('/').ifBlank { "Remote Playlist" },
                                url = formatted,
                                content = "",
                                enabled = true,
                            )
                        )
                        SP.activePlaylistId = saved.id
                        (context as? MainActivity)?.playFirstOnNextChannelLoad()
                        viewModel.parseUri(uri)
                    }
                }
            }
        } catch (e: IOException) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                e.message
            )
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "")
    }

    private fun handleProxy(session: IHTTPSession): Response {
        try {
            readBody(session)?.let { body ->
                val req = Utils.gson.fromJson(body, Req::class.java)
                handler.post {
                    if (req.proxy != null) {
                        SP.proxy = req.proxy
                        R.string.default_proxy_set_success.showToast()
                    } else {
                        R.string.default_proxy_set_failure.showToast()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                e.message
            )
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "")
    }

    private fun handleEPG(session: IHTTPSession): Response {
        try {
            readBody(session)?.let { body ->
                val req = Utils.gson.fromJson(body, Req::class.java)
                handler.post {
                    if (req.epg != null) {
                        SP.epg = req.epg
                        viewModel.updateEPG()
                        R.string.default_epg_set_success.showToast()
                    } else {
                        R.string.default_epg_set_failure.showToast()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                e.message
            )
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "")
    }

    private fun handleDefaultChannel(session: IHTTPSession): Response {
        R.string.start_set_default_channel.showToast()
        try {
            readBody(session)?.let { body ->
                val req = Utils.gson.fromJson(body, Req::class.java)
                handler.post {
                    if (req.channel != null && req.channel > -1) {
                        SP.channel = req.channel
                        R.string.default_channel_set_success.showToast()
                    } else {
                        R.string.default_channel_set_failure.showToast()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                e.message
            )
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "")
    }

    private fun handleTheme(session: IHTTPSession): Response {
        try {
            readBody(session)?.let { body ->
                val req = Utils.gson.fromJson(body, Req::class.java)
                handler.post {
                    if (req.darkMode != null) {
                        SP.darkMode = req.darkMode
                        (context as? MainActivity)?.applyTheme()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                e.message
            )
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "")
    }

    private fun handleAspectRatio(session: IHTTPSession): Response {
        try {
            readBody(session)?.let { body ->
                val req = Utils.gson.fromJson(body, Req::class.java)
                handler.post {
                    if (req.aspectRatioMode != null) {
                        SP.aspectRatioMode = req.aspectRatioMode
                        (context as? MainActivity)?.applyAspectRatio()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                e.message
            )
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "")
    }

    private fun readBody(session: IHTTPSession): String? {
        val map = HashMap<String, String>(2)
        session.parseBody(map)
        return map["postData"]
    }

    private fun handleStaticContent(): Response {
        return newFixedLengthResponse(Response.Status.OK, "text/html", cachedIndexHtml)
    }

    private fun loadHtmlFromResource(resourceId: Int): String {
        val inputStream = context.resources.openRawResource(resourceId)
        return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    companion object {
        const val TAG = "SimpleServer"
        const val PORT = 34567
    }
}