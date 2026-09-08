package com.itantra.app.core.model

enum class Language(val displayName: String, val code: String) {
    ENGLISH("English", "en"),
    HINDI("हिन्दी", "hi"),
    MALAYALAM("മലയാളം", "ml"),
    BENGALI("বাংলা", "bn"),
    KANNADA("ಕನ್ನಡ", "kn"),
    MARATHI("मराठी", "mr"),
    TAMIL("தமிழ்", "ta"),
    TELUGU("తెలుగు", "te"),
    GUJARATI("ગુજરાતી", "gu"),
    ODIA("ଓଡ଼ିଆ", "or");

    val indicConformerCode: String?
        get() = when (this) {
            ENGLISH -> "en"
            HINDI -> "hi"
            BENGALI -> "bn"
            GUJARATI -> "gu"
            MARATHI -> "mr"
            KANNADA -> "kn"
            MALAYALAM -> "ml"
            TAMIL -> "ta"
            TELUGU -> "te"
            ODIA -> null
        }

    val isIndicConformerSupported: Boolean
        get() = indicConformerCode != null
}
