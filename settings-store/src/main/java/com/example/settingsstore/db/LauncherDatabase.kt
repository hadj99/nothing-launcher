package com.example.settingsstore.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_index")
data class AppIndexEntity(
    @PrimaryKey val packageName: String,
    val activityName: String,
    val label: String,
    val category: String,
    val launchCount: Int = 0,
    val lastUsedTimestamp: Long = 0L,
    val dailyMinutesUsed: Int = 0,
    val isSystemApp: Boolean = false
)

@Entity(tableName = "theme_presets")
data class ThemePresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val themeJson: String,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "step_history")
data class StepHistoryEntity(
    @PrimaryKey val dateIso: String,
    val steps: Int,
    val goal: Int
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val queryText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "clipboard_history")
data class ClipboardEntryEntity(
    @PrimaryKey val contentHash: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface AppIndexDao {
    @Query("SELECT * FROM app_index ORDER BY label COLLATE NOCASE ASC")
    fun observeAllApps(): Flow<List<AppIndexEntity>>

    @Query("SELECT * FROM app_index")
    suspend fun getAllAppsOnce(): List<AppIndexEntity>

    @Query("SELECT * FROM app_index ORDER BY launchCount DESC, lastUsedTimestamp DESC LIMIT :limit")
    fun observeMostUsed(limit: Int = 8): Flow<List<AppIndexEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApps(apps: List<AppIndexEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewApps(apps: List<AppIndexEntity>)

    @Query(
        "UPDATE app_index SET activityName = :activityName, label = :label, category = :category, isSystemApp = :isSystemApp WHERE packageName = :packageName"
    )
    suspend fun updateAppMetadata(
        packageName: String,
        activityName: String,
        label: String,
        category: String,
        isSystemApp: Boolean
    )

    @Query("DELETE FROM app_index WHERE packageName NOT IN (:activePackages)")
    suspend fun pruneRemoved(activePackages: List<String>)

    @Query("UPDATE app_index SET launchCount = launchCount + 1, lastUsedTimestamp = :timestamp WHERE packageName = :packageName")
    suspend fun recordLaunch(packageName: String, timestamp: Long)

    @Query("UPDATE app_index SET lastUsedTimestamp = :timestamp WHERE packageName = :packageName AND lastUsedTimestamp < :timestamp")
    suspend fun updateLastUsedIfNewer(packageName: String, timestamp: Long)

    @Query("UPDATE app_index SET dailyMinutesUsed = :minutes WHERE packageName = :packageName")
    suspend fun updateDailyMinutes(packageName: String, minutes: Int)

    @Transaction
    suspend fun syncInstalledApps(scanned: List<AppIndexEntity>) {
        val existingByPkg = getAllAppsOnce().associateBy { it.packageName }
        val toInsert = mutableListOf<AppIndexEntity>()
        for (app in scanned) {
            val prev = existingByPkg[app.packageName]
            if (prev == null) {
                toInsert.add(app)
            } else if (
                prev.activityName != app.activityName ||
                prev.label != app.label ||
                prev.category != app.category ||
                prev.isSystemApp != app.isSystemApp
            ) {
                updateAppMetadata(
                    packageName = app.packageName,
                    activityName = app.activityName,
                    label = app.label,
                    category = app.category,
                    isSystemApp = app.isSystemApp
                )
            }
        }
        if (toInsert.isNotEmpty()) {
            insertNewApps(toInsert)
        }
        val activePkgs = scanned.map { it.packageName }
        val hasRemoved = existingByPkg.keys.any { it !in activePkgs.toSet() }
        if (hasRemoved && activePkgs.isNotEmpty()) {
            pruneRemoved(activePkgs)
        }
    }

    @Transaction
    suspend fun applyUsageUpdates(minutesByPkg: Map<String, Int>, lastUsedByPkg: Map<String, Long>) {
        val existingByPkg = getAllAppsOnce().associateBy { it.packageName }
        for ((pkg, mins) in minutesByPkg) {
            val prev = existingByPkg[pkg] ?: continue
            if (prev.dailyMinutesUsed != mins) {
                updateDailyMinutes(pkg, mins)
            }
        }
        for ((pkg, ts) in lastUsedByPkg) {
            val prev = existingByPkg[pkg] ?: continue
            if (ts > prev.lastUsedTimestamp) {
                updateLastUsedIfNewer(pkg, ts)
            }
        }
    }
}

@Dao
interface ThemePresetDao {
    @Query("SELECT * FROM theme_presets ORDER BY isBuiltIn DESC, createdAt ASC")
    fun observePresets(): Flow<List<ThemePresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreset(preset: ThemePresetEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBuiltIns(presets: List<ThemePresetEntity>)

    @Query("DELETE FROM theme_presets WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomPreset(id: String)
}

@Dao
interface StepHistoryDao {
    @Query("SELECT * FROM step_history ORDER BY dateIso DESC LIMIT 7")
    fun observeLast7Days(): Flow<List<StepHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(entry: StepHistoryEntity)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 12")
    fun observeHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordQuery(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE queryText = :query")
    suspend fun deleteQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()
}

@Dao
interface ClipboardHistoryDao {
    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC LIMIT 20")
    fun observeSnippets(): Flow<List<ClipboardEntryEntity>>

    @Query("SELECT * FROM clipboard_history WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 5")
    suspend fun searchSnippets(query: String): List<ClipboardEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnippet(entry: ClipboardEntryEntity)

    @Query("DELETE FROM clipboard_history")
    suspend fun clearSnippets()
}

@Database(
    entities = [
        AppIndexEntity::class,
        ThemePresetEntity::class,
        StepHistoryEntity::class,
        SearchHistoryEntity::class,
        ClipboardEntryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun appIndexDao(): AppIndexDao
    abstract fun themePresetDao(): ThemePresetDao
    abstract fun stepHistoryDao(): StepHistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun clipboardHistoryDao(): ClipboardHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: LauncherDatabase? = null

        fun getInstance(context: Context): LauncherDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LauncherDatabase::class.java,
                    "dotos_launcher.db"
                ).fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
