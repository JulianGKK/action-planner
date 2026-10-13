package org.pag.prototype

/** Composition boundary: only the selected factory is invoked, never a warm product runtime. */
internal inline fun launchAppMode(demoOnly: Boolean, demo: () -> Unit, product: () -> Unit) {
    if (demoOnly) demo() else product()
}
