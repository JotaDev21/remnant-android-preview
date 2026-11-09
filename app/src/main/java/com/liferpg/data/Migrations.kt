package com.liferpg.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written schema migrations, v1 through v9. Every statement is additive — no table is ever
 * dropped or rebuilt — so an install from any released version upgrades without losing a single row.
 */
object Migrations {

    /** Season summaries, the daily power snapshot, preferences and the meal log. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `temporada_resumo` (`numero` INTEGER NOT NULL, " +
                    "`patentePico` INTEGER NOT NULL, `powerScorePico` REAL NOT NULL, " +
                    "`pontosLigaPico` INTEGER NOT NULL, `encerradaEm` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`numero`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `power_snapshot` (`epochDay` INTEGER NOT NULL, " +
                    "`powerScore` REAL NOT NULL, `patente` INTEGER NOT NULL, " +
                    "`pontosLiga` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `preferencias` (`id` INTEGER NOT NULL, " +
                    "`objetivo` TEXT NOT NULL, `experiencia` TEXT NOT NULL, " +
                    "`localPreferido` TEXT NOT NULL, `diasPorSemana` INTEGER NOT NULL, " +
                    "`descansoPadraoSeg` INTEGER NOT NULL, `metaProteinaG` INTEGER NOT NULL, " +
                    "`rotinaAtiva` TEXT, `lembretesAtivos` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `refeicoes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`epochDay` INTEGER NOT NULL, `nome` TEXT NOT NULL, `proteinaG` REAL NOT NULL, " +
                    "`calorias` REAL NOT NULL, `carboG` REAL NOT NULL, `gorduraG` REAL NOT NULL, " +
                    "`porcaoG` REAL NOT NULL, `barcode` TEXT, `timestamp` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_refeicoes_epochDay` ON `refeicoes` (`epochDay`)")
        }
    }

    /** Body metrics on the profile, health/schedule preferences, and custom routines. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `perfil` ADD COLUMN `sexo` TEXT NOT NULL DEFAULT 'OUTRO'")
            db.execSQL("ALTER TABLE `perfil` ADD COLUMN `idade` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `perfil` ADD COLUMN `alturaCm` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `preferencias` ADD COLUMN `condicoes` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `preferencias` ADD COLUMN `horarioTreino` TEXT NOT NULL DEFAULT 'QUALQUER'")
            db.execSQL("ALTER TABLE `preferencias` ADD COLUMN `metaCalorias` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `rotina` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`nome` TEXT NOT NULL, `itensJson` TEXT NOT NULL, `criadoEm` INTEGER NOT NULL)"
            )
        }
    }

    /** Workout sessions, so the history survives independently of the individual sets. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `treino_sessao` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`epochDay` INTEGER NOT NULL, `nome` TEXT NOT NULL, `xpGanho` INTEGER NOT NULL, " +
                    "`series` INTEGER NOT NULL, `exercicios` INTEGER NOT NULL, `recordes` INTEGER NOT NULL, " +
                    "`duracaoSeg` INTEGER NOT NULL, `encerradaEm` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_treino_sessao_epochDay` ON `treino_sessao` (`epochDay`)")
        }
    }

    /** Equipped title. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `preferencias` ADD COLUMN `tituloEquipado` TEXT")
        }
    }

    /** Sets rejected by the validator stay in the table but stop counting — hence the flag. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `series` ADD COLUMN `valido` INTEGER NOT NULL DEFAULT 1")
        }
    }

    /** Body-weight tracking, one row per day. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `peso_corporal` (`epochDay` INTEGER NOT NULL, " +
                    "`pesoKg` REAL NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))"
            )
        }
    }

    /** Hunter identity customization, preserving every existing profile and workout. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `perfil` ADD COLUMN `lema` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `perfil` ADD COLUMN `estiloMoldura` TEXT NOT NULL DEFAULT 'CINZAS'")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `camera_boss` (`bossId` TEXT NOT NULL, `stage` INTEGER NOT NULL, `damage` INTEGER NOT NULL, `wins` INTEGER NOT NULL, `pendingReps` INTEGER NOT NULL, `lastSessionId` TEXT NOT NULL, `lastSessionReps` INTEGER NOT NULL, PRIMARY KEY(`bossId`))")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `camera_boss` ADD COLUMN `trialRank` INTEGER NOT NULL DEFAULT 1")
            for (column in listOf("bestTrialRank", "deaths", "lastSessionWins", "lastSessionDeaths", "lastSessionRevision")) {
                db.execSQL("ALTER TABLE `camera_boss` ADD COLUMN `$column` INTEGER NOT NULL DEFAULT 0")
            }
            // Accumulated pre-trial damage is not a completed uninterrupted attempt.
            // Workout reps, historical wins and every other table are preserved.
            db.execSQL("UPDATE `camera_boss` SET `damage` = 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS `camera_validated_set` (`id` TEXT NOT NULL, `bossId` TEXT NOT NULL, `reps` INTEGER NOT NULL, `closed` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, `registered` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("INSERT INTO `camera_validated_set` (`id`, `bossId`, `reps`, `closed`, `completedAt`, `registered`) SELECT 'legacy:' || `bossId`, `bossId`, `pendingReps`, 1, 0, 0 FROM `camera_boss` WHERE `pendingReps` > 0")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `perfil` ADD COLUMN `biografia` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `perfil` ADD COLUMN `focoTreino` TEXT NOT NULL DEFAULT 'EQUILIBRIO'")
            db.execSQL("ALTER TABLE `perfil` ADD COLUMN `seloPerfil` TEXT NOT NULL DEFAULT 'ESPADA'")
            db.execSQL("ALTER TABLE `treino_sessao` ADD COLUMN `itensJson` TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `preferencias` ADD COLUMN `metaCarboG` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `preferencias` ADD COLUMN `metaGorduraG` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `preferencias` ADD COLUMN `metasNutricaoManuais` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `preferencias` ADD COLUMN `atividadeNutri` TEXT NOT NULL DEFAULT 'LEVE'")
            db.execSQL("ALTER TABLE `preferencias` ADD COLUMN `objetivoNutri` TEXT NOT NULL DEFAULT 'MANTER'")
            db.execSQL("ALTER TABLE `refeicoes` ADD COLUMN `categoria` TEXT NOT NULL DEFAULT 'OUTROS'")
            db.execSQL("ALTER TABLE `refeicoes` ADD COLUMN `fonte` TEXT NOT NULL DEFAULT 'Registro anterior'")
            db.execSQL("ALTER TABLE `refeicoes` ADD COLUMN `estimado` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `refeicoes` ADD COLUMN `fibraG` REAL")
            db.execSQL("ALTER TABLE `refeicoes` ADD COLUMN `macrosCompletos` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `refeicoes` ADD COLUMN `registroId` TEXT")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_refeicoes_registroId` ON `refeicoes` (`registroId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `agua` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `ml` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agua_epochDay` ON `agua` (`epochDay`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `alimentos_salvos` (`id` TEXT NOT NULL, `nome` TEXT NOT NULL, `alimentoJson` TEXT NOT NULL, `porcaoG` REAL NOT NULL, PRIMARY KEY(`id`))")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `evolution_entry` (`id` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("ALTER TABLE `series` ADD COLUMN `esforco` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `series` ADD COLUMN `equipamento` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `series` ADD COLUMN `origem` TEXT NOT NULL DEFAULT 'MANUAL'")
        }
    }

    val MIGRATION_13_14 = object : Migration(13,14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `treino_sessao` ADD COLUMN `sessionUuid` TEXT")
            db.execSQL("ALTER TABLE `treino_sessao` ADD COLUMN `receiptJson` TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_treino_sessao_sessionUuid` ON `treino_sessao` (`sessionUuid`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `circle_delivery` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `circleId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `receipt` TEXT NOT NULL, `action` TEXT NOT NULL, `status` TEXT NOT NULL, `error` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_circle_delivery_userId` ON `circle_delivery` (`userId`)")
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `cardio_session` (`id` TEXT NOT NULL, `activity` TEXT NOT NULL, `goal` TEXT NOT NULL, `target` REAL NOT NULL, `startedAt` INTEGER NOT NULL, `accumulatedMs` INTEGER NOT NULL, `anchorMs` INTEGER NOT NULL, `boot` INTEGER NOT NULL, `status` TEXT NOT NULL, `distanceKm` REAL, `effort` TEXT NOT NULL, `note` TEXT NOT NULL, `source` TEXT NOT NULL, `endedAt` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `xpGained` INTEGER NOT NULL, `device` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cardio_session_epochDay` ON `cardio_session` (`epochDay`)")
            db.execSQL("ALTER TABLE `cardio_session` ADD COLUMN `steps` INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cardio_session_status` ON `cardio_session` (`status`)")
        }
    }

    val TODAS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
    )
}
