package com.liferpg.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.liferpg.notificationSocialRoute
import com.liferpg.notif.Notificacoes
import com.liferpg.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[32])
class SocialNotificationTest {
    @Test fun commentNotificationsKeepTheirWorkoutDestinationsAndRejectAnotherAccount() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val circle="11111111-1111-1111-1111-111111111111"; val p1="22222222-2222-2222-2222-222222222222"; val p2="33333333-3333-3333-3333-333333333333"
        val first=com.liferpg.data.sync.CircleCommentNotice("c1",circle,p1,"Aliado","Peito","2026-10-08T17:00:00Z")
        assertTrue(Notificacoes.mostrarComentarioCirculo(ctx,"a",first))
        assertTrue(Notificacoes.mostrarComentarioCirculo(ctx,"a",first.copy(id="c2",post=p2,title="Costas")))
        val notes=ctx.getSystemService(NotificationManager::class.java).activeNotifications.filter { it.notification.channelId=="circle_conversations" }
        assertEquals(2,notes.size)
        val targets=notes.map { val intent=shadowOf(it.notification.contentIntent).savedIntent
            assertNull(notificationSocialRoute(intent,"b")); notificationSocialRoute(intent,"a") }
        assertEquals(setOf("circulos/$circle/recibo/$p1","circulos/$circle/recibo/$p2"),targets.toSet())
        assertEquals(SocialSection.CIRCLES,RemnantRoute.socialSection(targets.first()!!))
        assertNull(notificationSocialRoute(Intent().putExtra("open_social_section","circulos/not-a-uuid/recibo/$p1")))
        Notificacoes.limparConversa(ctx,"a",p1)
        assertEquals(1,ctx.getSystemService(NotificationManager::class.java).activeNotifications.count { it.notification.channelId=="circle_conversations" })
    }
    @Test fun circleAndPactNotificationsKeepDifferentDestinationsAfterBothArePosted() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        Notificacoes.criarCanais(context)
        assertTrue(Notificacoes.mostrarConvitesArena(context,0,0,circulos=1))
        val manager=context.getSystemService(NotificationManager::class.java)
        val circle=manager.activeNotifications.single().notification.contentIntent
        assertTrue(Notificacoes.mostrarConvitesArena(context,0,0,pactos=1))
        val pact=manager.activeNotifications.first { it.notification.contentIntent!=circle }.notification.contentIntent
        assertEquals(RemnantRoute.CIRCLES,notificationSocialRoute(shadowOf(circle).savedIntent))
        assertEquals(RemnantRoute.PACTS,notificationSocialRoute(shadowOf(pact).savedIntent))
        assertNotEquals(circle,pact)
    }
    @Test fun legacyNotificationOpensFriendsAndUntrustedIntentCannotOpenOtherScreens() {
        assertEquals(RemnantRoute.FRIENDS,notificationSocialRoute(Intent().putExtra("open_arena",true)))
        assertNull(notificationSocialRoute(Intent().putExtra("open_social_section",RemnantRoute.BODY_PHOTOS)))
        assertNull(notificationSocialRoute(Intent().putExtra("open_social_section","https://evil.example")))
        assertNull(notificationSocialRoute(Intent()))
        SocialSection.entries.forEach { assertEquals(it,RemnantRoute.socialSection(it.route)) }
        assertEquals(SocialSection.FRIENDS,RemnantRoute.socialSection(RemnantRoute.tab(5)))
        assertEquals(4,RemnantRoute.tabIndex(RemnantRoute.tab(4)))
        assertNull(RemnantRoute.tabIndex("tab:6"))
    }
    @Test fun everyKindOfIncomingInvitationHasAnActionableAreaAndPendingCount() {
        val inbox=SocialInbox(friends=2,circles=1,pacts=3,duels=1)
        assertEquals(7,inbox.total)
        assertEquals(setOf(SocialSection.FRIENDS,SocialSection.CIRCLES,SocialSection.PACTS,SocialSection.DUELS),inbox.notices.map { it.section }.toSet())
        assertEquals(SocialSection.PACTS,SocialSection.PACTS.selectArea(SocialArea.COOP))
        assertEquals(SocialSection.RANKING,SocialSection.FRIENDS.selectArea(SocialArea.COMPETITIVE))
        assertEquals(SocialSection.CIRCLES,SocialSection.DUELS.selectArea(SocialArea.COOP))
        SocialSection.entries.forEach { assertTrue(MentorAction("navigate",it.route).valid(emptySet())) }
    }
}
