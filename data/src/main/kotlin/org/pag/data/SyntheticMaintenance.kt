package org.pag.data

import org.pag.domain.Maintenance
import org.pag.domain.Subscription

/** Only an injectable delayed completion. No DB, integrity or retention evidence. */
class SyntheticMaintenance(private val schedule: ((() -> Unit) -> Subscription)) : Maintenance {
    override fun run(complete: (Boolean) -> Unit): Subscription = schedule { complete(true) }
}
