package com.streamflow.app.di

import android.content.Context
import androidx.room.Room
import com.streamflow.app.data.db.AppDatabase
import com.streamflow.app.data.repository.YoutubeRepository
import com.streamflow.app.extractor.OkHttpDownloader
import com.streamflow.app.player.PlayerController
import com.streamflow.app.update.UpdateManager
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.TimeUnit

/**
 * Minimal manual DI container. The app is small enough that a framework like Hilt would add
 * more boilerplate than it removes.
 */
object ServiceLocator {

    @Volatile
    private var initialized = false

    lateinit var repository: YoutubeRepository
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var playerController: PlayerController
        private set

    lateinit var updateManager: UpdateManager
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            NewPipe.init(OkHttpDownloader(okHttpClient))

            database = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "streamflow.db"
            ).build()

            repository = YoutubeRepository()
            playerController = PlayerController(context.applicationContext)
            updateManager = UpdateManager(okHttpClient)

            initialized = true
        }
    }
}
