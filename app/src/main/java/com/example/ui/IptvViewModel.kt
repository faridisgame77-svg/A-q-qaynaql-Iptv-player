package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.IptvConfig
import com.example.data.IptvItem
import com.example.data.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = IptvRepository(application, db.iptvConfigDao())

    // UI screen: "LOGIN" | "LOADING" | "DASHBOARD" | "SERIES_DETAILS" | "PLAYER"
    private val _currentScreen = MutableStateFlow("LOGIN")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Dashboard navigation tab: "LIVE" | "MOVIE" | "SERIES"
    private val _currentDashboardTab = MutableStateFlow("LIVE")
    val currentDashboardTab: StateFlow<String> = _currentDashboardTab.asStateFlow()

    // Loading indicators
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingStatusText = MutableStateFlow("")
    val loadingStatusText: StateFlow<String> = _loadingStatusText.asStateFlow()

    // Saved login account configs from SQL
    val savedConfigs: StateFlow<List<IptvConfig>> = repository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected account context
    private val _activeConfig = MutableStateFlow<IptvConfig?>(null)
    val activeConfig: StateFlow<IptvConfig?> = _activeConfig.asStateFlow()

    // Complete parsed channels feeds
    private val _allItems = MutableStateFlow<List<IptvItem>>(emptyList())
    val allItems: StateFlow<List<IptvItem>> = _allItems.asStateFlow()

    // Filtering categories & queries
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Selected items for play and detail view
    private val _selectedSeries = MutableStateFlow<IptvItem?>(null)
    val selectedSeries: StateFlow<IptvItem?> = _selectedSeries.asStateFlow()

    private val _seriesEpisodes = MutableStateFlow<List<IptvItem>>(emptyList())
    val seriesEpisodes: StateFlow<List<IptvItem>> = _seriesEpisodes.asStateFlow()

    // Video streams
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeStreamUrl = MutableStateFlow("")
    val activeStreamUrl: StateFlow<String> = _activeStreamUrl.asStateFlow()

    private val _activeStreamTitle = MutableStateFlow("")
    val activeStreamTitle: StateFlow<String> = _activeStreamTitle.asStateFlow()

    // Filter categories computed dynamically
    val categories: StateFlow<List<String>> = combine(
        _allItems, _currentDashboardTab
    ) { items, tab ->
        val filtered = items.filter { it.type == tab }
        filtered.map { it.category }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // List of channels shown in the current visual context
    val filteredItems: StateFlow<List<IptvItem>> = combine(
        _allItems, _currentDashboardTab, _selectedCategory, _searchQuery
    ) { items, tab, cat, query ->
        var list = items.filter { it.type == tab }
        if (cat != null) {
            list = list.filter { it.category == cat }
        }
        if (query.isNotEmpty()) {
            list = list.filter { it.title.contains(query, ignoreCase = true) }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Display IP address and port
    var deploymentServerUrl = ""

    init {
        // Find Local IP Address for screen pairing indicators
        val ip = getLocalIpAddress()
        deploymentServerUrl = "http://$ip:8080"

        // Watch active setup config in database
        viewModelScope.launch {
            repository.activeConfigFlow.collect { active ->
                if (active != null) {
                    _activeConfig.value = active
                    loadPlaylistsForConfig(active)
                } else {
                    _activeConfig.value = null
                    _allItems.value = emptyList()
                    _currentScreen.value = "LOGIN"
                }
            }
        }

        // Catch incoming login payloads sent from remote mobile client to our HTTP web socket
        viewModelScope.launch {
            repository.onCredentialsLoaded.collect { config ->
                if (config != null) {
                    Log.d("IptvViewModel", "Processing received deployment from mobile: ${config.name}")
                    saveAndLoadConfig(config)
                    repository.clearReceivedCredentialEvent()
                }
            }
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "127.0.0.1"
    }

    private fun saveAndLoadConfig(config: IptvConfig) {
        viewModelScope.launch {
            _isLoading.value = true
            _loadingStatusText.value = "Yüklənir..."
            _currentScreen.value = "LOADING"
            
            // Insert into local SQL database
            val id = repository.insertConfig(config)
            val fullConfig = config.copy(id = id)
            _activeConfig.value = fullConfig
            
            // Save as active and run
            repository.selectConfig(id)
        }
    }

    fun selectSavedConfig(config: IptvConfig) {
        viewModelScope.launch {
            _isLoading.value = true
            _loadingStatusText.value = "${config.name} Hesabına giriş edilir..."
            _currentScreen.value = "LOADING"
            repository.selectConfig(config.id)
        }
    }

    fun deleteSavedConfig(config: IptvConfig) {
        viewModelScope.launch {
            repository.deleteConfig(config)
        }
    }

    fun logOutCurrentAccount() {
        viewModelScope.launch {
            _isLoading.value = true
            _loadingStatusText.value = "Çıxış edilir..."
            _currentScreen.value = "LOADING"
            
            repository.deactivateActive()
            _activeConfig.value = null
            _allItems.value = emptyList()
            _selectedCategory.value = null
            _currentScreen.value = "LOGIN"
            
            _isLoading.value = false
        }
    }

    fun loadPlaylistsForConfig(config: IptvConfig) {
        viewModelScope.launch(Dispatchers.Main) {
            _isLoading.value = true
            _currentScreen.value = "LOADING"
            _loadingStatusText.value = "Kanallar yüklənir, zəhmət olmasa gözləyin..."
            
            val itemsList = withContext(Dispatchers.IO) {
                repository.loadIptvItems(config)
            }
            
            if (itemsList.isNotEmpty()) {
                _allItems.value = itemsList
                // Preselect first category if exists
                val firstCat = itemsList.filter { it.type == _currentDashboardTab.value }
                    .map { it.category }.distinct().sorted().firstOrNull()
                _selectedCategory.value = firstCat
                _currentScreen.value = "DASHBOARD"
            } else {
                _allItems.value = emptyList()
                _currentScreen.value = "LOGIN"
                // Inform user on loading error or fall back to credentials manual
            }
            _isLoading.value = false
        }
    }

    fun changeDashboardTab(tab: String) {
        _currentDashboardTab.value = tab
        // Update category selection to match first category in new tab
        val cats = _allItems.value.filter { it.type == tab }.map { it.category }.distinct().sorted()
        _selectedCategory.value = cats.firstOrNull()
    }

    fun selectCategory(cat: String?) {
        _selectedCategory.value = cat
    }

    fun searchItems(query: String) {
        _searchQuery.value = query
    }

    fun selectSeries(series: IptvItem) {
        viewModelScope.launch {
            _isLoading.value = true
            _selectedSeries.value = series
            _currentScreen.value = "SERIES_DETAILS"
            
            val active = _activeConfig.value
            if (active != null && series.seriesId != null) {
                val eps = repository.loadSeriesEpisodes(active, series.seriesId)
                _seriesEpisodes.value = eps
            }
            _isLoading.value = false
        }
    }

    fun goBackToDashboard() {
        _selectedSeries.value = null
        _seriesEpisodes.value = emptyList()
        _currentScreen.value = "DASHBOARD"
    }

    fun launchPlayer(streamUrl: String, title: String) {
        _activeStreamUrl.value = streamUrl
        _activeStreamTitle.value = title
        _currentScreen.value = "PLAYER"
    }

    fun stopPlayer() {
        _activeStreamUrl.value = ""
        _activeStreamTitle.value = ""
        if (_selectedSeries.value != null) {
            _currentScreen.value = "SERIES_DETAILS"
        } else {
            _currentScreen.value = "DASHBOARD"
        }
    }

    fun addManualCredentials(type: String, url: String, user: String = "", pass: String = "") {
        val cleanedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "http://$url" else url
        val config = if (type == "XTREAM") {
            IptvConfig(
                name = "Xtream: " + cleanedUrl.substringAfter("://").substringBefore(":").substringBefore("/"),
                type = "XTREAM",
                serverUrl = cleanedUrl,
                username = user,
                password = pass,
                isActive = true
            )
        } else {
            IptvConfig(
                name = "Manual Link",
                type = "M3U",
                serverUrl = cleanedUrl,
                isActive = true
            )
        }
        saveAndLoadConfig(config)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopLocalServer()
    }
}
