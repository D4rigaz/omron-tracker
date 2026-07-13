package com.darigaz.omrontracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Insert
    suspend fun insert(measurement: Measurement): Long

    @Update
    suspend fun update(measurement: Measurement)

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Measurement>>

    @Query("SELECT * FROM measurements WHERE syncedToHealthConnect = 0 ORDER BY timestamp ASC")
    suspend fun pendingSync(): List<Measurement>

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): Measurement?
}
