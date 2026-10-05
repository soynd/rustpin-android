package com.rustpin.android

import com.rustpin.android.store.Target
import org.junit.Assert.*
import org.junit.Test

/** Mirrors native/src/settings.rs tests: round-trip, orientation, custom. */
class TargetTest {
    @Test fun roundTripsThroughText() {
        assertEquals(Target.Auto, Target.parse(Target.Auto.key))
        assertEquals(Target.P1080, Target.parse("1080p"))
        assertEquals(Target.P1080, Target.parse("1080P"))
        assertEquals(Target.parse("2560x1440"), Target("2560x1440", "Custom - 2560x1440", 2560, 1440))
        assertEquals(Target.Auto, Target.parse(""))
        assertEquals(Target.Auto, Target.parse("nonsense"))
        assertEquals(Target.Auto, Target.parse("12x12"))
    }
    @Test fun boxesTurnWithTheImage() {
        assertEquals(1080 to 1920, Target.Auto.boxFor(736, 1300))
        assertEquals(1920 to 1080, Target.Auto.boxFor(1300, 736))
        assertEquals(1080 to 1920, Target.P1080.boxFor(736, 1300))
        assertEquals(800 to 600, Target.parse("800x600").boxFor(736, 1300))
    }
    @Test fun customBoxIsUsedExactly() {
        val c = Target.parse("800x600")
        assertEquals("800x600", c.key)
        assertEquals(800 to 600, c.boxFor(100, 5000))
    }
}
