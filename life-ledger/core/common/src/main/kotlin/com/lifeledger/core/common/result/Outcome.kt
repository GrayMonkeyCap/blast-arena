package com.lifeledger.core.common.result

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * A three-state result wrapper used by every ViewModel-facing stream.
 *
 * Deliberately not `kotlin.Result`: the UI needs an explicit *loading* state, and errors
 * carry a user-facing message rather than a raw exception, because nothing about a
 * failure should ever leak a raw SMS body into a Snackbar.
 */
sealed interface Outcome<out T> {
    data object Loading : Outcome<Nothing>
    data class Success<T>(val data: T) : Outcome<T>
    data class Error(val message: String, val cause: Throwable? = null) : Outcome<Nothing>

    val dataOrNull: T? get() = (this as? Success)?.data
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Loading -> Outcome.Loading
    is Outcome.Error -> this
    is Outcome.Success -> Outcome.Success(transform(data))
}

/** Wraps a cold flow so subscribers see Loading first and never see a thrown exception. */
fun <T> Flow<T>.asOutcome(): Flow<Outcome<T>> = this
    .map<T, Outcome<T>> { Outcome.Success(it) }
    .onStart { emit(Outcome.Loading) }
    .catch { emit(Outcome.Error(it.message ?: "Something went wrong", it)) }

/** Runs [block], converting any throwable into [Outcome.Error]. */
inline fun <T> outcomeOf(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (t: Throwable) {
    Outcome.Error(t.message ?: "Something went wrong", t)
}
