package com.example.data.model

enum class ReadingTheme(val label: String, val subtitle: String) {
    LIGHT("Light", "Crisp ivory paper"),
    SEPIA("Sepia", "Warm eye-comfort parchment"),
    DARK("Dark", "Low-glare midnight ink");

    companion object {
        fun fromName(raw: String?): ReadingTheme =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: LIGHT
    }
}
