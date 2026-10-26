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
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal val productFormShape=androidx.compose.foundation.shape.RoundedCornerShape(4.dp) // CODE only
private val formCenterStyle=androidx.compose.ui.text.TextStyle(textAlign=androidx.compose.ui.text.style.TextAlign.Center)
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
internal val LocalFormScreen=staticCompositionLocalOf<OriginalUiFence?>{null}
private val LocalFormInput=staticCompositionLocalOf<FormInput>{error("PRODUCT-FORM-SCOPE")}

/** Observes unconsumed taps in Final pass; never consumes input or interrupts scrolling. */
@androidx.compose.runtime.NonRestartableComposable
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
    CompositionLocalProvider(LocalFormInput provides input,LocalFormScreen provides (screen as?OriginalUiFence)) {
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
/** Inline factories keep caller callback/content construction inside the admitted remember
 * branch. remember avoids reconstruction; it is never an owner-end receipt. */
@Composable internal inline fun FormButton(crossinline onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:androidx.compose.ui.graphics.Shape=productFormShape,crossinline content:@Composable RowScope.()->Unit) {
    val input=LocalFormInput.current;val screen=LocalFormScreen.current
    if(screen==null){Button(onClick={input.finish();onClick()},enabled=enabled,modifier=modifier.fillMaxWidth().heightIn(min=56.dp),shape=shape){ProvideTextStyle(formCenterStyle){content()}};return}
    val revision=screen.presentationRevision()
    val click=rememberOriginalValue(screen,revision,extra=input,cost={InputControlCost.click}){OriginalInputClick(screen,input,revision){onClick()}}?:return
    val render=rememberOriginalValue(screen,revision,cost={InputControlCost.content}){
        val value:@Composable RowScope.()->Unit={if(screen.live() && screen.presentationRevision()==revision)content()};value
    }?:return
    if(screen?.live()==false)return
    Button(onClick=click,enabled=enabled,modifier=modifier.fillMaxWidth().heightIn(min=56.dp),shape=shape) {
        ProvideTextStyle(formCenterStyle) { render() }
    }
}
@Composable internal inline fun FormTextButton(crossinline onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,crossinline content:@Composable RowScope.()->Unit) {
    val input=LocalFormInput.current;val screen=LocalFormScreen.current
    if(screen==null){TextButton(onClick={input.finish();onClick()},enabled=enabled,modifier=modifier.heightIn(min=48.dp)){content()};return}
    val revision=screen.presentationRevision()
    val click=rememberOriginalValue(screen,revision,extra=input,cost={InputControlCost.click}){OriginalInputClick(screen,input,revision){onClick()}}?:return
    val render=rememberOriginalValue(screen,revision,cost={InputControlCost.content}){
        val value:@Composable RowScope.()->Unit={if(screen.live() && screen.presentationRevision()==revision)content()};value
    }?:return
    if(screen?.live()!=false)TextButton(onClick=click,enabled=enabled,modifier=modifier.heightIn(min=48.dp),content=render)
}
internal class FinishedRowClick(private val input:FormInput,private val action:OriginalRowAction):()->Unit {
    override fun invoke(){if(action.live()){input.finish();action()}}
}
@androidx.compose.runtime.NonRestartableComposable
@Composable internal fun rowClick(input:FormInput,action:OriginalRowAction):FinishedRowClick? = remember(input,action){
    if(action.reserve(org.pag.domain.ActiveWorkspaceBudget.Units(objects=4,references=16)))FinishedRowClick(input,action) else null
}
@Composable internal inline fun FormOutlinedButton(crossinline onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:androidx.compose.ui.graphics.Shape=productFormShape,crossinline content:@Composable RowScope.()->Unit) {
    val input=LocalFormInput.current;val screen=LocalFormScreen.current
    if(screen==null){OutlinedButton(onClick={input.finish();onClick()},modifier=modifier.heightIn(min=48.dp),enabled=enabled,shape=shape){content()};return}
    val revision=screen.presentationRevision()
    val click=rememberOriginalValue(screen,revision,extra=input,cost={InputControlCost.click}){OriginalInputClick(screen,input,revision){onClick()}}?:return
    val render=rememberOriginalValue(screen,revision,cost={InputControlCost.content}){
        val value:@Composable RowScope.()->Unit={if(screen.live() && screen.presentationRevision()==revision)content()};value
    }?:return
    if(screen?.live()!=false)OutlinedButton(onClick=click,modifier=modifier.heightIn(min=48.dp),enabled=enabled,shape=shape,content=render)
}
@Composable internal inline fun FormOutlinedButton(onClick:OriginalRowAction,modifier:Modifier=Modifier,enabled:Boolean=true,shape:androidx.compose.ui.graphics.Shape=productFormShape,contentEpoch:Long=0L,crossinline content:@Composable RowScope.()->Unit) {
    val input=LocalFormInput.current
    val click=rowClick(input,onClick)?:return
    val render=rememberOriginalValue(onClick,contentEpoch,cost={InputControlCost.content}){
        val value:@Composable RowScope.()->Unit={if(onClick.live())content()};value
    }?:return
    if(onClick.live())OutlinedButton(onClick=click,modifier=modifier.heightIn(min=48.dp),enabled=enabled,shape=shape,content=render)
}
/** One accessible checkbox action with the same input cleanup as other form controls. */
@Composable internal inline fun FormCheckboxButton(label:String,checked:Boolean,crossinline onClick:()->Unit,enabled:Boolean=true) {
    val input=LocalFormInput.current;val screen=LocalFormScreen.current
    if(screen==null){
        val click={input.finish();onClick()}
        OutlinedButton(onClick=click,enabled=enabled,shape=productFormShape,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).clearAndSetSemantics{
                contentDescription=label;role=Role.Checkbox;toggleableState=if(checked)ToggleableState.On else ToggleableState.Off
                if(!enabled)disabled();onClick(label=label){if(enabled){click();true}else false}
            }){Text((if(checked)"✓ " else "")+label)}
        return
    }
    val properties=(if(checked)1L else 0L)+(if(enabled)2L else 0L)
    val control=rememberOriginalPresentation(screen,properties,extra=label,cost={OriginalCheckbox.cost(label.length.toLong())}){token->
        OriginalCheckbox(token,input,label,checked,enabled){onClick()}
    }?:return
    if(screen.live())OutlinedButton(onClick=control.click,enabled=enabled,shape=productFormShape,
        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).then(control.modifier)){Text(control.text)}
}

