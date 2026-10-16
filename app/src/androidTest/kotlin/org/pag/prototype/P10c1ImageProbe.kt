package org.pag.prototype

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.pag.domain.*
import org.pag.prototype.product.*
import java.io.ByteArrayOutputStream

/** Synthetic RAM-only decoder/UI test. Requires a separate test application package. */
class P10c1ImageProbe:Instrumentation() {
    override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);start()}
    override fun onStart() {
        var phase="isolated-package";val report=Bundle();var activity:MainActivity?=null
        try {
            check(targetContext.packageName=="org.pag.image.p10c1probe")
            fun fixture(format:Bitmap.CompressFormat,width:Int,height:Int):ByteArray {
                val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                return try {bitmap.eraseColor(Color.CYAN);ByteArrayOutputStream().use {bitmap.compress(format,95,it);it.toByteArray()}}
                finally {bitmap.recycle()}
            }
            phase="jpeg-png-decoder"
            for((compression,format) in listOf(Bitmap.CompressFormat.JPEG to DocumentFormat.JPEG,Bitmap.CompressFormat.PNG to DocumentFormat.PNG)) {
                val bytes=fixture(compression,320,160)
                try {
                    val image=AndroidDocumentImageDecoder.decode(bytes,format){} as BitmapDocumentImage
                    check(image.bitmap!!.width==320 && image.bitmap!!.height==160);image.close();check(image.bitmap==null)
                } finally {bytes.fill(0)}
            }
            phase="exif-orientation"
            val jpeg=fixture(Bitmap.CompressFormat.JPEG,320,160)
            // Test-only minimal EXIF orientation 6 (90 degrees). Original body unmodified.
            val exif=byteArrayOf(-1,-31,0,34,69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0)
            val oriented=jpeg.copyOfRange(0,2)+exif+jpeg.copyOfRange(2,jpeg.size)
            try {
                val image=AndroidDocumentImageDecoder.decode(oriented,DocumentFormat.JPEG){} as BitmapDocumentImage
                check(image.bitmap!!.width==160 && image.bitmap!!.height==320);image.close()
            } finally {jpeg.fill(0);oriented.fill(0);exif.fill(0)}
            phase="large-downsample"
            val large=fixture(Bitmap.CompressFormat.JPEG,4000,3000)
            try {
                val image=AndroidDocumentImageDecoder.decode(large,DocumentFormat.JPEG){} as BitmapDocumentImage
                check(image.bitmap!!.width.toLong()*image.bitmap!!.height<=ImageDecodeLimits.PIXELS)
                check(image.bitmap!!.allocationByteCount<=ImageDecodeLimits.BITMAP_BYTES);image.close()
            } finally {large.fill(0)}
            phase="panorama-downsample"
            val panorama=fixture(Bitmap.CompressFormat.PNG,8192,16)
            try {
                val image=AndroidDocumentImageDecoder.decode(panorama,DocumentFormat.PNG){} as BitmapDocumentImage
                check(image.bitmap!!.width<=ImageDecodeLimits.SIDE && image.bitmap!!.height<=ImageDecodeLimits.SIDE)
                image.close()
            } finally {panorama.fill(0)}
            phase="decoder-revoke-and-error"
            val png=fixture(Bitmap.CompressFormat.PNG,64,32)
            try {
                var denied=false
                try {AndroidDocumentImageDecoder.decode(png,DocumentFormat.PNG){throw IllegalStateException()}}catch(_:IllegalStateException){denied=true}
                check(denied)
                denied=false
                try {AndroidDocumentImageDecoder.decode(byteArrayOf(1,2,3),DocumentFormat.PNG){}}catch(_:DocumentImageUnsupported){denied=true}
                check(denied)
                phase="repeated-open-close"
                val repeated=fixture(Bitmap.CompressFormat.PNG,1024,1024)
                fun allocated():Long=Runtime.getRuntime().let {it.totalMemory()-it.freeMemory()}+android.os.Debug.getNativeHeapAllocatedSize()
                System.gc();android.os.SystemClock.sleep(100)
                val before=allocated()
                try {
                    repeat(20) {
                        phase="repeat-decode"
                        val image=AndroidDocumentImageDecoder.decode(repeated,DocumentFormat.PNG){} as BitmapDocumentImage
                        phase="repeat-close"
                    val owned=image.bitmap!!;image.close();check(image.bitmap==null && owned.isRecycled)
                    }
                    System.gc();android.os.SystemClock.sleep(300)
                    phase="repeat-memory-growth"
                    check(allocated()-before<=32L*1024*1024) // Bounded trend check, not a physical erasure claim.
                } finally {repeated.fill(0)}
                phase="compose-image-and-cleanup"
                val live=AndroidDocumentImageDecoder.decode(png,DocumentFormat.PNG){} as BitmapDocumentImage
                var snapshot=SessionSnapshot(SessionState.READY,1)
                val action=Action("a","synthetic","synthetic",1,2,0,ActionStatus.ACTIVE)
                val asset=DocumentAsset("d","a","synthetic",DocumentFormat.PNG,png.size.toLong(),1)
                var deliver:((DocumentResult<DocumentImage>)->Unit)?=null
                val repository=object:DocumentRepository {
                    override fun summary(actionId:String,publish:(DocumentResult<DocumentSummary>)->Unit)=publish(DocumentResult.Value(DocumentSummary(1)))
                    override fun list(actionId:String,publish:(DocumentResult<List<DocumentAsset>>)->Unit)=publish(DocumentResult.Value(listOf(asset)))
                    override fun readImage(actionId:String,expected:DocumentAsset,request:DocumentImageRequest,decoder:DocumentImageDecoder,publish:(DocumentResult<DocumentImage>)->Unit){deliver=publish}
                    override fun importDocument(actionId:String,name:String,format:DocumentFormat,source:DocumentSource,publish:(DocumentResult<Unit>)->Unit)=error("No import")
                    override fun deleteDocument(actionId:String,expected:DocumentAsset,publish:(DocumentResult<Unit>)->Unit)=error("No delete")
                }
                val documents=DocumentModel({repository},{null},{snapshot},{action},{},{},{})
                activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
                runOnMainSync {
                    documents.enter();documents.viewImage(asset);deliver!!(DocumentResult.Value(live))
                    activity!!.setContent {MaterialTheme {
                        (documents.image as? BitmapDocumentImage)?.forDisplay()?.let {Image(it.asImageBitmap(),"Synthetisches Testbild",Modifier.fillMaxWidth().height(300.dp),contentScale=ContentScale.Fit)}
                    }}
                }
                waitForIdleSync()
                runOnMainSync {snapshot=SessionSnapshot(SessionState.LOCKED,2);documents.clear()}
                waitForIdleSync();check(live.bitmap==null);check(documents.image==null && documents.viewed==null)
                val late=AndroidDocumentImageDecoder.decode(png,DocumentFormat.PNG){} as BitmapDocumentImage
                runOnMainSync {deliver!!(DocumentResult.Value(late))};check(late.bitmap==null)
                runOnMainSync {activity!!.setContent {}}
            } finally {png.fill(0)}
            phase="no-plaintext-files"
            for(root in listOf(targetContext.filesDir,targetContext.cacheDir,targetContext.noBackupFilesDir)) {
                root.walkTopDown().filter {it.isFile}.forEach {file->file.inputStream().use {
                    val prefix=ByteArray(8);val n=it.read(prefix)
                    check(!(n>=3 && prefix[0]==(-1).toByte() && prefix[1]==(-40).toByte() && prefix[2]==(-1).toByte()))
                    check(!(n>=8 && prefix.contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))))
                }}
            }
            report.putString("result","PASS");finish(android.app.Activity.RESULT_OK,report)
        } catch(_:Throwable) {report.putString("result","FAIL");report.putString("phase",phase);finish(android.app.Activity.RESULT_CANCELED,report)}
        finally {activity?.let {a->runOnMainSync {a.finish()}}}
    }
}
