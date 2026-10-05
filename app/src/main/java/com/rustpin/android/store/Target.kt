package com.rustpin.android.store

/** Output-size presets + custom W×H. Pure Kotlin, no Android imports (unit-tested). Mirrors native/src/settings.rs. */
data class Target(val key: String, val label: String, val w: Int, val h: Int) {
    companion object {
        val Auto = Target("auto", "Auto - match 1080p", 1920, 1080)
        val P720 = Target("720p", "720p - 1280x720", 1280, 720)
        val P1080 = Target("1080p", "1080p - 1920x1080", 1920, 1080)
        val P1440 = Target("1440p", "1440p - 2560x1440", 2560, 1440)
        val P2160 = Target("2160p", "2160p - 3840x2160", 3840, 2160)
        val ALL = listOf(Auto, P720, P1080, P1440, P2160)

        fun parse(raw: String?): Target {
            if (raw.isNullOrBlank() || raw.equals("auto", ignoreCase = true)) return Auto
            ALL.firstOrNull { it.key.equals(raw.trim(), ignoreCase = true) }?.let { return it }
            val t = raw.trim()
            val sep = t.indexOfFirst { it == 'x' || it == 'X' || it == '\u00d7' }
            if (sep > 0) {
                val w = t.substring(0, sep).trim().toIntOrNull() ?: 0
                val h = t.substring(sep + 1).trim().toIntOrNull() ?: 0
                if (w >= 16 && h >= 16) return Target(w.toString() + "x" + h.toString(), "Custom - " + w.toString() + "x" + h.toString(), w, h)
            }
            return Auto
        }
    }

    fun boxFor(imgW: Int, imgH: Int): Pair<Int, Int> {
        if (key.contains("x")) return w to h
        val bw: Int
        val bh: Int
        if (this == Auto) { bw = 1920; bh = 1080 } else { bw = w; bh = h }
        return if (imgH > imgW) bh to bw else bw to bh
    }
}
