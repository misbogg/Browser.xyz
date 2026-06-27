package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- ENTITIES ---

@Entity(tableName = "tabs")
data class Tab(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val isPrivate: Boolean = false,
    val isPinned: Boolean = false,
    val lastActive: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val adBlockCount: Int = 0,
    val trackerBlockCount: Int = 0,
    val scriptBlockCount: Int = 0,
    val cookieBlockCount: Int = 0,
    val fingerprintBlockCount: Int = 0,
    val redirectCount: Int = 0
)

@Entity(tableName = "custom_scripts")
data class CustomScript(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val targetPattern: String, // e.g., "*example.com*" or "*"
    val jsCode: String,
    val isEnabled: Boolean = true
)

@Entity(tableName = "site_rules")
data class SiteRule(
    @PrimaryKey val domain: String, // Host of the website, e.g., "google.com"
    val adBlockEnabled: Boolean = true,
    val cookieBlockEnabled: Boolean = true,
    val jsEnabled: Boolean = true,
    val webRtcBlockEnabled: Boolean = true,
    val fingerprintShieldEnabled: Boolean = true,
    val customCss: String = "",
    val customUserAgent: String = ""
)

// --- DAOS ---

@Dao
interface BrowserDao {
    // Tabs operations
    @Query("SELECT * FROM tabs ORDER BY isPinned DESC, lastActive DESC")
    fun getAllTabsFlow(): Flow<List<Tab>>

    @Query("SELECT * FROM tabs ORDER BY isPinned DESC, lastActive DESC")
    suspend fun getAllTabs(): List<Tab>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: Tab): Long

    @Update
    suspend fun updateTab(tab: Tab)

    @Delete
    suspend fun deleteTab(tab: Tab)

    @Query("DELETE FROM tabs")
    suspend fun deleteAllTabs()

    // Bookmarks operations
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarksFlow(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun isBookmarkedFlow(url: String): Flow<Boolean>

    // History operations
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(historyItem: HistoryItem): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteHistoryById(id: Int)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // Custom Scripts operations
    @Query("SELECT * FROM custom_scripts")
    fun getAllScriptsFlow(): Flow<List<CustomScript>>

    @Query("SELECT * FROM custom_scripts WHERE isEnabled = 1")
    suspend fun getActiveScripts(): List<CustomScript>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: CustomScript): Long

    @Query("DELETE FROM custom_scripts WHERE id = :id")
    suspend fun deleteScriptById(id: Int)

    // Site Rules operations
    @Query("SELECT * FROM site_rules WHERE domain = :domain")
    suspend fun getRuleForDomain(domain: String): SiteRule?

    @Query("SELECT * FROM site_rules")
    fun getAllRulesFlow(): Flow<List<SiteRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: SiteRule)

    @Query("DELETE FROM site_rules WHERE domain = :domain")
    suspend fun deleteRuleForDomain(domain: String)
}

// --- DATABASE ---

@Database(
    entities = [Tab::class, Bookmark::class, HistoryItem::class, CustomScript::class, SiteRule::class],
    version = 1,
    exportSchema = false
)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile
        private var INSTANCE: BrowserDatabase? = null

        fun getDatabase(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "browser_xyz_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
