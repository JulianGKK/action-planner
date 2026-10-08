package org.pag.prototype

import android.app.Instrumentation
import android.os.Bundle
import java.security.KeyStore

/** Read-only, explicitly invoked I2 probe running under the target application's UID. */
class AliasInventoryProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        try {
            val store = KeyStore.getInstance("AndroidKeyStore")
            store.load(null)
            val result = listOf(
                "B15" to "pag.prototype.b15.storage.v1",
                "B16" to "pag.prototype.b16.storage.v1",
                "B3" to "pag.prototype.b3.file.v1"
            ).joinToString("\n") { (label, alias) ->
                "$label: ${if (store.containsAlias(alias)) "PRESENT" else "ABSENT"}"
            }
            finish(-1, Bundle().apply { putString(REPORT_KEY_STREAMRESULT, "\n$result\n") })
        } catch (_: Exception) {
            // No exception payload, alias enumeration, key access or repair on failure.
            finish(0, Bundle())
        }
    }
}
