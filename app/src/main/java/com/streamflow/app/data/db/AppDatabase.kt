package com.streamflow.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntity::class,
        BookmarkEntity::class,
        SubscriptionEntity::class,
        SearchHistoryEntity::class,
        WatchLaterEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun watchLaterDao(): WatchLaterDao
}
