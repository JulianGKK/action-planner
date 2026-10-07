package org.pag.security

/** Fixed stage IDs only. Never preserve platform messages, paths or throwable causes. */
inline fun <T> wrappingStep(step: WrappingFailure, work: () -> T): T = try { work() }
catch (failure: WrappingException) { throw failure }
catch (_: android.security.keystore.KeyPermanentlyInvalidatedException) {
    throw WrappingException(WrappingFailure.KEY_INVALIDATED)
}
catch (_: Exception) { throw WrappingException(step) }

/** Shared with deterministic tests; unwrap has no route to initialization. */
object WrappingPreparation {
    fun <T> run(mode: KeyExperiment, state: () -> BootstrapState,
        initialize: () -> Unit, prepare: () -> T): T {
        val inventory = wrappingStep(WrappingFailure.INVENTORY, state)
        when (mode) {
            KeyExperiment.STORAGE_INITIALIZE -> when (inventory) {
                BootstrapState.EMPTY -> wrappingStep(WrappingFailure.INVENTORY, initialize)
                BootstrapState.COMPLETE -> throw WrappingException(WrappingFailure.NOT_EMPTY)
                BootstrapState.BLOCKED -> throw WrappingException(WrappingFailure.INVENTORY)
            }
            KeyExperiment.STORAGE_UNWRAP -> if (inventory != BootstrapState.COMPLETE)
                throw WrappingException(WrappingFailure.INVENTORY)
            else -> throw WrappingException(WrappingFailure.INVENTORY)
        }
        return wrappingStep(WrappingFailure.WRAPPED_READ, prepare)
    }
}
