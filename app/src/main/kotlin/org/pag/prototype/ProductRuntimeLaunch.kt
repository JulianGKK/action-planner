package org.pag.prototype

import android.app.Activity
import org.pag.data.product.ProductRuntime
import java.util.concurrent.Executor

/** Variant-local composition hook. Release and ordinary variants package no providers. */
interface ProductRuntimeLaunchProvider {
    fun create(activity: Activity, worker: Executor, main: Executor, foreground: () -> Boolean): ProductRuntimeLaunch
}

data class ProductRuntimeLaunch(
    val runtime: ProductRuntime,
    val close: () -> Unit = { runtime.close() },
    val testAuthorityBanner: Boolean = false,
    val testHandle: Any? = null
)
