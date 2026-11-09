package com.liferpg.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class CircleMigrationTest {
    @Test fun version13KeepsProfileSetsAndLegacySessionsWithNullableUuids() = runTest {
        val ctx=ApplicationProvider.getApplicationContext<Context>(); val name="circle-migration-test.db"; ctx.deleteDatabase(name)
        val schema=listOf(File("schemas/com.liferpg.data.AppDatabase/13.json"),File("app/schemas/com.liferpg.data.AppDatabase/13.json")).first { it.isFile }
        val entities=JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(object:SupportSQLiteOpenHelper.Callback(13) {
            override fun onCreate(db:SupportSQLiteDatabase) {
                for(i in 0 until entities.length()) { val e=entities.getJSONObject(i); db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
                    val indices=e.optJSONArray("indices") ?: org.json.JSONArray(); for(j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName"))) }
                db.execSQL("INSERT INTO perfil(id,nome,classe,pesoCorporalKg,xpTotal,streak,melhorStreak,ultimoDiaTreino,criadoEm) VALUES(1,'jota','TITAN',80,9900,12,20,20000,1)")
                db.execSQL("INSERT INTO treino_sessao(epochDay,nome,xpGanho,series,exercicios,recordes,duracaoSeg,encerradaEm) VALUES(20000,'Peito',100,10,3,1,3600,1),(20001,'Costas',100,10,3,1,3600,2)")
            }
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=Unit
        }).build()); helper.writableDatabase; helper.close()
        val upgraded=Room.databaseBuilder(ctx,AppDatabase::class.java,name).addMigrations(*Migrations.TODAS).allowMainThreadQueries().build()
        try { val dao=upgraded.dao(); assertEquals(9900L,dao.perfil()!!.xpTotal); assertEquals(12,dao.perfil()!!.streak)
            val sessions=dao.sessoesRecentes(10).first(); assertEquals(2,sessions.size); assertTrue(sessions.all { it.sessionUuid==null && it.receiptJson.isEmpty() })
            assertTrue(dao.circleDeliveries("a").first().isEmpty())
        } finally { upgraded.close(); ctx.deleteDatabase(name) }
    }
}
