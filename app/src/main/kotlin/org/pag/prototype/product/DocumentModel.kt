package org.pag.prototype.product

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.pag.domain.*

/** RAM-only metadata/forms. The exceptional picker source lives only in Activity RAM. */
class DocumentModel(private val repository: () -> DocumentRepository?, private val picker: () -> DocumentPicker?,
    private val session: () -> SessionSnapshot, private val action: () -> Action?, private val interaction: () -> Unit,
    private val refreshWorkspace: () -> Unit, private val feedback: (String) -> Unit,
    private val cameraAuthority: () -> Boolean = { true }) {
    private var pdfRenderer:DocumentPdfRenderer?=null
    internal fun bindPdfRenderer(renderer:DocumentPdfRenderer){pdfRenderer=renderer}
    internal var pdfViewed by mutableStateOf<DocumentAsset?>(null);private set
    internal var pdfPage by mutableStateOf<DocumentPdfPage?>(null);private set
    internal var pdfLoading by mutableStateOf(false);private set
    internal var pdfIndex by mutableStateOf(0);private set
    internal var pdfCount by mutableStateOf(0);private set
    private var pdfRequest:DocumentPdfRequest?=null
    private var pdfStarted=0L;private var pdfFrameReported=false
    internal fun pdfFrame(){if(!pdfFrameReported && available && cameraAuthority() && pdfPage!=null){pdfFrameReported=true;pdfRequest?.measured(DocumentPdfPhase.VISIBLE,pdfStarted)}}
    internal fun clearPdf(){val start=System.nanoTime();val request=pdfRequest;pdfRequest?.cancel();pdfRequest=null;pdfPage?.close();pdfPage=null;pdfViewed=null;pdfLoading=false;pdfIndex=0;pdfCount=0;request?.measured(DocumentPdfPhase.CLEANUP,start)}
    internal fun closePdf()=act{clearPdf();error=null}
    internal fun viewPdf(item:DocumentAsset)=act{
        if(!cameraAuthority() || item !in items.orEmpty() || item.format!=DocumentFormat.PDF || pdfRenderer==null)return@act
        clearViewers();camera.clear();error=null;pdfViewed=item;readPdfPage(0)
    }
    internal fun nextPdfPage()=act{if(!pdfLoading && pdfIndex+1<pdfCount)readPdfPage(pdfIndex+1)}
    internal fun previousPdfPage()=act{if(!pdfLoading && pdfIndex>0)readPdfPage(pdfIndex-1)}
    private fun readPdfPage(page:Int){
        if(!available || !cameraAuthority())return
        val item=pdfViewed ?: return;val renderer=pdfRenderer ?: return
        val changing=pdfCount>0
        pdfRequest?.cancel();pdfPage?.close();pdfPage=null;pdfLoading=true;pdfIndex=page;error=null
        val started=System.nanoTime();pdfStarted=started;pdfFrameReported=false;val attempt=DocumentPdfRequest(generation,::pdfTiming);pdfRequest=attempt;val e=epoch;val g=generation
        repository()!!.readPdf(boundId!!,item,page,attempt,renderer){result ->
            if(!attempt.active || pdfRequest!==attempt || !available || !cameraAuthority() || e!=epoch || g!=generation || pdfViewed!=item){
                (result as? DocumentResult.Value)?.value?.close();return@readPdf
            }
            pdfLoading=false
            attempt.measured(DocumentPdfPhase.PUBLICATION,started)
            if(changing)attempt.measured(DocumentPdfPhase.PAGE_CHANGE,started)
            when(result){
                is DocumentResult.Value -> if(result.value.index==page){pdfPage=result.value;pdfCount=result.value.pageCount}
                    else {result.value.close();error=DocumentFailure.PDF_UNSUPPORTED}
                is DocumentResult.Rejected -> error=result.reason
            }
        }
    }
    private var cameraImportTime: Long? = null
    internal var textViewed by mutableStateOf<DocumentAsset?>(null); private set
    internal var text by mutableStateOf<String?>(null); private set
    internal var textLoading by mutableStateOf(false); private set
    internal var textPageIndex by mutableStateOf(0); private set
    internal var textPageCount by mutableStateOf(0); private set
    private var textRequest:DocumentTextRequest?=null
    private var textStarted=0L
    private var textFrameReported=false
    internal fun clearText() {
        val started=System.nanoTime();val request=textRequest
        request?.cancel();textRequest=null;text=null;textViewed=null;textLoading=false;textPageIndex=0;textPageCount=0
        request?.measured(DocumentTextPhase.CLEANUP,started)
    }
    internal fun clearViewers() {clearImage();clearText();clearPdf()}
    internal fun closeText()=act {clearText();error=null}
    internal fun viewText(item:DocumentAsset)=act {
        if(!cameraAuthority() || item !in items.orEmpty() || item.format!=DocumentFormat.TEXT)return@act
        clearViewers();camera.clear();error=null;textViewed=item;readTextPage(0)
    }
    internal fun nextTextPage()=act {if(!textLoading && textPageIndex+1<textPageCount)readTextPage(textPageIndex+1)}
    internal fun previousTextPage()=act {if(!textLoading && textPageIndex>0)readTextPage(textPageIndex-1)}
    internal fun textFrame() {
        if(!textFrameReported && available && cameraAuthority() && text!=null) {
            textFrameReported=true;textRequest?.measured(DocumentTextPhase.VISIBLE,textStarted)
        }
    }
    private fun readTextPage(page:Int) {
        if(!available || !cameraAuthority())return
        val item=textViewed ?: return
        textRequest?.cancel();text=null;textLoading=true;textPageIndex=page;error=null
        textStarted=System.nanoTime();textFrameReported=false
        val started=textStarted
        val attempt=DocumentTextRequest(generation) {phase,ms ->
            if(org.pag.prototype.BuildConfig.DEBUG && android.util.Log.isLoggable("PaGTextTiming",android.util.Log.DEBUG))android.util.Log.i("PaGTextTiming","${phase.name} ms=$ms")
        };textRequest=attempt
        val e=epoch;val g=generation
        repository()!!.readText(boundId!!,item,page,attempt) {result ->
            if(!attempt.active || textRequest!==attempt || !available || !cameraAuthority() || e!=epoch || g!=generation || textViewed!=item) {
                (result as? DocumentResult.Value)?.value?.close();return@readText
            }
            textLoading=false
            when(result) {
                is DocumentResult.Value -> result.value.use {value ->
                    if(value.index!=page || value.pageCount !in 1..DocumentTextLimits.MAX_PAGES) {error=DocumentFailure.TEXT_UNSUPPORTED;return@use}
                    text=value.text();textPageCount=value.pageCount
                }
                is DocumentResult.Rejected -> error=result.reason
            }
            attempt.measured(DocumentTextPhase.PUBLICATION,started)
            if(page>0)attempt.measured(DocumentTextPhase.PAGE_CHANGE,started)
        }
    }
    internal var viewed by mutableStateOf<DocumentAsset?>(null); private set
    internal var image by mutableStateOf<DocumentImage?>(null); private set
    internal var imageLoading by mutableStateOf(false); private set
    private var imageRequest:DocumentImageRequest?=null
    internal fun clearImage() {
        val started=System.nanoTime()
        val request=imageRequest
        imageRequest?.cancel();imageRequest=null
        image?.close();image=null;viewed=null;imageLoading=false
        request?.measured(DocumentImagePhase.CLEANUP,started)
    }
    internal fun closeImage() = act {clearImage();error=null}
    internal fun viewImage(item:DocumentAsset) = act {
        if(!cameraAuthority() || item !in items.orEmpty() || item.format !in setOf(DocumentFormat.JPEG,DocumentFormat.PNG))return@act
        clearViewers();camera.clear();error=null
        viewed=item;imageLoading=true
        val started=System.nanoTime()
        val attempt=DocumentImageRequest(generation=generation) {phase,ms ->
            if(org.pag.prototype.BuildConfig.DEBUG) android.util.Log.i("PaGImageTiming","${phase.name} ms=$ms")
        };imageRequest=attempt
        val e=epoch;val g=generation
        repository()!!.readImage(boundId!!,item,attempt,AndroidDocumentImageDecoder) {result ->
            if(!attempt.active || imageRequest!==attempt || !available || !cameraAuthority() || e!=epoch || g!=generation) {
                (result as? DocumentResult.Value)?.value?.close();return@readImage
            }
            imageLoading=false
            attempt.measured(DocumentImagePhase.TOTAL,started)
            when(result) {
                is DocumentResult.Value -> {
                    image=result.value
                }
                is DocumentResult.Rejected -> error=result.reason
            }
        }
    }
    val cameraSupported get() = cameraPermission != null
    private var cameraPermission: CameraPermission? = null
    internal val camera = DocumentCameraModel({ available && cameraAuthority() }, interaction)
    fun bindCameraPermission(value: CameraPermission) { cameraPermission = value }
    fun takePhoto() = act {
        if (importing || confirmingDelete != null) return@act
        val permission = cameraPermission ?: return@act
        if (!permission.granted()) { camera.permissionMissing(); permission.request(); return@act }
        picker()?.cancel(); error=null; name=""; format=null; camera.start()
    }
    internal fun cancelCamera() = act { camera.clear();name="";format=null;error=null;cameraImportTime=null }
    internal fun retakeCamera() = act { camera.retake();name="";format=null;error=null }
    internal fun saveCamera() = act {
        if (!DocumentRules.validName(name)) { error=DocumentFailure.INVALID; return@act }
        val source=camera.sourceForSave() ?: return@act
        cameraImportTime=System.nanoTime()
        write("Dokument gespeichert.") { callback ->
            repository()!!.importDocument(boundId!!,name.trim(),DocumentFormat.JPEG,source,callback)
        }
    }
    var items by mutableStateOf<List<DocumentAsset>?>(null); private set
    var loading by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<DocumentFailure?>(null); private set
    var importing by mutableStateOf(false); private set
    var name by mutableStateOf(""); private set
    var format by mutableStateOf<DocumentFormat?>(null); private set
    var confirmingDelete by mutableStateOf<DocumentAsset?>(null); private set
    private var opened by mutableStateOf(false)
    private var boundId: String?=null
    private var boundStatus: ActionStatus?=null
    private var generation=-1L
    private var epoch=0L
    private var request=0L
    val available get() = opened && repository()!=null && session().state==SessionState.READY &&
        session().generation==generation && action()?.id==boundId && action()?.status==boundStatus &&
        action()?.let { DocumentRules.mayRead(it.status) }==true
    val canContinueImport get() = available && picker()?.available(boundId!!,generation)==true
    val selectionUnavailable get() = available && picker()?.unavailable(boundId!!)==true
    val canSave get() = importing && DocumentRules.validName(name) && format!=null && !busy
    fun clear() {
        clearViewers()
        camera.clear(); cameraImportTime=null
        epoch++; request++; opened=false; boundId=null; boundStatus=null; generation=-1
        items=null; loading=false; busy=false; error=null; importing=false; name=""; format=null; confirmingDelete=null
    }
    fun enter() {
        clear()
        val a=action() ?: return; val s=session()
        if (s.state!=SessionState.READY || !DocumentRules.mayRead(a.status) || repository()==null) return
        opened=true; boundId=a.id; boundStatus=a.status; generation=s.generation
        refresh()
    }
    private fun current(e: Long, r: Long) = e==epoch && r==request && available
    private fun act(work: () -> Unit) {
        if (!available || busy) return
        val e=epoch; val g=generation
        interaction()
        if (available && e==epoch && g==generation) work()
    }
    fun refresh() {
        if (!available || busy) return
        val e=epoch; val r=++request; loading=true; error=null
        repository()!!.list(boundId!!) { result ->
            if (!current(e,r)) return@list
            loading=false
            when(result) {
                is DocumentResult.Value -> {
                    if (result.value.any { it.actionId!=boundId || !DocumentRules.valid(it) }) { error=DocumentFailure.INVALID; return@list }
                    items=result.value.sortedWith(DocumentRules.order)
                    cameraImportTime?.let { cameraTime(CameraTime.IMPORT_LIST,it);cameraImportTime=null }
                }
                is DocumentResult.Rejected -> error=result.reason
            }
        }
    }
    fun chooseFile() = act { camera.clear(); picker()?.launch(boundId!!,generation) }
    fun continueImport() = act {
        if (!canContinueImport) return@act
        importing=true; name=""; format=null; error=null; confirmingDelete=null
    }
    fun changeName(value: String) = act { name=value }
    fun changeFormat(value: DocumentFormat) = act { format=value }
    fun cancelImport() = act { importing=false; name=""; format=null; error=null; picker()?.cancel() }
    fun save() = act {
        if (!canSave) { error=DocumentFailure.INVALID; return@act }
        // Consumed only on this explicit tap, never in a read/picker/session callback.
        val source=picker()?.consume(boundId!!,generation)
        if (source==null) { error=DocumentFailure.SOURCE_UNAVAILABLE; importing=false; name=""; format=null; return@act }
        write("Dokument gespeichert.") { callback -> repository()!!.importDocument(boundId!!,name.trim(),format!!,source,callback) }
    }
    fun requestDelete(item: DocumentAsset) = act { if (item in items.orEmpty()) { confirmingDelete=item; error=null } }
    fun cancelDelete() = act { confirmingDelete=null; error=null }
    fun confirmDelete() = act {
        val expected=confirmingDelete ?: return@act
        write("PaG-Kopie entfernt.") { callback -> repository()!!.deleteDocument(boundId!!,expected,callback) }
    }
    private fun write(message: String, work: ((DocumentResult<Unit>) -> Unit) -> Unit) {
        val e=epoch; val r=++request; busy=true; error=null
        work { result ->
            if (!current(e,r)) return@work
            busy=false
            camera.clear()
            when(result) {
                is DocumentResult.Value -> {
                    cameraImportTime?.let { cameraTime(CameraTime.IMPORT_COMMIT,it) }
                    importing=false; name=""; format=null; confirmingDelete=null
                    refresh(); refreshWorkspace(); feedback(message)
                }
                is DocumentResult.Rejected -> {
                    error=result.reason; cameraImportTime=null
                    // One attempt has been consumed; re-selection is always explicit.
                    if (importing) { importing=false; name=""; format=null }
                }
            }
        }
    }
}
