"""Index the selected, hashed Compose source artifacts; never downloads or builds."""
from pathlib import Path
import hashlib, json, zipfile

out=Path(__file__).parent
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
entries={
 'material3-android':{
  'commonMain/androidx/compose/material3/Text.kt':['fun Text(', 'BasicText(']},
 'foundation-android':{
  'commonMain/androidx/compose/foundation/text/BasicText.kt':['fun BasicText(', 'TextStringSimpleElement(', 'BackgroundTextMeasurement('],
  'androidMain/androidx/compose/foundation/text/BasicText.android.kt':['LocalBackgroundTextMeasurementExecutor =', 'executor != null'],
  'commonMain/androidx/compose/foundation/text/modifiers/TextStringSimpleElement.kt':['private val text: String', 'override fun create', 'override fun update'],
  'commonMain/androidx/compose/foundation/text/modifiers/TextStringSimpleNode.kt':['private var text: String', 'applySemantics()', 'text = AnnotatedString(', 'getTextLayoutResult(', 'override fun ContentDrawScope.draw()', 'localParagraph.paint('],
  'commonMain/androidx/compose/foundation/text/modifiers/ParagraphLayoutCache.kt':['private var text: String', 'internal var paragraph:', 'private var paragraphIntrinsics:', 'fun slowCreateTextLayoutResultOrNull', 'TextLayoutInput(']},
 'ui-text-android':{
  'androidMain/androidx/compose/ui/text/ParagraphIntrinsics.android.kt':['class AndroidParagraphIntrinsics(', 'val text: String', 'createCharSequence('],
  'androidMain/androidx/compose/ui/text/AndroidParagraph.android.kt':['private val layout:', 'internal val charSequence:', 'constructTextLayout(', 'override fun paint('],
  'androidMain/androidx/compose/ui/text/android/TextLayout.android.kt':['val layout: Layout', 'BoringLayoutFactory.create(', 'StaticLayoutFactory.create(', 'val text: CharSequence', 'fun paint(', 'layout.draw(']},
 'ui-android':{
  'androidMain/androidx/compose/ui/platform/AndroidComposeViewAccessibilityDelegateCompat.android.kt':['private var previousSemanticsNodes:', 'info.text =', 'private fun sendEvent(', 'event.text.add(it)', 'previousSemanticsNodes.clear()'],
  'androidMain/androidx/compose/ui/platform/AndroidComposeView.android.kt':['override fun onDetachedFromWindow()', 'snapshotObserver.stopObserving()'],
  'androidMain/androidx/compose/ui/platform/RenderNodeLayer.android.kt':['override fun destroy()', 'renderNode.discardDisplayList()', 'ownerView.recycle(this)']}}
index=[]
for artifact in json.loads((out/'artifacts.json').read_text()):
    group,module,version=artifact['coordinate'].split(':')
    source=cache/artifact['sources_cache_path']
    aar=next((cache/group/module/version).rglob('*.aar'))
    assert hashlib.sha256(source.read_bytes()).hexdigest()==artifact['sources_sha256']
    assert hashlib.sha256(aar.read_bytes()).hexdigest()==artifact['aar_sha256']
    with zipfile.ZipFile(source) as archive:
        for name,markers in entries[module].items():
            raw=archive.read(name);lines=raw.decode().splitlines()
            hits={marker:[i+1 for i,line in enumerate(lines) if marker in line] for marker in markers}
            assert all(hits.values()),(name,hits)
            index.append(dict(coordinate=artifact['coordinate'],entry=name,sha256=hashlib.sha256(raw).hexdigest(),line_hits=hits))
(out/'source-index.json').write_text(json.dumps(index,indent=2)+'\n')
print('Hashed Compose source entries and matching AARs verified:',len(index))