/** Optional content is also constructed at its call site only AFTER admission. */
@Composable internal inline fun originalContent(crossinline content:@Composable ()->Unit):OriginalContent? {
    val screen=LocalFormScreen.current
    if(screen==null)return OriginalContent{content()}
    val revision=screen.presentationRevision()
    return rememberOriginalValue(screen,revision,cost={InputControlCost.content}){
        OriginalContent{if(screen.live() && screen.presentationRevision()==revision)content()}
    }
}
/** Own semantics lambdas are immutable per admitted presentation. */
@Composable internal inline fun Modifier.originalSemantics(crossinline properties:SemanticsPropertyReceiver.()->Unit):Modifier {
    val screen=LocalFormScreen.current
    if(screen==null)return semantics{properties()}
    val revision=screen.presentationRevision()
    val values=rememberOriginalValue(screen,revision,extra=this,cost={OriginalSemantics.cost}){
        OriginalSemantics(screen,revision){properties()}
    }?:return this
    return remember(this,values){
        if(!screen.reserve(OriginalFieldInput.modifierCost))this else this.then(values.modifier)
    }
}
@Composable internal inline fun FormTextField(value:String,crossinline onValueChange:(String)->Unit,crossinline label:@Composable ()->Unit,
    modifier:Modifier=Modifier,enabled:Boolean=true,singleLine:Boolean=false,
    keyboardOptions:KeyboardOptions=KeyboardOptions.Default,imeAction:ImeAction=ImeAction.Done,
    visualTransformation:VisualTransformation=VisualTransformation.None,
    trailingIcon:OriginalContent?=null,supportingText:OriginalContent?=null,
    autoFocus:Boolean=false,focusRequester:FocusRequester?=null,codeLabel:CodeContent?=null,crossinline focusChanged:(Boolean)->Unit={}) {
    val input=LocalFormInput.current;val manager=LocalFocusManager.current;val screen=LocalFormScreen.current
    if(screen==null){
        LegacyFormTextField(value,{onValueChange(it)},codeLabel?.render?:{label()},modifier,enabled,singleLine,keyboardOptions,imeAction,visualTransformation,
            trailingIcon?.render,supportingText?.render,autoFocus,focusRequester,{focusChanged(it)});return
    }

    val field=remember(input,screen){
        if(screen?.reserve(OriginalFieldInput.cost)==false)null else OriginalFieldInput(screen,input,manager,{onValueChange(it)},{focusChanged(it)})
    }?:return
    DisposableEffect(field){onDispose{field.dispose()}}
    val requester=focusRequester ?: field.requester.takeIf{autoFocus}
    val keyboard=LocalSoftwareKeyboardController.current
    val accessibility=LocalContext.current.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
    LaunchedEffect(autoFocus,field){
        if(screen?.live()!=false && autoFocus && accessibility?.isTouchExplorationEnabled!=true){requester?.requestFocus();keyboard?.show()}
    }
    val options=remember(field,keyboardOptions,imeAction){
        if(screen?.reserve(OriginalFieldInput.modifierCost)==false)null else keyboardOptions.copy(imeAction=imeAction)
    }?:return
    val request=remember(requester,field){
        if(screen?.reserve(OriginalFieldInput.modifierCost)==false)null else
            if(requester==null)field.modifier else Modifier.focusRequester(requester).then(field.modifier)
    }?:return
    val actualModifier=remember(modifier,request){if(screen?.reserve(OriginalFieldInput.modifierCost)==false)null else modifier.then(request)}?:return
    val fieldLabel=codeLabel?.render?:originalContent{label()}?.render?:return
    if(screen?.live()==false)return
    OutlinedTextField(value,field.valueCallback,label=fieldLabel,enabled=enabled,singleLine=singleLine,
        visualTransformation=visualTransformation,trailingIcon=trailingIcon?.render,supportingText=supportingText?.render,
        keyboardOptions=options,keyboardActions=field.keyboard,modifier=actualModifier)
}

