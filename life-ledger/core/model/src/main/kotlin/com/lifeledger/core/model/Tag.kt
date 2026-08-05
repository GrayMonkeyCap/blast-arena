package com.lifeledger.core.model

/** A free-form user label that can be attached to any transaction or timeline event. */
data class Tag(
    val id: Long = 0,
    val name: String,
    val colorSeed: Int = 0,
    val usageCount: Int = 0,
)
