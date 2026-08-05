package com.lifeledger.core.model

/**
 * A 0..1 confidence score with a small vocabulary of named bands.
 *
 * Life Ledger never silently discards a low-confidence parse — it stores it with the score
 * so Developer Mode and the Parser Logs screen can show exactly why something was
 * classified the way it was, and so the user can correct it.
 */
@JvmInline
value class Confidence(val value: Float) : Comparable<Confidence> {

    init {
        require(!value.isNaN()) { "Confidence must be a number" }
    }

    val band: Band
        get() = when {
            value >= 0.9f -> Band.CERTAIN
            value >= 0.7f -> Band.HIGH
            value >= 0.5f -> Band.MEDIUM
            value >= 0.25f -> Band.LOW
            else -> Band.GUESS
        }

    /** True when the value is trustworthy enough to act on without asking the user. */
    val isActionable: Boolean get() = value >= MIN_ACTIONABLE

    operator fun times(other: Confidence) = Confidence(value * other.value)

    override fun compareTo(other: Confidence): Int = value.compareTo(other.value)

    enum class Band { GUESS, LOW, MEDIUM, HIGH, CERTAIN }

    companion object {
        const val MIN_ACTIONABLE = 0.5f

        val NONE = Confidence(0f)
        val GUESS = Confidence(0.2f)
        val LOW = Confidence(0.4f)
        val MEDIUM = Confidence(0.6f)
        val HIGH = Confidence(0.8f)
        val CERTAIN = Confidence(1f)

        fun of(value: Float) = Confidence(value.coerceIn(0f, 1f))
    }
}