@androidx.compose.runtime.NonRestartableComposable

@Composable private fun LegacyFormTextField(value:String,onValueChange:(String)->Unit,label:@Composable ()->Unit,
    modifier:Modifier=Modifier,enabled:Boolean=true,singleLine:Boolean=false,
    keyboardOptions:KeyboardOptions=KeyboardOptions.Default,imeAction:ImeAction=ImeAction.Done,
    visualTransformation:VisualTransformation=VisualTransformation.None,
    trailingIcon:(@Composable ()->Unit)?=null,supportingText:(@Composable ()->Unit)?=null,
    autoFocus:Boolean=false,focusRequester:FocusRequester?=null,focusChanged:(Boolean)->Unit={}) {
    val input=LocalFormInput.current;val manager=LocalFocusManager.current
    val id=remember{Any()};var rectangle:Rect? by remember{mutableStateOf(null)};var active by remember{mutableStateOf(false)}
    DisposableEffect(id){onDispose{if(input.focused===id)input.finish()}}
    val entryFocus=remember{FocusRequester()}
    val requester=focusRequester ?: entryFocus.takeIf{autoFocus}
    val keyboard=LocalSoftwareKeyboardController.current
    val accessibility=LocalContext.current.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
    // One entry effect; no focus restoration at ticks/recomposition or after leaving this form.
    LaunchedEffect(autoFocus) {
        if(autoFocus && accessibility?.isTouchExplorationEnabled != true) {
            requester?.requestFocus(); keyboard?.show()
        }
    }
    val request=if(requester==null)Modifier else Modifier.focusRequester(requester)
    OutlinedTextField(value,onValueChange,label=label,enabled=enabled,singleLine=singleLine,
        visualTransformation=visualTransformation,trailingIcon=trailingIcon,supportingText=supportingText,
        keyboardOptions=keyboardOptions.copy(imeAction=imeAction),
        keyboardActions=KeyboardActions(onDone={input.finish()},onNext={manager.moveFocus(FocusDirection.Next)}),
        modifier=modifier.then(request).onGloballyPositioned{rectangle=it.boundsInRoot();if(active)input.focus(id,rectangle,true)}
            .onFocusChanged{active=it.hasFocus;input.focus(id,rectangle,active);focusChanged(active)})
}


