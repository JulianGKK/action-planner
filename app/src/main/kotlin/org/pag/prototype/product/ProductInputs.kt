package org.pag.prototype.product

import android.view.ViewTreeObserver
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** UI-only state. Never an authorization flag or a retained credential. */
internal class HoldToReveal {
    var visible by mutableStateOf(false); private set
    fun press() { visible=true }
    fun end() { visible=false }
    fun move(inside:Boolean) { if(!inside) end() }
}
internal class FormInput(val dismiss:()->Unit) {
    var focused:Any?=null
    var bounds:Rect?=null
    fun focus(id:Any,rectangle:Rect?,active:Boolean) {
        if(active){focused=id;bounds=rectangle}
        else if(focused===id){focused=null;bounds=null}
    }
    fun finish(){dismiss();focused=null;bounds=null}
    fun backgroundTap(point:Offset,consumed:Boolean,dragged:Boolean){
        if(!consumed&&!dragged && focused!=null && bounds?.contains(point)!=true)finish()
    }
}
private val LocalFormInput=staticCompositionLocalOf<FormInput>{error("PRODUCT-FORM-SCOPE")}

/** Observes unconsumed taps in Final pass; never consumes input or interrupts scrolling. */
@Composable internal fun ProductInputScope(screen:Any?,content:@Composable ()->Unit) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val input=remember(focus,keyboard){FormInput{focus.clearFocus(force=true);keyboard?.hide()}}
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view,lifecycle,input) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE || event==Lifecycle.Event.ON_STOP)input.finish()}
        val window=ViewTreeObserver.OnWindowFocusChangeListener{focused->if(!focused)input.finish()}
        lifecycle.addObserver(observer);view.viewTreeObserver.addOnWindowFocusChangeListener(window)
        onDispose {lifecycle.removeObserver(observer);if(view.viewTreeObserver.isAlive)view.viewTreeObserver.removeOnWindowFocusChangeListener(window);input.finish()}
    }
    DisposableEffect(screen){onDispose{input.finish()}}
    var origin by remember {mutableStateOf(Offset.Zero)}
    CompositionLocalProvider(LocalFormInput provides input) {
        Box(Modifier.fillMaxSize().onGloballyPositioned{origin=it.positionInRoot()}.pointerInput(input){
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=true,pass=PointerEventPass.Final)
                var dragged=false;var consumed=false;var last=down
                do {
                    val event=awaitPointerEvent(PointerEventPass.Final)
                    val change=event.changes.firstOrNull{it.id==down.id} ?: break
                    dragged=dragged || (change.position-down.position).getDistance()>viewConfiguration.touchSlop
                    consumed=consumed || change.isConsumed
                    last=change
                } while(last.pressed)
                if(!last.pressed)input.backgroundTap(last.position+origin,consumed,dragged)
            }
        }){content()}
    }
}
@Composable internal fun FormButton(onClick:()->Unit,enabled:Boolean=true,content:@Composable RowScope.()->Unit) {
    val input=LocalFormInput.current
    Button(onClick={input.finish();onClick()},enabled=enabled,content=content)
}
@Composable internal fun FormTextButton(onClick:()->Unit,enabled:Boolean=true,content:@Composable RowScope.()->Unit) {
    val input=LocalFormInput.current
    TextButton(onClick={input.finish();onClick()},enabled=enabled,content=content)
}
@Composable internal fun FormOutlinedButton(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,content:@Composable RowScope.()->Unit) {
    val input=LocalFormInput.current
    OutlinedButton(onClick={input.finish();onClick()},modifier=modifier,enabled=enabled,content=content)
}
@Composable internal fun FormTextField(value:String,onValueChange:(String)->Unit,label:@Composable ()->Unit,
    modifier:Modifier=Modifier,enabled:Boolean=true,singleLine:Boolean=false,
    keyboardOptions:KeyboardOptions=KeyboardOptions.Default,imeAction:ImeAction=ImeAction.Done,
    visualTransformation:VisualTransformation=VisualTransformation.None,
    trailingIcon:(@Composable ()->Unit)?=null,supportingText:(@Composable ()->Unit)?=null,
    focusRequester:FocusRequester?=null,focusChanged:(Boolean)->Unit={}) {
    val input=LocalFormInput.current;val manager=LocalFocusManager.current
    val id=remember{Any()};var rectangle:Rect? by remember{mutableStateOf(null)};var active by remember{mutableStateOf(false)}
    DisposableEffect(id){onDispose{if(input.focused===id)input.finish()}}
    val request=if(focusRequester==null)Modifier else Modifier.focusRequester(focusRequester)
    OutlinedTextField(value,onValueChange,label=label,enabled=enabled,singleLine=singleLine,
        visualTransformation=visualTransformation,trailingIcon=trailingIcon,supportingText=supportingText,
        keyboardOptions=keyboardOptions.copy(imeAction=imeAction),
        keyboardActions=KeyboardActions(onDone={input.finish()},onNext={manager.moveFocus(FocusDirection.Next)}),
        modifier=modifier.then(request).onGloballyPositioned{rectangle=it.boundsInRoot();if(active)input.focus(id,rectangle,true)}
            .onFocusChanged{active=it.hasFocus;input.focus(id,rectangle,active);focusChanged(active)})
}

