package com.streamflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.streamflow.data.local.dao.BlockedDao
import com.streamflow.data.local.dao.DownloadDao
import com.streamflow.data.local.dao.FavoriteDao
import com.streamflow.data.local.dao.HistoryDao
import com.streamflow.data.local.dao.PlaylistDao
import com.streamflow.data.local.dao.SubscriptionDao
import com.streamflow.data.local.dao.WatchLaterDao
import com.streamflow.data.local.entity.BlockedItemEntity
import com.streamflow.data.local.entity.DownloadEntity
import com.streamflow.data.local.entity.FavoriteEntity
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.local.entity.PlaylistEntity
import com.streamflow.data.local.entity.PlaylistItemEntity
import com.streamflow.data.local.entity.SubscriptionEntity
import com.streamflow.data.local.entity.WatchLaterEntity

@Database(
    entities = [FavoriteEntity::class, HistoryEntity::class, WatchLaterEntity::class, SubscriptionEntity::class, BlockedItemEntity::class, DownloadEntity::class, PlaylistEntity::class, PlaylistItemEntity::class, com.streamflow.data.local.entity.BookmarkEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): com.streamflow.data.local.dao.BookmarkDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun blockedDao(): BlockedDao
    abstract fun downloadDao(): DownloadDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Adds the subscriptions table without wiping favorites/history/watch-later
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subscriptions` (" +
                    "`channelUrl` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`avatarUrl` TEXT NOT NULL, " +
                    "`subscribedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`channelUrl`))"
                )
            }
        }

        // Adds the blocked_items ("not interested") table, preserving all user data
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `blocked_items` (" +
                    "`itemKey` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`blockedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`itemKey`))"
                )
            }
        }

        // Downloads + local playlists tables, and lastVideoUrl for upload notifications
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (" +
                    "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, " +
                    "`uploaderName` TEXT NOT NULL, `filePath` TEXT NOT NULL, `isAudio` INTEGER NOT NULL, " +
                    "`downloadId` INTEGER NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`url`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_items` (" +
                    "`playlistId` INTEGER NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`thumbnailUrl` TEXT NOT NULL, `uploaderName` TEXT NOT NULL, " +
                    "`duration` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`playlistId`, `url`))"
                )
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `lastVideoUrl` TEXT NOT NULL DEFAULT ''")
            }
        }

        // Channel groups (folders) for subscriptions
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `groupName` TEXT NOT NULL DEFAULT ''")
            }
        }

        // Per-channel notification toggle
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `notify` INTEGER NOT NULL DEFAULT 1")
            }
        }

        // Timestamp bookmarks table
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmarks` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`videoUrl` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`thumbnailUrl` TEXT NOT NULL, `uploaderName` TEXT NOT NULL, " +
                    "`positionMs` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "streamflow.db")
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    // Destructive fallback ONLY from the ancient v1 schema (no
                    // migration exists for it). The blanket fallback was a data
                    // landmine: any future version bump missing a migration would
                    // silently wipe favorites/history/playlists/subscriptions.
                    // Now that case crashes in development instead of destroying
                    // user data in production.
                    .fallbackToDestructiveMigrationFrom(1)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
