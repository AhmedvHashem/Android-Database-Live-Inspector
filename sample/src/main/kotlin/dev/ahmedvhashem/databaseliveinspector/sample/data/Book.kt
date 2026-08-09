package dev.ahmedvhashem.databaseliveinspector.sample.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Author::class,
            parentColumns = ["id"],
            childColumns = ["authorId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("authorId")],
)
data class Book(
    @PrimaryKey val id: Int,
    val title: String,
    val year: Int,
    val authorId: Int,
)
