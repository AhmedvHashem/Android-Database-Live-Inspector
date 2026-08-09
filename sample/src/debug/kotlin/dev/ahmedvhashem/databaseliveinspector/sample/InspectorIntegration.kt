package dev.ahmedvhashem.databaseliveinspector.sample

import android.content.Context
import androidx.room.RoomDatabase
import dev.ahmedvhashem.databaseliveinspector.agent.DatabaseLiveInspector

object InspectorIntegration {

    fun init(context: Context) {
        DatabaseLiveInspector.install(context)
    }

    fun <T : RoomDatabase> attachToBuilder(
        builder: RoomDatabase.Builder<T>,
        dbName: String,
    ): RoomDatabase.Builder<T> = DatabaseLiveInspector.attachTo(builder, dbName)
}
