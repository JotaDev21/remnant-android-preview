package com.liferpg.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BossCampaignTest {
    @Test fun onlyValidatedDamageAdvancesAndPersistsAcrossSets() {
        val boss = CameraBoss.ASH_WARDEN
        val first = BossCampaign.advance(boss, 0, 0, 0, 5)
        assertEquals(BossAdvance(0, 5, 0), first)
        val second = BossCampaign.advance(boss, first.stage, first.damage, first.wins, 7)
        assertEquals(BossAdvance(1, 0, 1), second)
        assertEquals(20, boss.hpFor(second.stage))
    }

    @Test fun extraRepsCarryIntoNextBossWithoutDoubleCounting() {
        val result = BossCampaign.advance(CameraBoss.STONE_COLOSSUS, 0, 14, 0, 5)
        assertEquals(BossAdvance(1, 3, 1), result)
        assertEquals(result, BossCampaign.advance(CameraBoss.STONE_COLOSSUS, result.stage, result.damage, result.wins, 0))
    }
}
