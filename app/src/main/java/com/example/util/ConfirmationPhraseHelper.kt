package com.example.util

/**
 * Standardized Confirmation Phrase System for Max.
 * Uses a single, consistent style across the entire app: "Ji Sir, ho gaya"
 * Emits exact required Debug Log: "CONFIRMATION_PHRASE_USED: <text>"
 */
object ConfirmationPhraseHelper {

    const val STANDARD_CONFIRMATION = "Ji Sir, ho gaya"
    const val MULTI_STEP_FINAL_CONFIRMATION = "Ji Sir, kar diya"

    /**
     * Fast single-step confirmation: "Ji Sir, ho gaya"
     */
    fun getSingleStepConfirmation(actionDescription: String? = null): String {
        val phrase = STANDARD_CONFIRMATION
        DebugLogger.logConfirmationPhraseUsed(phrase)
        return phrase
    }

    /**
     * Final multi-step completion confirmation: "Ji Sir, kar diya"
     * Spoken ONLY once after all sequential steps complete.
     */
    fun getMultiStepFinalConfirmation(actionDescription: String? = null): String {
        val phrase = MULTI_STEP_FINAL_CONFIRMATION
        DebugLogger.logConfirmationPhraseUsed(phrase)
        return phrase
    }

    /**
     * Standardized confirmation phrase helper.
     */
    fun getConfirmation(actionDescription: String? = null): String {
        val phrase = STANDARD_CONFIRMATION
        DebugLogger.logConfirmationPhraseUsed(phrase)
        return phrase
    }
}
