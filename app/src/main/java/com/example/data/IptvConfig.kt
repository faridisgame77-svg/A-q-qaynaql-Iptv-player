package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "iptv_configs")
data class IptvConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // "XTREAM" or "M3U"
    val serverUrl: String? = null,
    val username: String? = null,
    val password: String? = null,
    val m3uFilePath: String? = null,
    val isActive: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class IptvItem(
    val title: String,
    val url: String,
    val logoUrl: String? = null,
    val category: String = "Other",
    val type: String = "LIVE", // "LIVE", "MOVIE", "SERIES"
    val seriesId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val streamId: String? = null
)
