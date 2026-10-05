package com.rustpin.android.store

/** Shared filename rule with desktop file_stem(): clean-title + pin id. Pure Kotlin, unit-tested. */
fun fileStem(title: String, pinId: String): String {
    val clean = StringBuilder()
    var dash = false
    for (c in title.lowercase()) {
        val keep = c.isLetterOrDigit() || c == '_' || c == '-'
        if (keep) { clean.append(c); dash = false }
        else if (!dash && clean.isNotEmpty()) { clean.append('-'); dash = true }
    }
    while (clean.endsWith("-")) clean.deleteCharAt(clean.length - 1)
    if (clean.length > 60) { clean.setLength(60); while (clean.endsWith("-")) clean.deleteCharAt(clean.length - 1) }
    if (clean.isEmpty()) clean.append("pin")
    val id = pinId.take(40)
    return if (id.isEmpty()) clean.toString() else clean.toString() + "_" + id
}
