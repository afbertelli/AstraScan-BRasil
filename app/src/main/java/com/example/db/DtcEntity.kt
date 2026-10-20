package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dtc_dictionary")
data class DtcEntity(
    @PrimaryKey
    val code: String,
    val title: String,
    val system: String,
    val description: String,
    val symptoms: String,
    val probableCauses: String,
    val recommendedFix: String,
    val applicableEngines: String
)
