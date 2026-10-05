package com.rustpin.android.model

import kotlinx.serialization.Serializable

/** Mirrors native/src/pinterest.rs `Pin`. */
@Serializable
data class Pin(
    val id: String,
    val title: String,
    val thumb: String,
    val preview: String,
    val orig: String,
    val pageUrl: String,
    val w: Int = 0,
    val h: Int = 0,
) {
    val aspect: Float get() = if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 1f
    val resolutionLabel: String get() = if (w > 0 && h > 0) "${w}×${h}" else "—"
}
