package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class IptvRepository(
    private val context: Context,
    private val configDao: IptvConfigDao
) : IptvHttpServer.HttpServerListener {

    val allConfigs: Flow<List<IptvConfig>> = configDao.getAllConfigs()
    val activeConfigFlow: Flow<IptvConfig?> = configDao.getActiveConfigFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Event updates for external view models
    private val _onCredentialsLoaded = MutableStateFlow<IptvConfig?>(null)
    val onCredentialsLoaded: StateFlow<IptvConfig?> = _onCredentialsLoaded.asStateFlow()

    // Local HTTP server instance
    private var httpServer: IptvHttpServer? = null

    init {
        startLocalServer()
    }

    fun startLocalServer() {
        if (httpServer == null) {
            httpServer = IptvHttpServer(8080, this)
            httpServer?.start()
        }
    }

    fun stopLocalServer() {
        httpServer?.stop()
        httpServer = null
    }

    fun clearReceivedCredentialEvent() {
        _onCredentialsLoaded.value = null
    }

    // --- Local Server Listeners (QR Code or M3U Mobile deploying) ---
    override fun onXtreamReceived(serverUrl: String, username: String, password: String) {
        Log.d("IptvRepository", "Received Xtream Credentials via Web Portal: $serverUrl")
        val cleanedUrl = cleanUrl(serverUrl)
        val config = IptvConfig(
            name = "Portal: ${cleanedUrl.substringAfter("://").substringBefore("/")}",
            type = "XTREAM",
            serverUrl = cleanedUrl,
            username = username,
            password = password,
            isActive = true
        )
        // Insert and dispatch to UI
        _onCredentialsLoaded.value = config
    }

    override fun onM3uReceived(fileName: String, content: String) {
        Log.d("IptvRepository", "Received M3U File upload via Web Portal: $fileName ($content)")
        try {
            val file = File(context.filesDir, "deployed_playlist.m3u")
            FileOutputStream(file).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            val config = IptvConfig(
                name = fileName.removeSuffix(".m3u"),
                type = "M3U",
                m3uFilePath = file.absolutePath,
                isActive = true
            )
            _onCredentialsLoaded.value = config
        } catch (e: Exception) {
            Log.e("IptvRepository", "Failed saving deployed M3U file", e)
        }
    }

    override fun onM3uUrlReceived(url: String) {
        Log.d("IptvRepository", "Received M3U URL Link via Web Portal: $url")
        val config = IptvConfig(
            name = "Link: " + url.substringAfter("://").take(15) + "...",
            type = "M3U",
            serverUrl = url,
            isActive = true
        )
        _onCredentialsLoaded.value = config
    }

    // --- DB Operations ---
    suspend fun insertConfig(config: IptvConfig): Int {
        return withContext(Dispatchers.IO) {
            if (config.isActive) {
                configDao.deactivateAllConfigs()
            }
            configDao.insertConfig(config).toInt()
        }
    }

    suspend fun deleteConfig(config: IptvConfig) {
        withContext(Dispatchers.IO) {
            // Delete associated file if exists
            config.m3uFilePath?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            configDao.deleteConfig(config)
        }
    }

    suspend fun selectConfig(id: Int) {
        withContext(Dispatchers.IO) {
            configDao.deactivateAllConfigs()
            configDao.activateConfig(id)
        }
    }

    suspend fun getActiveConfig(): IptvConfig? {
        return withContext(Dispatchers.IO) {
            configDao.getActiveConfig()
        }
    }

    suspend fun deactivateActive() {
        withContext(Dispatchers.IO) {
            configDao.deactivateAllConfigs()
        }
    }

    // --- IPTV Data Loading Engine ---
    suspend fun loadIptvItems(config: IptvConfig): List<IptvItem> = withContext(Dispatchers.IO) {
        try {
            if (config.type == "XTREAM") {
                loadXtreamItems(config)
            } else {
                loadM3uItems(config)
            }
        } catch (e: Exception) {
            Log.e("IptvRepository", "Error loading IPTV items", e)
            emptyList()
        }
    }

    private suspend fun loadM3uItems(config: IptvConfig): List<IptvItem> {
        val content = if (config.m3uFilePath != null) {
            val file = File(config.m3uFilePath)
            if (file.exists()) file.readText(Charsets.UTF_8) else ""
        } else if (!config.serverUrl.isNullOrEmpty()) {
            fetchUrlText(config.serverUrl)
        } else {
            ""
        }
        
        if (content.isEmpty()) return emptyList()
        return M3uParser.parse(content)
    }

    private suspend fun fetchUrlText(url: String): String {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string() ?: ""
                } else {
                    ""
                }
            }
        } catch (e: Exception) {
            Log.e("IptvRepository", "Error fetching M3U URL: $url", e)
            ""
        }
    }

    private fun loadXtreamItems(config: IptvConfig): List<IptvItem> {
        val baseUrl = config.serverUrl ?: return emptyList()
        val user = config.username ?: return emptyList()
        val pass = config.password ?: return emptyList()

        val items = mutableListOf<IptvItem>()

        // 1. Fetch Live Channels
        val liveUrl = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_live_streams"
        val liveJson = fetchJsonArray(liveUrl)
        liveJson?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val streamId = obj.optString("stream_id")
                items.add(
                    IptvItem(
                        title = obj.optString("name", "Unknown Channel"),
                        url = "$baseUrl/live/$user/$pass/$streamId.ts",
                        logoUrl = obj.optString("stream_icon").takeIf { !it.isNullOrBlank() },
                        category = obj.optString("category_name").takeIf { !it.isNullOrBlank() } ?: "Canlı TV",
                        type = "LIVE",
                        streamId = streamId
                    )
                )
            }
        }

        // 2. Fetch VOD Movies
        val vodUrl = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_vod_streams"
        val vodJson = fetchJsonArray(vodUrl)
        vodJson?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val streamId = obj.optString("stream_id")
                val ext = obj.optString("container_extension", "mp4")
                items.add(
                    IptvItem(
                        title = obj.optString("name", "Unknown Movie"),
                        url = "$baseUrl/movie/$user/$pass/$streamId.$ext",
                        logoUrl = obj.optString("stream_icon").takeIf { !it.isNullOrBlank() },
                        category = obj.optString("category_name").takeIf { !it.isNullOrBlank() } ?: "Filmlər",
                        type = "MOVIE",
                        streamId = streamId
                    )
                )
            }
        }

        // 3. Fetch Series
        val seriesUrl = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series"
        val seriesJson = fetchJsonArray(seriesUrl)
        seriesJson?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val seriesId = obj.optString("series_id")
                items.add(
                    IptvItem(
                        title = obj.optString("name", "Unknown Series"),
                        url = "", // Empty url because episodes are fetched on demand
                        logoUrl = obj.optString("cover").takeIf { !it.isNullOrBlank() },
                        category = obj.optString("category_name").takeIf { !it.isNullOrBlank() } ?: "Seriallar",
                        type = "SERIES",
                        seriesId = seriesId
                    )
                )
            }
        }

        return items
    }

    private fun fetchJsonArray(url: String): JSONArray? {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: return null
                    // Safe parsing back to JSONArray or XML/other format resilience
                    if (bodyStr.trim().startsWith("[")) {
                        JSONArray(bodyStr)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("IptvRepository", "Error fetching array path: $url", e)
            null
        }
    }

    // Load series details (seasons & episodes)
    suspend fun loadSeriesEpisodes(config: IptvConfig, seriesId: String): List<IptvItem> = withContext(Dispatchers.IO) {
        val baseUrl = config.serverUrl ?: return@withContext emptyList()
        val user = config.username ?: return@withContext emptyList()
        val pass = config.password ?: return@withContext emptyList()
        
        val url = "$baseUrl/player_api.php?username=$user&password=$pass&action=get_series_info&series_id=$seriesId"
        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                
                val episodesList = mutableListOf<IptvItem>()
                val rootObj = JSONObject(bodyStr)
                if (rootObj.has("episodes")) {
                    val episodesObj = rootObj.getJSONObject("episodes")
                    // Episodes keys are season numbers ("1", "2" etc.)
                    val keys = episodesObj.keys()
                    while (keys.hasNext()) {
                        val seasonKey = keys.next()
                        val seasonNum = seasonKey.toIntOrNull() ?: 1
                        val valArr = episodesObj.optJSONArray(seasonKey)
                        if (valArr != null) {
                            for (j in 0 until valArr.length()) {
                                val epObj = valArr.getJSONObject(j)
                                val epId = epObj.optString("id")
                                val ext = epObj.optString("container_extension", "mp4")
                                val title = epObj.optString("title", "Bölüm ${j+1}")
                                val epNum = epObj.optString("episode_num").toIntOrNull() ?: (j + 1)
                                
                                episodesList.add(
                                    IptvItem(
                                        title = title,
                                        url = "$baseUrl/series/$user/$pass/$epId.$ext",
                                        logoUrl = epObj.optString("logo").takeIf { !it.isNullOrBlank() } 
                                                  ?: rootObj.optJSONObject("info")?.optString("cover"),
                                        category = "Sezon $seasonNum",
                                        type = "EPISODE",
                                        seriesId = seriesId,
                                        seasonNumber = seasonNum,
                                        episodeNumber = epNum,
                                        streamId = epId
                                    )
                                )
                            }
                        }
                    }
                }
                
                episodesList.sortedWith(compareBy<IptvItem> { it.seasonNumber }.thenBy { it.episodeNumber })
            }
        } catch (e: Exception) {
            Log.e("IptvRepository", "Failed loading series episodes for $seriesId", e)
            emptyList()
        }
    }

    private fun cleanUrl(url: String): String {
        var clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        return clean.removeSuffix("/")
    }
}

