package org.pag.data.product

import android.content.Context
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import org.pag.domain.*
import org.pag.security.*
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

class ProductLeadershipRepository(app:Context,private val access:StorageAccess,private val inventory:ProductInventory,
    private val owner:()->SessionDbKeyOwner?,private val worker:Executor,private val main:Executor,
    private val clock:MonotonicTime,private val device:LeadershipDeviceKey=AndroidLeadershipDeviceKey(),
    internal val afterSensitiveWrite:()->Unit={}):LeadershipRepository {
    private val database=ActionDatabaseAccess(app.applicationContext)
    private val active=AtomicReference<LeadershipGrant?>()
    private val request=AtomicLong()
    private val pendingCleanups=java.util.concurrent.ConcurrentHashMap.newKeySet<Runnable>()
    override fun revoke(){request.incrementAndGet();active.getAndSet(null)?.cancel();pendingCleanups.toList().forEach{it.run()}}
    private fun check(c:StorageContext,point:AccessPoint=AccessPoint.STEP){if(c.scope!=AccessScope.READY || !c.check(point))throw SensitiveProblem(SensitiveFailure.DENIED)}
    private suspend fun parent(db:ActionDatabase,c:StorageContext,actionId:String,personId:String?=null,write:Boolean=false):Action {
        check(c)
        val action=db.actions().get(actionId)?.model() ?: throw SensitiveProblem(SensitiveFailure.DENIED)
        if(!(if(write)LeadershipRules.mayWrite(action.status) else LeadershipRules.mayRead(action.status)))throw SensitiveProblem(SensitiveFailure.DENIED)
        if(personId!=null){check(c);db.persons().get(actionId,personId)?.model() ?: throw SensitiveProblem(SensitiveFailure.DENIED)}
        return action
    }
    private fun bounded(c:StorageContext,allowed:()->Boolean):StorageContext=object:StorageContext {
        override val generation=c.generation
        override val scope=c.scope
        override fun check(point:AccessPoint)=c.check(point)&&allowed()
        override fun publish(result:()->Unit):Boolean { if(!allowed())return false; var delivered=false; c.publish{if(allowed()){result();delivered=true}}; return delivered }
        override fun <T> admitKey(validUntilMillis:Long,acquire:(()->Boolean)->T?):T? =
            c.admitKey(validUntilMillis){authorize->acquire{authorize() && allowed()}}
    }
    private fun alias(actionId:String)="org.pag.product.leadership.aes.v1.$actionId"
    private fun rootAad(row:LeadershipRow)=SensitiveCodec.context("P3b-root",row.actionId,row.epoch,row.cryptoVersion.toString(),row.algorithm,row.kdfVersion.toString(),row.memoryKiB.toString(),row.iterations.toString(),row.parallelism.toString(),java.util.Base64.getEncoder().encodeToString(row.salt))
    private fun areaAad(row:LeadershipRow,personId:String,area:SensitiveArea,revision:Long)=SensitiveCodec.context("P3b-area",row.actionId,personId,area.name,row.cryptoVersion.toString(),row.epoch,revision.toString())
    private fun keyAad(row:LeadershipRow,personId:String,area:SensitiveArea)=SensitiveCodec.context("P3b-DEK",row.actionId,personId,area.name,row.cryptoVersion.toString(),row.epoch)
    private fun validate(row:LeadershipRow){
        if(row.state!="READY" || row.cryptoVersion!=1 || row.epoch.isBlank() || row.salt.size!=16 || row.envelope.size!=88)throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
        LeadershipKdfParameters(row.algorithm,row.kdfVersion,row.memoryKiB,row.iterations,row.parallelism).validate()
    }
    private fun validateArea(row:SensitiveRow,actionId:String) {
        if(row.actionId!=actionId || row.personId.isBlank() || row.cryptoVersion!=1 || row.revision<=0 ||
            row.keyEnvelope.size!=60 || row.payload.size<33 ||
            SensitiveArea.entries.none{it.name==row.area})throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
    }
    private fun classification(row:LeadershipRow?,rows:List<SensitiveRow>,present:Boolean,usable:Boolean):SensitiveState {
        if(row==null)return if(rows.isEmpty()&&!present)SensitiveState.UNINITIALIZED else SensitiveState.PARTIAL_OR_INVALID
        return try{validate(row);if(!present||!usable)throw SensitiveProblem(SensitiveFailure.UNAVAILABLE);rows.forEach{validateArea(it,row.actionId)};SensitiveState.READY}
        catch(_:Exception){SensitiveState.PARTIAL_OR_INVALID}
    }
    private data class SensitiveSnapshot(val state:SensitiveState,val root:LeadershipRow?,val rows:List<SensitiveRow>,val present:Boolean,val usable:Boolean)
    /** Current transaction view, never a cached UI inventory or grant-derived authority. */
    private suspend fun sensitiveSnapshot(db:ActionDatabase,c:StorageContext,actionId:String):SensitiveSnapshot {
        check(c);val row=db.leadership().get(actionId)
        check(c);val rows=db.leadership().all(actionId)
        check(c);val present=device.present(alias(actionId))
        check(c);val usable=present && device.usable(alias(actionId))
        check(c)
        val state=if(row!=null && row.actionId!=actionId)SensitiveState.PARTIAL_OR_INVALID else classification(row,rows,present,usable)
        return SensitiveSnapshot(state,row,rows,present,usable)
    }
    private suspend fun requireSensitiveReady(db:ActionDatabase,c:StorageContext,actionId:String):SensitiveSnapshot =
        sensitiveSnapshot(db,c,actionId).also { if(it.state!=SensitiveState.READY)throw SensitiveProblem(SensitiveFailure.UNAVAILABLE) }
    private fun sameRoot(a:LeadershipRow,b:LeadershipRow)=
        a.copy(salt=b.salt,envelope=b.envelope)==b && a.salt.contentEquals(b.salt) && a.envelope.contentEquals(b.envelope)
    private fun requireSelected(row:SensitiveRow?,g:LeadershipGrant) {
        if(row==null){if(g.revision!=0L || g.expectedPayload!=null)throw SensitiveProblem(SensitiveFailure.CONFLICT);return}
        validateArea(row,g.actionId)
        if(row.personId!=g.personId || row.area!=g.area.name || row.revision!=g.revision ||
            !row.keyEnvelope.contentEquals(g.keyEnvelope) || g.expectedPayload==null ||
            !row.payload.contentEquals(g.expectedPayload))throw SensitiveProblem(SensitiveFailure.CONFLICT)
    }
    private fun <T> db(c:StorageContext,work:suspend (ActionDatabase)->T):T {
        check(c)
        if(inventory.state()!=ProductStorageState.READY_TO_OPEN)throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
        return (owner() ?: throw SensitiveProblem(SensitiveFailure.DENIED)).withDbKey(c){key->database.use(inventory.database,key,c,work=work)}
    }
    private fun <T> run(publish:(SensitiveResult<T>)->Unit,cleanup:()->Unit={},publicationAllowed:()->Boolean={true},work:(StorageContext)->T){
        val cleaned=java.util.concurrent.atomic.AtomicBoolean(false)
        lateinit var release:Runnable
        release=Runnable{if(cleaned.compareAndSet(false,true)){cleanup();pendingCleanups.remove(release)}}
        pendingCleanups.add(release)
        val c=access.acquire(AccessScope.READY)
        if(c==null){release.run();return}
        try { worker.execute {
            var result:SensitiveResult<T>
            try {check(c);result=SensitiveResult.Value(work(c))}
            catch(e:SensitiveProblem){result=SensitiveResult.Rejected(e.reason)}
            catch(_:Exception){result=SensitiveResult.Rejected(SensitiveFailure.UNAVAILABLE)}
            finally {release.run()}
            try { main.execute {if(!publicationAllowed() || (result is SensitiveResult.Value && result.value is SensitiveOpened && !(result.value as SensitiveOpened).grant.valid()) || !c.publish{publish(result)}){(result as? SensitiveResult.Value)?.value.let{if(it is SensitiveOpened)it.grant.cancel()}}} }
            catch(_:java.util.concurrent.RejectedExecutionException){(result as? SensitiveResult.Value)?.value.let{if(it is SensitiveOpened)it.grant.cancel()}}
        } } catch(_:java.util.concurrent.RejectedExecutionException){release.run()}
    }
    override fun inventory(actionId:String,personId:String,publish:(SensitiveResult<SensitiveInventory>)->Unit)=run(publish){c->
        val snapshot=db(c){d->d.withReadTransaction{parent(d,c,actionId,personId);sensitiveSnapshot(d,c,actionId)}}
        SensitiveInventory(snapshot.state,if(snapshot.state==SensitiveState.READY)snapshot.rows.filter{it.personId==personId}.map{SensitiveArea.valueOf(it.area)}.toSet() else emptySet())
    }
    override fun setup(actionId:String,passphrase:CharArray,confirmation:CharArray,publish:(SensitiveResult<Unit>)->Unit) {
        val ticket=request.get()
        run(publish,{passphrase.fill('\u0000');confirmation.fill('\u0000')}, {request.get()==ticket}){base->
        val c=bounded(base){request.get()==ticket}
        if(!LeadershipRules.validPassphrase(passphrase)||!passphrase.contentEquals(confirmation))throw SensitiveProblem(SensitiveFailure.INVALID_PASSPHRASE)
        val root=LeadershipCrypto.random(32);var derived:ByteArray?=null;var inner:ByteArray?=null
        try{
            check(c);if(device.present(alias(actionId)))throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
            val row=LeadershipRow(actionId,"RESERVED",UUID.randomUUID().toString(),1,"Argon2id",19,65536,3,1,LeadershipCrypto.random(16),byteArrayOf())
            db(c){d->d.withWriteTransaction{parent(d,c,actionId,write=true);check(c);if(sensitiveSnapshot(d,c,actionId).state!=SensitiveState.UNINITIALIZED)throw SensitiveProblem(SensitiveFailure.UNAVAILABLE);check(c);d.leadership().insert(row);check(c,AccessPoint.COMMIT)}}
            check(c);derived=LeadershipCrypto.derive(passphrase,row.salt,LeadershipKdfParameters())
            check(c);inner=LeadershipCrypto.encrypt(derived,root,rootAad(row))
            check(c);device.create(alias(actionId))
            check(c);val wrapped=device.encrypt(alias(actionId),inner,rootAad(row))
            val ready=row.copy(state="READY",envelope=wrapped)
            db(c){d->d.withWriteTransaction{parent(d,c,actionId,write=true);check(c);val pending=sensitiveSnapshot(d,c,actionId);if(pending.root==null || !sameRoot(pending.root,row) || pending.rows.isNotEmpty() || classification(ready,pending.rows,pending.present,pending.usable)!=SensitiveState.READY)throw SensitiveProblem(SensitiveFailure.UNAVAILABLE);check(c);d.leadership().update(ready);check(c,AccessPoint.COMMIT)}}
        }finally{root.fill(0);derived?.fill(0);inner?.fill(0)}
        }
    }
    private fun <T> root(c:StorageContext,row:LeadershipRow,password:CharArray,consume:(ByteArray)->T):T{
        validate(row);if(!LeadershipRules.validPassphrase(password))throw SensitiveProblem(SensitiveFailure.INVALID_PASSPHRASE)
        var inner:ByteArray?=null;var derived:ByteArray?=null;var raw:ByteArray?=null
        try{check(c);inner=device.decrypt(alias(row.actionId),row.envelope,rootAad(row));check(c);derived=LeadershipCrypto.derive(password,row.salt,LeadershipKdfParameters());check(c);raw=LeadershipCrypto.decrypt(derived,inner,rootAad(row));if(raw.size!=32)throw SensitiveProblem(SensitiveFailure.UNAVAILABLE);check(c);return consume(raw)}
        finally{inner?.fill(0);derived?.fill(0);raw?.fill(0)}
    }
    override fun open(actionId:String,personId:String,area:SensitiveArea,passphrase:CharArray,publish:(SensitiveResult<SensitiveOpened>)->Unit){
        revoke()
        val ticket=request.get()
        run(publish,{passphrase.fill('\u0000')}, {request.get()==ticket}){base->
            val c=bounded(base){request.get()==ticket}
            val parts=db(c){d->d.withReadTransaction{val a=parent(d,c,actionId,personId);check(c);val snapshot=requireSensitiveReady(d,c,actionId);Triple(a,snapshot.root!!,snapshot.rows.singleOrNull{it.personId==personId&&it.area==area.name})}}
            val row=parts.second;val stored=parts.third
            var dek:ByteArray?=null;var plaintext:ByteArray?=null;var grant:LeadershipGrant?=null
            try{
                val revision=stored?.revision?:0
                if(stored!=null&&(stored.cryptoVersion!=1||stored.revision<=0))throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
                val aad=areaAad(row,personId,area,revision)
                val envelope=root(c,row,passphrase){key->check(c);if(request.get()!=ticket)throw SensitiveProblem(SensitiveFailure.DENIED);if(stored==null){if(!LeadershipRules.mayWrite(parts.first.status))throw SensitiveProblem(SensitiveFailure.DENIED);dek=LeadershipCrypto.random(32);LeadershipCrypto.encrypt(key,dek!!,keyAad(row,personId,area))}else{dek=LeadershipCrypto.decrypt(key,stored.keyEnvelope,keyAad(row,personId,area));stored.keyEnvelope}}
                check(c);if(dek!!.size!=32)throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
                val data=if(stored==null)SensitiveCodec.empty(area)else{plaintext=LeadershipCrypto.decrypt(dek!!,stored.payload,aad);SensitiveCodec.decode(area,plaintext!!)}
                // KDF/decrypt happens outside Room; recheck a fresh transaction before issuing the grant.
                db(c){d->d.withReadTransaction{
                    parent(d,c,actionId,personId)
                    val current=requireSensitiveReady(d,c,actionId)
                    if(!sameRoot(current.root!!,row))throw SensitiveProblem(SensitiveFailure.CONFLICT)
                    val selected=current.rows.singleOrNull{it.personId==personId&&it.area==area.name}
                    if(selected?.revision!=stored?.revision || selected?.cryptoVersion!=stored?.cryptoVersion ||
                        (selected!=null && stored!=null && (!selected.keyEnvelope.contentEquals(stored.keyEnvelope)||!selected.payload.contentEquals(stored.payload))))throw SensitiveProblem(SensitiveFailure.CONFLICT)
                }}
                grant=LeadershipGrant(actionId,personId,area,c,clock,dek!!,row.epoch,revision,envelope,LeadershipRules.mayWrite(parts.first.status),stored?.payload?.copyOf(),row)
                check(c);if(request.get()!=ticket)throw SensitiveProblem(SensitiveFailure.DENIED);active.getAndSet(grant)?.cancel()
                SensitiveOpened(grant,data)
            }catch(e:Exception){grant?.cancel();throw e}finally{dek?.fill(0);plaintext?.fill(0)}
        }
    }
    override fun save(grant:SensitiveGrant,data:SensitiveData,publish:(SensitiveResult<Unit>)->Unit)=mutate(grant,publish){g,c->
        if(!g.writable||LeadershipRules.area(data)!=g.area)throw SensitiveProblem(SensitiveFailure.DENIED)
        val plain=SensitiveCodec.encode(data);var cipher:ByteArray?=null;
        try{
            // Only payload AAD changes with revision; the DEK envelope binds owner/area/epoch.
            val next=g.revision+1
            if(next<=0)throw SensitiveProblem(SensitiveFailure.CONFLICT)
            // Bind the key envelope to owner/area/epoch, not changing row revision; payload includes revision.
            cipher=g.consume{key->LeadershipCrypto.encrypt(key,plain,payloadAad(g,next))}
            db(c){d->d.withWriteTransaction{parent(d,c,g.actionId,g.personId,true);check(c);val snapshot=requireSensitiveReady(d,c,g.actionId);val r=snapshot.root!!;if(r.epoch!=g.epoch || g.expectedRoot==null || !sameRoot(r,g.expectedRoot))throw SensitiveProblem(SensitiveFailure.CONFLICT);check(c);val old=snapshot.rows.singleOrNull{it.personId==g.personId&&it.area==g.area.name};requireSelected(old,g);if(!g.valid())throw SensitiveProblem(SensitiveFailure.DENIED);val row=SensitiveRow(g.actionId,g.personId,g.area.name,1,next,g.keyEnvelope,cipher!!);check(c);if(!SensitiveCodec.hasData(data))d.leadership().removeArea(g.actionId,g.personId,g.area.name)else if(old==null)d.leadership().insertArea(row)else d.leadership().updateArea(row);afterSensitiveWrite();check(c,AccessPoint.COMMIT);if(!g.valid())throw SensitiveProblem(SensitiveFailure.DENIED)}}
        }finally{plain.fill(0);cipher?.fill(0)}
    }
    private fun payloadAad(g:LeadershipGrant,revision:Long)=SensitiveCodec.context("P3b-area",g.actionId,g.personId,g.area.name,"1",g.epoch,revision.toString())
    private fun mutate(grant:SensitiveGrant,publish:(SensitiveResult<Unit>)->Unit,work:(LeadershipGrant,StorageContext)->Unit) {
        val ticket=request.get()
        run(publish,publicationAllowed={request.get()==ticket && (grant as? LeadershipGrant)?.inLifetime()==true}){c->
        val g=grant as? LeadershipGrant?:throw SensitiveProblem(SensitiveFailure.DENIED)
        if(active.get()!==g||c.generation!=g.context.generation||!g.valid())throw SensitiveProblem(SensitiveFailure.DENIED)
        try{work(g,bounded(c){g.valid()})}finally{g.cancel();active.compareAndSet(g,null)}
    }
    }
    override fun deleteArea(grant:SensitiveGrant,publish:(SensitiveResult<Unit>)->Unit)=mutate(grant,publish){g,c->
        if(!g.writable)throw SensitiveProblem(SensitiveFailure.DENIED)
        db(c){d->d.withWriteTransaction{parent(d,c,g.actionId,g.personId,true);check(c);val snapshot=requireSensitiveReady(d,c,g.actionId);val r=snapshot.root!!;if(r.epoch!=g.epoch || g.expectedRoot==null || !sameRoot(r,g.expectedRoot))throw SensitiveProblem(SensitiveFailure.CONFLICT);check(c);val old=snapshot.rows.singleOrNull{it.personId==g.personId&&it.area==g.area.name};requireSelected(old,g);if(!g.valid())throw SensitiveProblem(SensitiveFailure.DENIED);check(c);d.leadership().removeArea(g.actionId,g.personId,g.area.name);check(c,AccessPoint.COMMIT);if(!g.valid())throw SensitiveProblem(SensitiveFailure.DENIED)}}
    }
    override fun deletePerson(actionId:String,personId:String,passphrase:CharArray,publish:(SensitiveResult<Unit>)->Unit)=run(publish,{passphrase.fill('\u0000')}){c->
        val row=db(c){d->d.withReadTransaction{parent(d,c,actionId,personId,true);requireSensitiveReady(d,c,actionId).root!!}}
        root(c,row,passphrase){}
        revoke()
        db(c){d->d.withWriteTransaction{parent(d,c,actionId,personId,true);check(c);if(!sameRoot(requireSensitiveReady(d,c,actionId).root!!,row))throw SensitiveProblem(SensitiveFailure.CONFLICT);check(c);d.leadership().removePerson(actionId,personId);check(c,AccessPoint.COMMIT)}}
    }
    override fun deleteAction(actionId:String,passphrase:CharArray,publish:(SensitiveResult<Unit>)->Unit)=run(publish,{passphrase.fill('\u0000')}){c->
        val row=db(c){d->d.withReadTransaction{
            parent(d,c,actionId,write=true);check(c)
            val row=d.leadership().get(actionId)?:throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
            // Explicit continuation of an already authorized delete intent is not a normal read/write.
            if(row.state=="DELETE_PENDING")row
            else requireSensitiveReady(d,c,actionId).root!!
        }}
        if(row.state!="DELETE_PENDING"){
            root(c,row,passphrase){}
            db(c){d->d.withWriteTransaction{parent(d,c,actionId,write=true);check(c);if(!sameRoot(requireSensitiveReady(d,c,actionId).root!!,row))throw SensitiveProblem(SensitiveFailure.CONFLICT);check(c);d.leadership().update(row.copy(state="DELETE_PENDING"));check(c,AccessPoint.COMMIT)}}
        }
        revoke();check(c);device.delete(alias(actionId));check(c)
        if(device.present(alias(actionId)))throw SensitiveProblem(SensitiveFailure.UNAVAILABLE)
        db(c){d->d.withWriteTransaction{parent(d,c,actionId,write=true);check(c);val r=d.leadership().get(actionId);if(r?.epoch!=row.epoch||r.state!="DELETE_PENDING")throw SensitiveProblem(SensitiveFailure.CONFLICT);check(c);d.leadership().removeAction(actionId);check(c);d.leadership().remove(actionId);check(c,AccessPoint.COMMIT)}}
    }
}
