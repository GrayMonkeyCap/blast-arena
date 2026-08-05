package com.lifeledger.domain.nlq

import kotlinx.coroutines.flow.Flow

/**
 * Plug point for a future on-device language model.
 *
 * Nothing in the shipped app implements this. It exists so that adding a local model is a
 * matter of writing one class and one Hilt binding rather than reshaping the app:
 *
 *  - [ModelBackedQueryInterpreter] (future) would implement [QueryInterpreter] on top of
 *    this, constrained to emitting a [com.lifeledger.core.model.TransactionQuery]; the
 *    model never sees the answer and never produces figures.
 *  - Weights are loaded from a user-chosen file on device. There is deliberately no
 *    download path: the app has no INTERNET permission, and adding one would break the
 *    guarantee the whole product rests on.
 *
 * The API is streaming-first because any model small enough to run on a phone is slow
 * enough that the UI must show partial output.
 */
interface LocalLanguageModel {

    val id: String

    /** Where the weights live on device, and whether they are currently loaded. */
    suspend fun status(): ModelStatus

    suspend fun load(): Result<Unit>

    suspend fun unload()

    /**
     * Generates from [prompt]. Implementations must honour [GenerationConfig.maxTokens]
     * and must be cancellable — a user leaving the screen has to stop the work.
     */
    fun generate(prompt: String, config: GenerationConfig = GenerationConfig()): Flow<String>

    /** Optional embedding support, for a future semantic search over merchants and notes. */
    suspend fun embed(text: String): FloatArray? = null
}

data class GenerationConfig(
    val maxTokens: Int = 256,
    val temperature: Float = 0.2f,
    val topP: Float = 0.9f,
    /** When set, the model must emit only text matching this grammar (e.g. JSON schema). */
    val grammar: String? = null,
)

sealed interface ModelStatus {
    data object NotInstalled : ModelStatus
    data class Installed(val path: String, val sizeBytes: Long) : ModelStatus
    data class Loaded(val path: String, val contextTokens: Int) : ModelStatus
    data class Error(val message: String) : ModelStatus
}
