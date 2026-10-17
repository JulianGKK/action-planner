package org.pag.data.product

import android.os.Bundle
import androidx.room3.withReadTransaction
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.*

/** CLASS B ANALYSIS ONLY. Fresh isolated synthetic databases, no rekey or product integration. */
class P11KeyFormatBenchmark {
    @Test fun compareDerivedAndRawRandomKeysWithAllPreflights() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext
        check(app.packageName=="org.pag.data.unlockprobe") // Before file/key work.
        val parent=app.noBackupFilesDir.canonicalFile
        val root=File(parent,"p11-key-analysis-${UUID.randomUUID()}").apply{check(mkdirs())}
        val random=ByteArray(32).also{SecureRandom().nextBytes(it)}
        // Zetetic's public raw-key syntax. Mutable encoding, no secret String or key logging.
        val raw=ByteArray(67).apply {
            this[0]=120;this[1]=39;this[66]=39
            val digits="0123456789abcdef"
            random.forEachIndexed{i,b->this[2+2*i]=digits[(b.toInt() ushr 4) and 15].code.toByte();this[3+2*i]=digits[b.toInt() and 15].code.toByte()}
        }
        val context=object:StorageContext {
            override val scope=AccessScope.MAINTENANCE
            override val generation=0L
            override fun check(point:AccessPoint)=true
            override fun publish(result:()->Unit)=false
        }
        try {
            val modes=listOf("DERIVED_FULL_OPERATION" to random,"RAW_FULL_OPERATION" to raw)
            val files=modes.indices.map{index->File(File(root,"mode-$index").apply{check(mkdirs())},"synthetic.db")}
            modes.forEachIndexed{index,(_,key)->ActionDatabaseAccess(app).use(files[index],key,context,true){db->
                db.withReadTransaction{assertTrue(db.actions().list().isEmpty())}
            }}
            repeat(22){iteration->modes.forEachIndexed{index,(name,key)->
                val completed=mutableListOf<ActionPreflightStep>();var closed=false
                val started=System.nanoTime()
                ActionDatabaseAccess(app,ActionPreflightHooks(completed={completed.add(it)},closed={closed=it}))
                    .use(files[index],key,context){db->db.withReadTransaction{assertTrue(db.actions().list().isEmpty())}}
                assertEquals(ActionPreflightStep.entries,completed);assertTrue(closed)
                instrumentation.sendStatus(0,Bundle().apply{putString("stream","P11,decision,$name,${iteration-2},${(System.nanoTime()-started)/1000000.0}\n")})
            }}
        }finally {
            random.fill(0);raw.fill(0)
            check(root.parentFile==parent && root.name.startsWith("p11-key-analysis-"));root.deleteRecursively()
        }
    }
}
