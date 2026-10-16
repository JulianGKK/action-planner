package org.pag.prototype

import android.app.Instrumentation
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.pag.domain.*
import org.pag.prototype.product.*

/** Synthetic UI only, separate package, never opens product storage. */
class P10c2TextProbe:Instrumentation() {
    private var font=1f
    override fun onCreate(args:Bundle?){super.onCreate(args);font=args?.getString("font")?.toFloat() ?: 1f;start()}
    private fun waitFor(test:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+15000
        while(SystemClock.elapsedRealtime()<end){var passed=false;runOnMainSync{passed=test()};if(passed)return;SystemClock.sleep(80)}
        error("Synthetic timeout")
    }
    private fun click(label:String) {
        fun find(node:AccessibilityNodeInfo?):Boolean {
            if(node==null || !node.refresh())return false
            if(node.text?.toString()?.trim()==label) {
                var target:AccessibilityNodeInfo=node
                while(!target.isClickable)target=target.parent ?: break
                if(target.isEnabled && target.isClickable) {
                    val bounds=android.graphics.Rect();target.getBoundsInScreen(bounds)
                    check(bounds.height()>=48*targetContext.resources.displayMetrics.density)
                    return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
            repeat(node.childCount){if(find(node.getChild(it)))return true};return false
        }
        repeat(25){if(find(uiAutomation.rootInActiveWindow))return;SystemClock.sleep(100)}
        error("Synthetic control unavailable")
    }
    override fun onStart() {
        var phase="isolation";val report=Bundle();var activity:MainActivity?=null
        try {
            check(targetContext.packageName=="org.pag.text.p10c2probe")
            var snapshot=SessionSnapshot(SessionState.READY,1)
            val action=Action("a","synthetic","synthetic",1,2,0,ActionStatus.ACTIVE)
            val asset=DocumentAsset("t","a","synthetic",DocumentFormat.TEXT,70000,1)
            val pages=listOf("P10C2_SYNTHETIC_NEVER_PERSIST 🦋 e\u0301\r\nhttps://example.invalid\t<b>literal</b>","SECOND_PAGE 🦋\n".repeat(1200),"\n".repeat(32768))
            val callbacks=mutableListOf<Pair<Int,(DocumentResult<DocumentTextPage>)->Unit>>()
            val requests=mutableListOf<DocumentTextRequest>()
            var interactions=0
            val repository=object:DocumentRepository {
                override fun summary(actionId:String,publish:(DocumentResult<DocumentSummary>)->Unit)=publish(DocumentResult.Value(DocumentSummary(1)))
                override fun list(actionId:String,publish:(DocumentResult<List<DocumentAsset>>)->Unit)=publish(DocumentResult.Value(listOf(asset)))
                override fun readText(actionId:String,expected:DocumentAsset,page:Int,request:DocumentTextRequest,publish:(DocumentResult<DocumentTextPage>)->Unit){callbacks+=page to publish;requests+=request}
                override fun importDocument(actionId:String,name:String,format:DocumentFormat,source:DocumentSource,publish:(DocumentResult<Unit>)->Unit)=error("No import")
                override fun deleteDocument(actionId:String,expected:DocumentAsset,publish:(DocumentResult<Unit>)->Unit)=error("No delete")
            }
            val model=DocumentModel({repository},{null},{snapshot},{action},{interactions++},{},{})
            fun complete() {
                val read=callbacks.last();val chars=pages[read.first].toCharArray()
                runOnMainSync{read.second(DocumentResult.Value(DocumentTextPage(read.first,3,chars)))}
                check(chars.all{it=='\u0000'});waitForIdleSync()
            }
            phase="compose-first-page"
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            runOnMainSync {
                model.enter();model.viewText(asset)
                activity!!.setContent {val density=LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density,font)) {
                        MaterialTheme {ProductInputScope(Unit) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                if(model.textViewed!=null)DocumentTextPane(model)
                            }
                        }}
                    }
                }
            }
            complete();waitFor{model.text==pages[0]};val before=interactions
            phase="plaintext-no-link-or-copy"
            fun inspect(node:AccessibilityNodeInfo?):Boolean {
                if(node==null || !node.refresh())return false
                var found=false
                if(node.text?.toString()?.contains("https://example.invalid")==true) {
                    phase="plaintext-action-click"
                    check(!node.isClickable)
                    phase="plaintext-action-copy"
                    check(node.actionList.none{it.id==AccessibilityNodeInfo.ACTION_COPY})
                    found=true
                }
                repeat(node.childCount){if(inspect(node.getChild(it)))found=true}
                return found
            }
            phase="plaintext-find-label"
            var found=false
            repeat(25) {if(!found){found=inspect(uiAutomation.rootInActiveWindow);if(!found)SystemClock.sleep(100)}}
            check(found)
            runOnMainSync{model.textFrame()};check(interactions==before)
            phase="next-page-button"
            click("Seite weiter ›");waitFor{callbacks.last().first==1};check(model.text==null);complete();check(model.text==pages[1])
            phase="previous-page-button"
            click("‹ Seite zurück");waitFor{callbacks.last().first==0};complete();check(model.text==pages[0])
            phase="newline-heavy-page"
            runOnMainSync{model.nextTextPage()};complete();runOnMainSync{model.nextTextPage()};complete();check(model.text==pages[2])
            phase="lock-visible-text"
            runOnMainSync{snapshot=SessionSnapshot(SessionState.LOCKED,2);model.clear()};waitForIdleSync()
            check(model.text==null && model.textViewed==null);check(!requests.last().active)
            val late="late synthetic".toCharArray()
            runOnMainSync{callbacks.last().second(DocumentResult.Value(DocumentTextPage(2,3,late)))}
            check(late.all{it=='\u0000'});check(model.text==null)
            runOnMainSync{snapshot=SessionSnapshot(SessionState.READY,3);model.enter()};check(model.textViewed==null)
            phase="no-plaintext-persistence"
            val marker="P10C2_SYNTHETIC_NEVER_PERSIST"
            for(root in listOf(targetContext.filesDir,targetContext.cacheDir,targetContext.noBackupFilesDir))for(file in root.walkTopDown().filter{it.isFile}) {
                file.inputStream().use {stream->val buffer=ByteArray(1024);var tail=""
                    while(true){val n=stream.read(buffer);if(n<0)break;val window=tail+String(buffer,0,n,Charsets.ISO_8859_1);check(!window.contains(marker));tail=window.takeLast(marker.length-1)}
                    buffer.fill(0)
                }
            }
            report.putString("result","PASS");finish(Activity.RESULT_OK,report)
        }catch(_:Throwable){report.putString("result","FAIL");report.putString("phase",phase);finish(Activity.RESULT_CANCELED,report)}
        finally {activity?.let{runOnMainSync{it.finish()}}}
    }
}
