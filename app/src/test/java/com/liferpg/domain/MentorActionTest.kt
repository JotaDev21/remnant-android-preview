package com.liferpg.domain

import org.junit.Assert.*
import org.junit.Test

class MentorActionTest {
    private val catalog = setOf("pushup", "squat")
    @Test fun rejectsUnknownCommandsAndExternalRoutes() {
        listOf("delete_save", "publish_ranking", "register_xp").forEach { assertFalse(MentorAction(it).valid(catalog)) }
        listOf("https://example.com", "ex:invented", "patente:INVALID", "tab:9", "reset").forEach { assertFalse(MentorAction("navigate", it).valid(catalog)) }
        assertTrue(MentorAction("navigate", "ex:pushup").valid(catalog))
        assertTrue(MentorAction("navigate", "camera:pushup").valid(catalog))
        assertTrue(MentorAction("navigate", "sistema").valid(catalog))
        listOf("arena", "health", "wger", "alimentos", "scanner", "receitas", "ferramentas").forEach {
            assertTrue(MentorAction("navigate", it).valid(catalog))
        }
    }
    @Test fun preventsFabricatedAndDuplicateExercises() {
        assertFalse(MentorAction("start_workout").valid(catalog))
        assertFalse(MentorAction("start_workout", ids = listOf("invented")).valid(catalog))
        assertFalse(MentorAction("start_workout", ids = listOf("pushup", "pushup")).valid(catalog))
        assertTrue(MentorAction("start_workout", ids = listOf("pushup", "squat")).valid(catalog))
    }
    @Test fun boundsRestAndWorkoutNames() {
        listOf(-1, 0, 14, 601, Int.MAX_VALUE).forEach { assertFalse(MentorAction("rest", seconds = it).valid(catalog)) }
        listOf(15, 90, 600).forEach { assertTrue(MentorAction("rest", seconds = it).valid(catalog)) }
        assertFalse(MentorAction("start_workout", ids = listOf("squat"), name = " ").valid(catalog))
        assertFalse(MentorAction("start_workout", ids = listOf("squat"), name = "a".repeat(81)).valid(catalog))
    }
}
