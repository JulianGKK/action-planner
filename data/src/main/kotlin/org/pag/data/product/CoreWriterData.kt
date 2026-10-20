package org.pag.data.product

/** Internal persistence seam for real SQLite host proofs; production uses the SAME Room DAOs.
 * No transaction, authority, key or lifecycle ownership is delegated to this port.
 */
internal interface CoreWriterData {
    val actions: ActionDao
    val persons: PersonDao
    val operations: OperationsDao
    val tasks: TaskDao
}
internal class RoomCoreWriterData(private val db: ActionDatabase):CoreWriterData {
    override val actions get()=db.actions()
    override val persons get()=db.persons()
    override val operations get()=db.operations()
    override val tasks get()=db.tasks()
}
