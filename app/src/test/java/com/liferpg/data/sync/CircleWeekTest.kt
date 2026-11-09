package com.liferpg.data.sync

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class CircleWeekTest {
    private val monday=LocalDate.of(2026,10,12)
    private fun row(uid:String,goal:Int,days:Int)=CircleWeekMember(uid,uid,goal,(0 until days).map { monday.plusDays(it.toLong()) }.toSet(),days,days*2,days,days*600L)
    private fun payload()=JSONObject().put("circle_id","circle").put("week_start","2026-10-12")
        .put("first_week","2026-10-05").put("current_week","2026-10-12").put("today","2026-10-14").put("timezone","America/Sao_Paulo")
        .put("members",JSONArray().put(JSONObject().put("uid","a").put("nickname","jota").put("goal",3)
            .put("dates",JSONArray(listOf("2026-10-12","2026-10-14"))).put("workouts",3).put("sets",8).put("prs",1).put("seconds",3000)))
    @Test fun countsServerTotalsAndDistinctDatesWithoutInferringFromTheFeed() {
        val week=CircleWeekCodec.read(payload().toString()); val member=week.members.single()
        assertEquals(2,member.days); assertEquals(3,member.workouts); assertEquals(8,member.sets)
        assertEquals(1,member.remaining); assertEquals(3000L,member.seconds); assertTrue(week.isCurrent)
    }
    @Test fun equalGoalCompletionSharesPositionAndNeverRewardsExtraWorkouts() {
        val members=listOf(row("a",3,3),row("b",5,5),row("c",1,7),row("d",3,2),row("e",6,4))
        val week=CircleWeek("c",monday,monday,monday,monday,"UTC",members)
        members.take(3).forEach { assertEquals(1,week.position(it)); assertEquals(1f,it.progress,0f) }
        members.drop(3).forEach { assertEquals(4,week.position(it)) }; assertEquals(3,week.goalsMet)
    }
    @Test fun invalidDatesDuplicateDaysAndUnexpectedWeeksAreRejected() {
        fun rejects(mutator:(JSONObject)->Unit) { val p=payload(); mutator(p); assertThrows(IllegalArgumentException::class.java) { CircleWeekCodec.read(p.toString()) } }
        rejects { it.getJSONArray("members").getJSONObject(0).put("dates",JSONArray(listOf("2026-10-11"))) }
        rejects { it.getJSONArray("members").getJSONObject(0).put("dates",JSONArray(listOf("2026-10-12","2026-10-12"))) }
        rejects { it.put("week_start","2026-10-13") }
        rejects { it.put("first_week","2026-06-01") }
        rejects { it.getJSONArray("members").getJSONObject(0).put("prs",9) }
        rejects { it.getJSONArray("members").getJSONObject(0).put("workouts",0) }
    }
}
