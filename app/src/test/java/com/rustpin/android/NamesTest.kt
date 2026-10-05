package com.rustpin.android

import com.rustpin.android.store.fileStem
import org.junit.Assert.*
import org.junit.Test

class NamesTest {
    @Test fun slugPlusId() {
        assertEquals("cozy-wallpaper_abc123", fileStem("Cozy Wallpaper!!", "abc123"))
    }
    @Test fun emptyTitleFallsBackToPin() {
        assertEquals("pin", fileStem("!!!", ""))
        assertEquals("pin_xyz", fileStem("!!!", "xyz"))
    }
    @Test fun longTitlesTrimTo60() {
        val stem = fileStem("a".repeat(200), "id")
        assertTrue(stem.startsWith("a".repeat(60)))
        assertTrue(stem.length <= 60 + 1 + 40)
    }
}
