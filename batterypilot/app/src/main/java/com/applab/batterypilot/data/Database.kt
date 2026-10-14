package com.applab.batterypilot.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One battery reading. Taken every ~15 min, on plug/unplug, and whenever the app is opened. */
@Entity(tableName = "samples", indices = [Index("time")])
data class BatterySample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    /** Battery percent 0–100. */
    val level: Int,
    val charging: Boolean,
    /** 0 = on battery, else BatteryManager.BATTERY_PLUGGED_*. */
    val plugged: Int,
    val tempC: Float,
    val voltageMv: Int,
    /** Instant current in µA (negative = discharging on most phones), null if unknown. */
    val currentUa: Int?,
    /** Remaining charge in µAh, null if unknown. */
    val chargeCounterUah: Int?,
    val screenOn: Boolean,
    val powerSave: Boolean,
    /** "worker", "plug", "unplug", "open", "boot". */
    val source: String,
)

/** A change the user made from the Fixes list. Used for the before/after comparison. */
@Entity(tableName = "fix_log", indices = [Index("time")])
data class FixLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val fixId: String,
    val title: String,
    /** "applied", "opened" (guided: settings screen opened), "undone". */
    val action: String,
)

@Dao
interface SampleDao {
    @Insert
    suspend fun insert(s: BatterySample): Long

    @Query("SELECT * FROM samples WHERE time >= :from ORDER BY time")
    suspend fun since(from: Long): List<BatterySample>

    @Query("SELECT * FROM samples WHERE time >= :from ORDER BY time")
    fun observeSince(from: Long): Flow<List<BatterySample>>

    @Query("SELECT * FROM samples ORDER BY time DESC LIMIT 1")
    suspend fun latest(): BatterySample?

    @Query("SELECT COUNT(*) FROM samples")
    suspend fun count(): Int

    @Query("SELECT MIN(time) FROM samples")
    suspend fun firstTime(): Long?

    @Query("DELETE FROM samples WHERE time < :before")
    suspend fun deleteBefore(before: Long)
}

@Dao
interface FixLogDao {
    @Insert
    suspend fun insert(f: FixLog): Long

    @Query("SELECT * FROM fix_log ORDER BY time DESC")
    fun observeAll(): Flow<List<FixLog>>

    @Query("SELECT * FROM fix_log ORDER BY time DESC")
    suspend fun all(): List<FixLog>
}

@Database(entities = [BatterySample::class, FixLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun samples(): SampleDao
    abstract fun fixes(): FixLogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "batterypilot.db")
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
