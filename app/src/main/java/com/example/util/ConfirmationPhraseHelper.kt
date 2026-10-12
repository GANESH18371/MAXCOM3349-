package com.example.util

/**
 * Standardized Confirmation Phrase System for Max.
 * Uses a single, consistent style across the entire app: "Ji Sir, ho gaya"
 * Emits exact required Debug Log: "CONFIRMATION_PHRASE_USED: <text>"
 */
object ConfirmationPhraseHelper {

    const val STANDARD_CONFIRMATION = "Ji Sir, ho gaya"

    /**
     * Returns the standardized confirmation phrase, with optional action context prefix
     * (e.g. "YouTube par gana chala diya. Ji Sir, ho gaya" or standalone "Ji Sir, ho gaya").
     * Always logs the confirmation phrase used.
     */
    fun getConfirmation(actionDescription: String? = null): String {
        val phrase = if (actionDescription.isNullOrBlank()) {
            STANDARD_CONFIRMATION
        } else {
            val cleanDesc = actionDescription.trim().trimEnd('.')
            "$cleanDesc. $STANDARD_CONFIRMATION"
        }
        DebugLogger.logConfirmationPhraseUsed(phrase)
        return phrase
    }
}
