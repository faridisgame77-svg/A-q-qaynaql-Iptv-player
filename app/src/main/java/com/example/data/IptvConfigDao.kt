package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvConfigDao {
    @Query("SELECT * FROM iptv_configs ORDER BY timestamp DESC")
    fun getAllConfigs(): Flow<List<IptvConfig>>

    @Query("SELECT * FROM iptv_configs WHERE isActive = 1 LIMIT 1")
    fun getActiveConfigFlow(): Flow<IptvConfig?>

    @Query("SELECT * FROM iptv_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveConfig(): IptvConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: IptvConfig): Long

    @Update
    suspend fun updateConfig(config: IptvConfig)

    @Delete
    suspend fun deleteConfig(config: IptvConfig)

    @Query("UPDATE iptv_configs SET isActive = 0")
    suspend fun deactivateAllConfigs()

    @Query("UPDATE iptv_configs SET isActive = 1 WHERE id = :id")
    suspend fun activateConfig(id: Int)
}
