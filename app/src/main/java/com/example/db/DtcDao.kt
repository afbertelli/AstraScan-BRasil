package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DtcDao {
    @Query("SELECT * FROM dtc_dictionary WHERE code = :code LIMIT 1")
    suspend fun getDtc(code: String): DtcEntity?

    @Query("SELECT * FROM dtc_dictionary")
    fun getAllDtcs(): Flow<List<DtcEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dtcs: List<DtcEntity>)

    @Query("SELECT COUNT(*) FROM dtc_dictionary")
    suspend fun count(): Int
}
