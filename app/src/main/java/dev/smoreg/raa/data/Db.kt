package dev.smoreg.raa.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms")
    suspend fun all(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): Alarm?

    @Upsert
    suspend fun upsert(alarm: Alarm): Long

    @Delete
    suspend fun delete(alarm: Alarm)

    @Query("SELECT COUNT(*) FROM alarms WHERE enabled AND dismissMode = 'QR'")
    suspend fun enabledQrAlarms(): Int
}

@Dao
interface QrDao {
    @Query("SELECT * FROM qr_codes ORDER BY createdAt")
    fun observeAll(): Flow<List<QrCode>>

    @Query("SELECT * FROM qr_codes WHERE payload = :payload LIMIT 1")
    suspend fun find(payload: String): QrCode?

    @Insert
    suspend fun insert(code: QrCode): Long

    @Delete
    suspend fun delete(code: QrCode)
}

@Database(entities = [Alarm::class, QrCode::class], version = 2)
abstract class RaaDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun qrCodes(): QrDao
}

/** Five dismiss modes became three (QR and spinning are always quiet now); spin seconds became a shake level. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE alarms_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, hour INTEGER NOT NULL,
            minute INTEGER NOT NULL, days INTEGER NOT NULL, label TEXT NOT NULL, enabled INTEGER NOT NULL,
            dismissMode TEXT NOT NULL, sound TEXT NOT NULL, volume INTEGER NOT NULL, rampSeconds INTEGER NOT NULL,
            vibrate INTEGER NOT NULL, light TEXT NOT NULL, sunriseMinutes INTEGER NOT NULL,
            snoozeMinutes INTEGER NOT NULL, snoozeMax INTEGER NOT NULL, shakeLevel TEXT NOT NULL,
            quietMinutes INTEGER NOT NULL, handledUntil INTEGER NOT NULL, snoozeUntil INTEGER NOT NULL,
            snoozeCount INTEGER NOT NULL)""",
        )
        db.execSQL(
            """INSERT INTO alarms_new SELECT id, hour, minute, days, label, enabled,
            CASE WHEN dismissMode LIKE 'QR%' THEN 'QR' WHEN dismissMode LIKE 'ROTATE%' THEN 'SHAKE' ELSE 'BUTTON' END,
            sound, volume, rampSeconds, vibrate, light, sunriseMinutes, snoozeMinutes, snoozeMax, 'AAAGH',
            quietMinutes, handledUntil, snoozeUntil, snoozeCount FROM alarms""",
        )
        db.execSQL("DROP TABLE alarms")
        db.execSQL("ALTER TABLE alarms_new RENAME TO alarms")
    }
}
