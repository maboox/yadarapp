package com.yadavard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TiersTest {
    @Test fun tiersFollowTokensBought() {
        assertNull(Tier.of(0.0, false))
        assertEquals(Tier.BRONZE, Tier.of(50.0, false))
        assertEquals(Tier.BRONZE, Tier.of(199.0, false))
        assertEquals(Tier.SILVER, Tier.of(200.0, false))
        assertEquals(Tier.SILVER, Tier.of(499.0, false))
        assertEquals(Tier.GOLD, Tier.of(500.0, false))
        assertEquals(Tier.GOLD, Tier.of(1500.0, false))
        assertEquals(Tier.DIAMOND, Tier.of(1501.0, false))
        // the admin always has the admin frame, whatever was bought
        assertEquals(Tier.ADMIN, Tier.of(0.0, true))
        assertEquals(Tier.ADMIN, Tier.of(5000.0, true))
    }

    @Test fun eachTierAddsToTheOneBefore() {
        val order = listOf(Tier.BRONZE, Tier.SILVER, Tier.GOLD, Tier.DIAMOND)
        order.zipWithNext { a, b -> assertTrue("$b has at least as many stars as $a", b.stars >= a.stars); assertTrue(b.sparkles >= a.sparkles) }
        assertTrue(Tier.ADMIN.crown && Tier.ADMIN.wings && Tier.DIAMOND.wings && !Tier.GOLD.wings)
        Tier.entries.forEach { assertEquals(5, it.metal.size); assertEquals(2, it.gem.size) }
    }

    @Test fun everyBadgeSaysHowToEarnIt() {
        GameRules.badges(GameFacts()).forEach { b ->
            val (fa, en) = GameRules.badgeHow(b.key)
            assertTrue("badge ${b.key} has no explanation", fa.isNotBlank() && en.isNotBlank())
        }
    }
}
