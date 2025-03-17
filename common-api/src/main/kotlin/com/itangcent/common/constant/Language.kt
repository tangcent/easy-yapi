package com.itangcent.common.constant

/**
 * Enum representing supported languages for translation
 * @property code The language ISO code (e.g., "en", "zh")
 * @property displayName The human-readable language name (e.g., "English", "Chinese")
 */
enum class Language(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    CHINESE("zh", "Chinese"),
    SPANISH("es", "Spanish"),
    FRENCH("fr", "French"),
    GERMAN("de", "German"),
    JAPANESE("ja", "Japanese"),
    KOREAN("ko", "Korean"),
    RUSSIAN("ru", "Russian"),
    PORTUGUESE("pt", "Portuguese"),
    ITALIAN("it", "Italian"),
    DUTCH("nl", "Dutch"),
    ARABIC("ar", "Arabic"),
    HINDI("hi", "Hindi"),
    TURKISH("tr", "Turkish"),
    VIETNAMESE("vi", "Vietnamese");

    companion object {
        /**
         * Find a Language by its code
         * @param code The language code to search for
         * @return The Language enum value or null if not found
         */
        fun fromCode(code: String?): Language? {
            return entries.find { it.code == code }
        }

        /**
         * Get the language name from its code
         * @param code The language code
         * @return The language name or the original code if not found
         */
        fun getNameFromCode(code: String): String {
            return fromCode(code)?.displayName ?: code
        }

        /**
         * Get the default language (English)
         */
        fun getDefault(): Language = ENGLISH
    }
}