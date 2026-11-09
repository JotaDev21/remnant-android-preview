package com.liferpg.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class ProfileSocialMigrationTest {
    @Test fun roomValidatesFullSchemaUpgradeAndKeepsTrainingAndBossProgress() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "social-migration-test.db"
        context.deleteDatabase(name)
        val schemaFile = listOf(File("schemas/com.liferpg.data.AppDatabase/10.json"),File("app/schemas/com.liferpg.data.AppDatabase/10.json"))
            .first { it.isFile }
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object: SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for(i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
                        val indexes = entity.optJSONArray("indices") ?: org.json.JSONArray()
                        for(j in 0 until indexes.length()) db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
                    }
                    db.execSQL("INSERT INTO perfil(id,nome,classe,pesoCorporalKg,xpTotal,streak,melhorStreak,ultimoDiaTreino,criadoEm) VALUES(1,'jota','TITAN',80,4200,12,15,20000,1)")
                    db.execSQL("INSERT INTO series(exercicioId,musculo,pesoKg,reps,segundos,score,xpGanho,epochDay,timestamp) VALUES('bench','PEITO',80,10,NULL,1.4,10,20000,1)")
                    db.execSQL("INSERT INTO camera_boss(bossId,stage,damage,wins,pendingReps,lastSessionId,lastSessionReps) VALUES('ASH_WARDEN',3,0,3,39,'old',39)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase,oldVersion:Int,newVersion:Int) = Unit
            }).build())
        helper.writableDatabase
        helper.close()
        val upgraded = Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(*Migrations.TODAS).allowMainThreadQueries().build()
        try {
            val dao = upgraded.dao()
            val profile = dao.perfil()!!
            assertEquals("jota",profile.nome);assertEquals(4200L,profile.xpTotal);assertEquals(12,profile.streak)
            assertEquals("EQUILIBRIO",profile.focoTreino);assertEquals("ESPADA",profile.seloPerfil);assertEquals("",profile.biografia)
            assertEquals(80f,dao.todasSeries().single().pesoKg!!,.001f)
            assertEquals(3,dao.cameraBoss("ASH_WARDEN")?.wins);assertEquals(39,dao.cameraBoss("ASH_WARDEN")?.pendingReps)
        } finally { upgraded.close();context.deleteDatabase(name) }
    }
}
