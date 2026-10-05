package com.rustpin.android

import com.rustpin.android.store.Accent
import org.junit.Assert.*
import org.junit.Test

class AppearanceTest {
    @Test fun accentDefaultsToRed() {
        assertEquals(Accent.RED, Accent.parse(null))
        assertEquals(Accent.RED, Accent.parse("nonsense"))
        assertEquals(Accent.BLUE, Accent.parse("blue"))
    }
}
