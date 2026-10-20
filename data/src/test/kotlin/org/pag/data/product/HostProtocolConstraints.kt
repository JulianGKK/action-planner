package org.pag.data.product

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking

/** Execute the ACTUAL production trigger installer on host SQLite. This bridge supports only
 * the installer's prepare/step/close API; DAO reads/writes use real SQL separately. No lifecycle. */
internal suspend fun SqliteHostConnection.installHostProtocolConstraints() {
    val connection=Proxy.newProxyInstance(SQLiteConnection::class.java.classLoader,arrayOf(SQLiteConnection::class.java)){_,method,args->
        when(method.name){
            "prepare"->{
                val text=args!![0] as String
                Proxy.newProxyInstance(SQLiteStatement::class.java.classLoader,arrayOf(SQLiteStatement::class.java)){_,m,_->
                    when(m.name){"step"->{runBlocking{execute(text)};false};"close"->null;else->error("Unsupported installer statement")}
                }
            }
            "close"->null
            else->error("Unsupported installer connection")
        }
    } as SQLiteConnection
    installProtocolConstraints(connection)
}
