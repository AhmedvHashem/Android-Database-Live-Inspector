package dev.ahmedvhashem.databaseliveinspector.sample.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Author(
    @PrimaryKey val id: Int,
    val name: String,
    val country: String,
)
