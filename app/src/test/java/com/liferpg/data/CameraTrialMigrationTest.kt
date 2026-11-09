package com.liferpg.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraTrialMigrationTest {
    @Test fun migrationKeepsProfileWorkoutWinsAndPendingReps() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext<Context>()).name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE perfil (nome TEXT NOT NULL, xpTotal INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO perfil VALUES ('jota', 4200)")
                    db.execSQL("CREATE TABLE series (reps INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO series VALUES (17)")
                    Migrations.MIGRATION_8_9.migrate(db)
                    db.execSQL("INSERT INTO camera_boss VALUES ('ASH_WARDEN', 3, 9, 3, 39, 'old', 39)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            val db = helper.writableDatabase
            Migrations.MIGRATION_9_10.migrate(db)
            db.query("SELECT nome, xpTotal FROM perfil").use { it.moveToFirst(); assertEquals("jota", it.getString(0)); assertEquals(4200, it.getInt(1)) }
            db.query("SELECT reps FROM series").use { it.moveToFirst(); assertEquals(17, it.getInt(0)) }
            db.query("SELECT wins, damage, pendingReps, trialRank FROM camera_boss").use {
                it.moveToFirst(); assertEquals(3, it.getInt(0)); assertEquals(0, it.getInt(1))
                assertEquals(39, it.getInt(2)); assertEquals(1, it.getInt(3))
            }
            db.query("SELECT reps, closed, registered FROM camera_validated_set").use {
                it.moveToFirst(); assertEquals(39, it.getInt(0)); assertEquals(1, it.getInt(1)); assertEquals(0, it.getInt(2))
            }
        } finally { helper.close() }
    }
}
