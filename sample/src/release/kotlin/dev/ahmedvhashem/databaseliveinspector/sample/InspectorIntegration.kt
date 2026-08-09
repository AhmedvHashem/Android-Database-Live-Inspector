package dev.ahmedvhashem.databaseliveinspector.sample

import android.content.Context
import androidx.room.RoomDatabase

object InspectorIntegration {

    fun init(context: Context) = Unit

    fun <T : RoomDatabase> attachToBuilder(
        builder: RoomDatabase.Builder<T>,
        dbName: String,
    ): RoomDatabase.Builder<T> = builder
}
