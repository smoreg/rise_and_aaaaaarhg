package dev.smoreg.raa

import android.app.Application
import android.content.Context
import androidx.room.Room
import dev.smoreg.raa.alarm.Notifications
import dev.smoreg.raa.alarm.Scheduler
import dev.smoreg.raa.data.MIGRATION_1_2
import dev.smoreg.raa.data.RaaDatabase
import dev.smoreg.raa.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class RaaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }

    companion object {
        lateinit var container: AppContainer
            private set
    }
}

/**
 * Everything lives in device-protected storage so an alarm can ring (and resume after a reboot)
 * before the user unlocks the phone for the first time.
 */
class AppContainer(app: Application) {
    val context: Context = app
    private val storage: Context = app.createDeviceProtectedStorageContext()

    val scope = CoroutineScope(SupervisorJob())
    val db = Room.databaseBuilder(storage, RaaDatabase::class.java, "raa.db").addMigrations(MIGRATION_1_2).build()
    val settings = Settings(storage)
    val scheduler = Scheduler(app, db.alarms(), settings)
}
