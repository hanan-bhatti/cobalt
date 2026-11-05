package com.example.links

object LinkDescriptionPolicy {
    /** One saved description. Only a missing value or untouched AI fallback is enriched automatically. */
    fun automatic(saved: String?, page: String?, replaceFallback: Boolean): String? =
        if (saved == null || replaceFallback) page?.takeIf(String::isNotBlank) ?: saved else saved
}
