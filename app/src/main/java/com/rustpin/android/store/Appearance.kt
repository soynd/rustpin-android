package com.rustpin.android.store

import androidx.compose.ui.graphics.Color

/** Accent choices. */
enum class Accent(val key: String, val label: String, val color: Color) {
    RED("red", "Red", Color(0xFFE60023)),
    ORANGE("orange", "Orange", Color(0xFFF57C00)),
    YELLOW("yellow", "Yellow", Color(0xFFFBC02D)),
    GREEN("green", "Green", Color(0xFF43A047)),
    BLUE("blue", "Blue", Color(0xFF1E88E5)),
    PURPLE("purple", "Purple", Color(0xFF8E24AA)),
    PINK("pink", "Pink", Color(0xFFEC407A));

    companion object {
        val DEFAULT = RED
        fun parse(raw: String?): Accent =
            values().firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
