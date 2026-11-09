package com.liferpg.domain

enum class SocialArea(val title: String) { FRIENDS("Amigos"), COOP("Coop"), COMPETITIVE("Competitivo") }

enum class SocialSection(val route: String, val area: SocialArea, val title: String) {
    FRIENDS("amigos", SocialArea.FRIENDS, "Amigos"),
    CIRCLES("circulos", SocialArea.COOP, "Círculos"),
    PACTS("pactos", SocialArea.COOP, "Pactos"),
    RANKING("arena", SocialArea.COMPETITIVE, "Ranking"),
    DUELS("duelos", SocialArea.COMPETITIVE, "Duelos");

    fun selectArea(next: SocialArea): SocialSection = if (area == next) this else when(next) {
        SocialArea.FRIENDS -> FRIENDS
        SocialArea.COOP -> CIRCLES
        SocialArea.COMPETITIVE -> RANKING
    }
}

data class SocialNotice(val section: SocialSection, val count: Int)
data class SocialInbox(val friends: Int = 0, val circles: Int = 0, val pacts: Int = 0, val duels: Int = 0) {
    val notices get() = listOf(SocialNotice(SocialSection.FRIENDS,friends),SocialNotice(SocialSection.CIRCLES,circles),
        SocialNotice(SocialSection.PACTS,pacts),SocialNotice(SocialSection.DUELS,duels)).filter { it.count > 0 }
    val total get() = notices.sumOf { it.count }
    val destination get() = notices.firstOrNull()?.section ?: SocialSection.FRIENDS
}