@androidx.compose.runtime.NonRestartableComposable


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
        supportingText=if(setupCounter)originalContent{Text(passphraseCounter(value))}else null,
        trailingIcon=originalContent{
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

/** Keyboard hints only: values and domain normalization never pass through this mapping. */
internal enum class ProductTextKind { PERSON, ACTION_TITLE, ROLE, TASK_TITLE, PLACE, DESCRIPTION, PHONE, DATE, PASSPHRASE }
private fun makeProductKeyboard(kind:ProductTextKind):KeyboardOptions = when(kind) {
    ProductTextKind.PERSON,ProductTextKind.ROLE,ProductTextKind.PLACE -> KeyboardOptions(capitalization=KeyboardCapitalization.Words)
    ProductTextKind.ACTION_TITLE,ProductTextKind.TASK_TITLE,ProductTextKind.DESCRIPTION -> KeyboardOptions(capitalization=KeyboardCapitalization.Sentences)
    ProductTextKind.PHONE -> KeyboardOptions(keyboardType=KeyboardType.Phone,autoCorrectEnabled=false)
    ProductTextKind.DATE -> KeyboardOptions(capitalization=KeyboardCapitalization.None,autoCorrectEnabled=false)
    ProductTextKind.PASSPHRASE -> KeyboardOptions(capitalization=KeyboardCapitalization.None,keyboardType=KeyboardType.Password,autoCorrectEnabled=false)
}

private val productKeyboards=ProductTextKind.entries.associateWith(::makeProductKeyboard) // CODE, no user data
internal fun productKeyboard(kind:ProductTextKind)=productKeyboards.getValue(kind)

@Composable internal inline fun FormChoiceButton(label:String,chosen:Boolean,enabled:Boolean,fillWidth:Boolean=true,tab:Boolean=false,selection:Boolean=true,primary:Boolean=false,crossinline onClick:()->Unit) {
    val screen=LocalFormScreen.current;val input=LocalFormInput.current
    if(screen==null){FormOutlinedButton(onClick,enabled=enabled,modifier=Modifier.fillMaxWidth()){Text((if(chosen)"✓ " else "")+label)};return}
    val properties=(if(chosen)1L else 0L)+(if(enabled)2L else 0L)+(if(tab)4L else 0L)+(if(selection)8L else 0L)+(if(primary)16L else 0L)
    val value=rememberOriginalPresentation(screen,properties,extra=label,cost={OriginalCheckbox.cost(label.length.toLong())}){token->
        OriginalChoice(token,input,label,chosen,enabled,tab,selection){onClick()}
    }?:return
    if(!screen.live())return
    val modifier=(if(fillWidth)Modifier.fillMaxWidth() else Modifier).heightIn(min=if(primary)56.dp else 48.dp).then(value.semantics.modifier)
    if(primary)Button(onClick=value.click,enabled=enabled,shape=productFormShape,modifier=modifier){ProvideTextStyle(formCenterStyle){Text(value.text)}}
    else OutlinedButton(onClick=value.click,enabled=enabled,shape=productFormShape,modifier=modifier){Text(value.text)}
}
