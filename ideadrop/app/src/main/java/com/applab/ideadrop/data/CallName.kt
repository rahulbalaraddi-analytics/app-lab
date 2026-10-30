package com.applab.ideadrop.data

/**
 * Reads Samsung call-recording file names, e.g.
 *   "Call recording Amma_241030_153012.m4a"  → contact "Amma"
 *   "Call recording +91 98450 12345_250102_090501.m4a" → contact "+91 98450 12345"
 * Other names are cleaned up and used as they are.
 */
data class CallName(val contact: String, val title: String) {
    companion object {
        private val SAMSUNG = Regex("^(?:call recording|recording|call)\\s*(.*?)_(\\d{6})_(\\d{6})$", RegexOption.IGNORE_CASE)

        fun parse(fileName: String): CallName {
            val base = fileName.substringBeforeLast('.').trim()
            SAMSUNG.find(base)?.let { m ->
                val who = m.groupValues[1].replace('_', ' ').trim().ifBlank { "Unknown" }
                return CallName(who, "Call · $who")
            }
            val clean = base.replace(Regex("[_]+"), " ").replace(Regex("\\s+"), " ").trim().take(60)
            return CallName(clean, clean)
        }
    }
}
