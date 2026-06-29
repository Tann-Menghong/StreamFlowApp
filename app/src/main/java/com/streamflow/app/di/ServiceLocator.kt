package com.streamflow.app.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.streamflow.app.data.db.AppDatabase
import com.streamflow.app.data.repository.YoutubeRepository
import com.streamflow.app.extractor.OkHttpDownloader
import com.streamflow.app.player.PlayerController
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.TimeUnit

object ServiceLocator {

    @Volatile
    private var initialized = false

    lateinit var repository: YoutubeRepository
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var playerController: PlayerController
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

            try {
                NewPipe.init(OkHttpDownloader(okHttpClient))
            } catch (e: Exception) {
                Log.e("ServiceLocator", "NewPipe init failed — video loading may not work", e)
            }

            database = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "streamflow.db"
            ).fallbackToDestructiveMigration().build()

            repository = YoutubeRepository()
            playerController = PlayerController(context.applicationContext)

            initialized = true
        }
    }
}
