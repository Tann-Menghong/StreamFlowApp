package com.streamflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.streamflow.data.local.dao.FavoriteDao
import com.streamflow.data.local.dao.HistoryDao
import com.streamflow.data.local.dao.SubscriptionDao
import com.streamflow.data.local.dao.WatchLaterDao
import com.streamflow.data.local.entity.FavoriteEntity
import com.streamflow.data.local.entity.HistoryEntity
import com.streamflow.data.local.entity.SubscriptionEntity
import com.streamflow.data.local.entity.WatchLaterEntity

@Database(
    entities = [FavoriteEntity::class, HistoryEntity::class, WatchLaterEntity::class, SubscriptionEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun subscriptionDao(): SubscriptionDao

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

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "streamflow.db")
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
