package dev.ahmedvhashem.databaseliveinspector.sample.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.ahmedvhashem.databaseliveinspector.sample.InspectorIntegration

@Database(entities = [Author::class, Book::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun authorDao(): AuthorDao
    abstract fun bookDao(): BookDao

    companion object {
        private const val DB_NAME = "library.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .let { InspectorIntegration.attachToBuilder(it, DB_NAME) }
                .build()
    }
}
