package com.liferpg

import android.annotation.SuppressLint
import android.content.Context
import androidx.room.Room
import com.liferpg.data.AppDatabase
import com.liferpg.data.AppRepository
import com.liferpg.data.CatalogoRepo
import com.liferpg.data.HealthConnectRepo
import com.liferpg.data.Migrations
import com.liferpg.data.net.NutricaoRepo
import com.liferpg.data.sync.LocalSyncService
import com.liferpg.data.sync.SupabaseAuth
import com.liferpg.data.sync.SupabaseConfig
import com.liferpg.data.sync.SupabaseSyncService
import com.liferpg.data.sync.SyncService

/**
 * Hand-rolled service locator. Built once from [LifeRpgApp.onCreate] and read from every
 * ViewModel, Composable and BroadcastReceiver that needs a singleton.
 */
object Graph {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: AppRepository
        private set
    lateinit var cardioRoutes: com.liferpg.data.CardioRouteStore
        private set
    lateinit var circleOutbox: com.liferpg.data.CircleOutbox
        private set

    lateinit var nutricao: NutricaoRepo
        private set

    lateinit var sync: SyncService
        private set

    lateinit var auth: SupabaseAuth
        private set

    lateinit var appContext: Context
        private set

    @SuppressLint("StaticFieldLeak") // HealthConnectRepo stores applicationContext only.
    lateinit var health: HealthConnectRepo
        private set

    /** Idempotent: a second call (process restart with a live singleton) is a no-op. */
    @Synchronized
    fun provide(context: Context) {
        if (::database.isInitialized) return

        val app = context.applicationContext
        appContext = app
        health = HealthConnectRepo(app)

        database = Room.databaseBuilder(app, AppDatabase::class.java, "ironquest.db")
            .addMigrations(*Migrations.TODAS)
            .build()

        cardioRoutes = com.liferpg.data.CardioRouteStore(app)
        repository = AppRepository(database, CatalogoRepo(app), cardioRoutes)
        circleOutbox = com.liferpg.data.CircleOutbox(database)
        nutricao = NutricaoRepo(app)
        auth = SupabaseAuth(app, temPerfilLocal = { database.dao().perfil() != null })

        // No Supabase credentials baked in -> the app still runs, fully local.
        sync = if (SupabaseConfig.configurado) SupabaseSyncService(auth) else LocalSyncService()
    }
}
