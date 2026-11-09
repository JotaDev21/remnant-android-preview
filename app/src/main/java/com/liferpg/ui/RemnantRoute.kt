package com.liferpg.ui

/** Single source of truth for the string routes kept in saved instance state. */
internal object RemnantRoute {
    const val TAB_PREFIX = "tab:"
    const val EXERCISE_PREFIX = "ex:"
    const val CAMERA_PREFIX = "camera:"
    const val MUSCLE_RANK_PREFIX = "patente:"

    const val MENTOR = "mentor"
    const val SYSTEM = "sistema"
    const val BOSS_HUNT = "chefes"
    const val BODY_PHOTOS = "fotos"
    const val PLAN = "plano"
    const val SESSION = "sessao"
    const val HISTORY = "historico-treinos"
    const val HISTORY_EXERCISE_PREFIX = "historico-exercicio:"
    const val HISTORY_SESSION_PREFIX = "historico-sessao:"
    const val CARDIO = "cardio"
    const val TROPHIES = "trofeus"
    const val TITLES = "titulos"
    const val WEIGHT = "peso"
    const val SETTINGS = "config"
    const val ROUTINES = "rotinas"
    const val RECORDS = "recordes"
    const val NUTRITION = "nutricao"
    const val CELEBRATIONS = "celebracoes"
    const val ARENA = "arena"
    const val HEALTH = "health"
    const val WGER = "wger"
    const val FOOD_SEARCH = "alimentos"
    const val FOOD_SCANNER = "scanner"
    const val FOOD_PHOTO = "prato"
    const val RECIPES = "receitas"
    const val TOOLS = "ferramentas"
    const val PRIVACY = "privacidade"
    const val JOURNEY = "jornada"
    const val CLOUD_SAVE = "save-nuvem"
    const val PACTS = "pactos"
    const val FRIENDS = "amigos"
    const val CIRCLES = "circulos"
    const val DUELS = "duelos"

    const val FRIENDS_TAB = 5
    fun socialSection(route: String): com.liferpg.domain.SocialSection? =
        if(com.liferpg.data.sync.CircleConversationTarget.parse(route)!=null) com.liferpg.domain.SocialSection.CIRCLES
        else if(route==tab(FRIENDS_TAB)) com.liferpg.domain.SocialSection.FRIENDS
        else com.liferpg.domain.SocialSection.entries.firstOrNull { it.route==route }

    fun tab(index: Int) = "$TAB_PREFIX$index"
    fun exercise(id: String) = "$EXERCISE_PREFIX$id"
    fun camera(id: String) = "$CAMERA_PREFIX$id"
    fun muscleRank(id: String) = "$MUSCLE_RANK_PREFIX$id"
    fun exerciseHistory(id: String) = "$HISTORY_EXERCISE_PREFIX$id"
    fun sessionHistory(id: Long) = "$HISTORY_SESSION_PREFIX$id"
    fun isHistory(route: String?) = route == HISTORY || route?.startsWith(HISTORY_EXERCISE_PREFIX) == true || route?.startsWith(HISTORY_SESSION_PREFIX) == true

    fun tabIndex(route: String): Int? = route.takeIf { it.startsWith(TAB_PREFIX) }
        ?.removePrefix(TAB_PREFIX)?.toIntOrNull()?.takeIf { it in 0..5 }
    fun exerciseId(route: String): String? = route.takeIf { it.startsWith(EXERCISE_PREFIX) }?.removePrefix(EXERCISE_PREFIX)
    fun cameraExerciseId(route: String): String? = route.takeIf { it.startsWith(CAMERA_PREFIX) }?.removePrefix(CAMERA_PREFIX)
    fun muscleId(route: String): String? = route.takeIf { it.startsWith(MUSCLE_RANK_PREFIX) }?.removePrefix(MUSCLE_RANK_PREFIX)
    fun isImmersive(route: String?): Boolean = route == SYSTEM || route == BOSS_HUNT || isHistory(route) || route?.startsWith(CAMERA_PREFIX) == true
}