object M3uParser {
    fun parse(content: String): List<IptvItem> {
        val items = mutableListOf<IptvItem>()
        val lines = content.lineSequence().map { it.trim() }.toList()
        
        var currentLogo: String? = null
        var currentName = ""
        var currentGroup = ""
        
        for (line in lines) {
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF:")) {
                currentLogo = getAttribute(line, "tvg-logo")
                currentGroup = getAttribute(line, "group-title") ?: "Digər"
                
                val commaIndex = line.lastIndexOf(',')
                currentName = if (commaIndex != -1 && commaIndex < line.length - 1) {
                    line.substring(commaIndex + 1).trim()
                } else {
                    "Untitled Channel"
                }
            } else if (!line.startsWith("#")) {
                if (line.startsWith("http://") || line.startsWith("https://") || line.contains("://")) {
                    val type = determineType(currentGroup, currentName)
                    
                    var season: Int? = null
                    var episode: Int? = null
                    val match = Regex("(?i)S(\\d+)\\s?E(\\d+)").find(currentName)
                    if (match != null) {
                        season = match.groupValues[1].toIntOrNull()
                        episode = match.groupValues[2].toIntOrNull()
                    }

                    items.add(
                        IptvItem(
                            title = currentName,
                            url = line,
                            logoUrl = currentLogo,
                            category = currentGroup,
                            type = type,
                            seasonNumber = season,
                            episodeNumber = episode
                        )
                    )
                }
                currentLogo = null
                currentName = ""
                currentGroup = ""
            }
        }
        return items
    }

    private fun getAttribute(line: String, key: String): String? {
        val search = "$key=\""
        val start = line.indexOf(search)
        if (start == -1) return null
        val beginIndex = start + search.length
        val end = line.indexOf('"', beginIndex)
        if (end == -1) return null
        return line.substring(beginIndex, end)
    }

    private fun determineType(group: String, name: String): String {
        val nameLower = name.lowercase()
        val groupLower = group.lowercase()
        
        if (groupLower.contains("series") || groupLower.contains("serial") || groupLower.contains("dizi") || groupLower.contains("sezon") || 
            nameLower.contains("s01") || nameLower.contains("s02") || nameLower.contains("s03") || nameLower.contains("s04") || 
            nameLower.contains("e01") || nameLower.contains("e02")) {
            return "SERIES"
        }
        if (groupLower.contains("movies") || groupLower.contains("vod") || groupLower.contains("film") || groupLower.contains("cinema") || 
            groupLower.contains("sinema") || groupLower.contains("kino")) {
            return "MOVIE"
        }
        return "LIVE"
    }
}