@Composable internal fun PassphraseField(label:String,value:String,enabled:Boolean,setupCounter:Boolean=false,
    imeAction:ImeAction=ImeAction.Done,change:(String)->Unit) {
    val hold=remember{HoldToReveal()};val requester=remember{FocusRequester()}
    val view=LocalView.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(hold,view,lifecycle,enabled){
        hold.end()
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE || event==Lifecycle.Event.ON_STOP)hold.end()}
        val window=ViewTreeObserver.OnWindowFocusChangeListener{focused->if(!focused)hold.end()}
        lifecycle.addObserver(observer);view.viewTreeObserver.addOnWindowFocusChangeListener(window)
        onDispose{hold.end();lifecycle.removeObserver(observer);if(view.viewTreeObserver.isAlive)view.viewTreeObserver.removeOnWindowFocusChangeListener(window)}
    }
    FormTextField(value,change,label={Text(label)},enabled=enabled,singleLine=true,
        visualTransformation=if(hold.visible&&enabled)VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,keyboardType=KeyboardType.Password),imeAction=imeAction,
        modifier=Modifier.fillMaxWidth().semantics{password()},focusRequester=requester,focusChanged={if(!it)hold.end()},
        supportingText=if(setupCounter){{Text(passphraseCounter(value))}}else null,
        trailingIcon={
            Box(Modifier.size(48.dp).semantics{contentDescription="Passphrase gedrückt halten zum Anzeigen";role=Role.Button}
                .pointerInput(enabled,hold){
                    if(enabled)awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false);down.consume();requester.requestFocus();hold.press()
                        try {
                            while(true){
                                val event=awaitPointerEvent()
                                val change=event.changes.firstOrNull{it.id==down.id} ?: break
                                if(!change.pressed)break
                                hold.move(change.position.x>=0 && change.position.y>=0 && change.position.x<size.width && change.position.y<size.height)
                                val final=awaitPointerEvent(PointerEventPass.Final)
                                if(final.changes.any{it.id==down.id&&it.isConsumed})break
                            }
                        } finally {hold.end()}
                    }
                }) {
                val color=MaterialTheme.colorScheme.onSurfaceVariant
                Canvas(Modifier.fillMaxSize().padding(12.dp)){
                    drawOval(color,topLeft=Offset(0f,size.height*.2f),size=androidx.compose.ui.geometry.Size(size.width,size.height*.6f),style=Stroke(2.dp.toPx()))
                    drawCircle(color,radius=size.height*.13f)
                    if(!hold.visible)drawLine(color,Offset(0f,size.height),Offset(size.width,0f),strokeWidth=2.dp.toPx())
                }
            }
        })
}
internal fun passphraseCounter(value:String):String {
    val count=org.pag.domain.LeadershipRules.codePointCount(value)
    return "$count / 128 Zeichen · "+when{count<16->"mindestens 16";count>128->"Maximum überschritten";count==128->"Maximum erreicht";else->"Mindestlänge erreicht"}
}
