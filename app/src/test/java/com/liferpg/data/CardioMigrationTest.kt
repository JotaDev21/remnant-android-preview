package com.liferpg.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class CardioMigrationTest {
    @Test fun additiveVersion15PreservesExistingSaveAndPendingCircleDelivery() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>(); val name = "cardio-migration-test.db"; ctx.deleteDatabase(name)
        val schema = listOf(File("schemas/com.liferpg.data.AppDatabase/14.json"), File("app/schemas/com.liferpg.data.AppDatabase/14.json")).first { it.isFile }
        val entities = JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(object : SupportSQLiteOpenHelper.Callback(14) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                for (i in 0 until entities.length()) { val e = entities.getJSONObject(i); db.execSQL(e.getString("createSql").replace("${'$'}{TABLE_NAME}", e.getString("tableName")))
                    val indices = e.optJSONArray("indices") ?: org.json.JSONArray(); for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", e.getString("tableName"))) }
                db.execSQL("INSERT INTO perfil(id,nome,classe,pesoCorporalKg,xpTotal,streak,melhorStreak,ultimoDiaTreino,criadoEm) VALUES(1,'Atleta','TITAN',80,9900,12,20,20000,1)")
                db.execSQL("INSERT INTO circle_delivery VALUES('delivery','owner','circle','session','{}','publish','pending',NULL,1)")
                db.execSQL("INSERT INTO treino_sessao(epochDay,nome,xpGanho,series,exercicios,recordes,duracaoSeg,encerradaEm,sessionUuid,receiptJson) VALUES(20000,'Peito',100,10,3,1,3600,1,'uuid','{\"title\":\"Peito\"}')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build()); helper.writableDatabase; helper.close()
        val upgraded = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*Migrations.TODAS).allowMainThreadQueries().build()
        try {
            val dao = upgraded.dao(); assertEquals(9900L, dao.perfil()!!.xpTotal); assertEquals(12, dao.perfil()!!.streak)
            assertEquals("uuid", dao.todasSessoes().single().sessionUuid); assertEquals("delivery", dao.circleDeliveries("owner").first().single().id)
            assertNull(dao.activeCardio()); assertTrue(dao.allCardio().isEmpty())
        } finally { upgraded.close(); ctx.deleteDatabase(name) }
    }
}
