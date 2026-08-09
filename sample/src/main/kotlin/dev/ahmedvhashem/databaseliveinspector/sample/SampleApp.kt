package dev.ahmedvhashem.databaseliveinspector.sample

import android.app.Application
import dev.ahmedvhashem.databaseliveinspector.sample.data.AppDatabase

class SampleApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }

    override fun onCreate() {
        super.onCreate()
        InspectorIntegration.init(this)
    }
}
