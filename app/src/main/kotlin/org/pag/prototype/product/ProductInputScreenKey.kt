package org.pag.prototype.product

/** Fixed identity/cancellation metadata only; no lists, payload DTOs or board snapshots.
 * Created after the existing original-owner copy gate admits the complete metadata object.
 * Comparing fields uses their inferred primitive/enum/reference types, not a new List<Any?>
 * (nor new boxed generation/page values) at every root composition. No end receipt. */
internal class ProductInputScreenKey(m:ProductViewModel):OriginalUiFence {
    private val port=m // Existing service endpoint; never a payload/snapshot capture.
    override fun live()=port.inputScreenLive(this)
    override fun reserve(units:org.pag.domain.ActiveWorkspaceBudget.Units)=port.reserveInput(this,units)
    override fun presentationRevision():Long=port.uiPresentationRevision
    private val p0=m.snapshot.state
    private val p1=m.snapshot.generation
    private val p2=m.selected?.id
    private val p3=m.selectedPerson?.id
    private val p4=m.actionListState.javaClass
    private val p5=m.preparationArea
    private val p6=m.activeArea
    private val p7=m.personDetailLoading
    private val p8=m.creating
    private val p9=m.editing?.id
    private val p10=m.personForm
    private val p11=m.sensitiveSetup
    private val p12=m.sensitiveArea
    private val p13=m.sensitiveOpened?.grant
    private val p14=m.operations.screen
    private val p15=m.operations.personId
    private val p16=m.tasks.form
    private val p17=m.tasks.selected?.id
    private val p18=m.tasks.filter
    private val p19=m.protocol.mode
    private val p20=m.protocol.form
    private val p21=m.protocol.detail?.entry?.id
    private val p22=m.protocol.historical?.revision?.id
    private val p23=m.expenses.form
    private val p24=m.expenses.selected?.id
    private val p25=m.expenses.confirmingDelete
    private val p26=m.completedExpensesOpen
    private val p27=m.material.form
    private val p28=m.material.selected?.id
    private val p29=m.material.confirmingDelete
    private val p30=m.completedMaterialOpen
    private val p31=m.memoryReports.mode
    private val p32=m.memoryReports.sectionOrdinal
    private val p33=m.memoryReports.expected?.id
    private val p34=m.completedMemoryReportsOpen
    private val p35=m.documents.available
    private val p36=m.documents.importing
    private val p37=m.documents.confirmingDelete
    private val p38=m.documents.camera.mode
    private val p39=m.documents.viewed?.id
    private val p40=m.documents.textViewed?.id
    private val p41=m.documents.textPageIndex
    private val p42=m.documents.pdfViewed?.id
    private val p43=m.documents.pdfIndex
    private val p44=m.tasks.formRevision
    private val p45=m.tasks.uiHistoryEpoch
    private val p46=m.tasks.busy
    private val p47=m.tasks.error
    private val p48=m.tasks.commitConfirmed
    private val p49=m.tasks.closedExpanded
    private val p50=m.operations.uiEditorEpoch
    private val p51=m.operations.uiCreateEpoch
    private val p52=m.operations.uiReadEpoch
    private val p53=m.operations.busy
    private val p54=m.operations.error
    private val p55=m.operations.createSurface
    private val p56=m.operations.createError
    private val p57=m.operations.templateCreateError
    private val p58=m.operations.moreFunctionsExpanded
    private val p59=m.operations.functionFocus?.roleId
    private val p60=m.protocol.uiEditorEpoch
    private val p61=m.protocol.busy
    private val p62=m.protocol.loading
    private val p63=m.protocol.detailLoading
    private val p64=m.protocol.error
    private val p65=m.protocol.confirmingMistaken
    private val p66=m.protocol.moreFields
    private val p67=m.protocol.moreTemplates
    private val p68=m.protocol.history
    private val p69=m.expenses.uiEditorEpoch
    private val p70=m.expenses.busy
    private val p71=m.expenses.error
    private val p72=m.expenses.loading
    private val p73=m.material.uiEditorEpoch
    private val p74=m.material.busy
    private val p75=m.material.error
    private val p76=m.material.loading
    private val p77=m.memoryReports.uiEditorEpoch
    private val p78=m.memoryReports.busy
    private val p79=m.memoryReports.loading
    private val p80=m.memoryReports.error
    private val p81=m.memoryReports.confirmingComplete
    private val p82=m.memoryReports.confirmingDiscard
    private val p83=m.workspaceError
    private val p84=m.workspaceRefusal
    private val p85=m.protocol.formStructureEpoch
    private val p86=m.memoryReports.formStructureEpoch
    private val pageEpoch=m.pageEpoch
    private val boundedList=m.boundedListVisible
    private val choiceEpoch=m.choiceEpoch
    fun matches(m:ProductViewModel):Boolean=
        pageEpoch==m.pageEpoch && boundedList==m.boundedListVisible && choiceEpoch==m.choiceEpoch &&
        p85==m.protocol.formStructureEpoch && p86==m.memoryReports.formStructureEpoch &&
        p0==m.snapshot.state &&
        p1==m.snapshot.generation &&
        p2==m.selected?.id &&
        p3==m.selectedPerson?.id &&
        p4==m.actionListState.javaClass &&
        p5==m.preparationArea &&
        p6==m.activeArea &&
        p7==m.personDetailLoading &&
        p8==m.creating &&
        p9==m.editing?.id &&
        p10==m.personForm &&
        p11==m.sensitiveSetup &&
        p12==m.sensitiveArea &&
        p13==m.sensitiveOpened?.grant &&
        p14==m.operations.screen &&
        p15==m.operations.personId &&
        p16==m.tasks.form &&
        p17==m.tasks.selected?.id &&
        p18==m.tasks.filter &&
        p19==m.protocol.mode &&
        p20==m.protocol.form &&
        p21==m.protocol.detail?.entry?.id &&
        p22==m.protocol.historical?.revision?.id &&
        p23==m.expenses.form &&
        p24==m.expenses.selected?.id &&
        p25==m.expenses.confirmingDelete &&
        p26==m.completedExpensesOpen &&
        p27==m.material.form &&
        p28==m.material.selected?.id &&
        p29==m.material.confirmingDelete &&
        p30==m.completedMaterialOpen &&
        p31==m.memoryReports.mode &&
        p32==m.memoryReports.sectionOrdinal &&
        p33==m.memoryReports.expected?.id &&
        p34==m.completedMemoryReportsOpen &&
        p35==m.documents.available &&
        p36==m.documents.importing &&
        p37==m.documents.confirmingDelete &&
        p38==m.documents.camera.mode &&
        p39==m.documents.viewed?.id &&
        p40==m.documents.textViewed?.id &&
        p41==m.documents.textPageIndex &&
        p42==m.documents.pdfViewed?.id &&
        p43==m.documents.pdfIndex &&
        p44==m.tasks.formRevision &&
        p45==m.tasks.uiHistoryEpoch &&
        p46==m.tasks.busy &&
        p47==m.tasks.error &&
        p48==m.tasks.commitConfirmed &&
        p49==m.tasks.closedExpanded &&
        p50==m.operations.uiEditorEpoch &&
        p51==m.operations.uiCreateEpoch &&
        p52==m.operations.uiReadEpoch &&
        p53==m.operations.busy &&
        p54==m.operations.error &&
        p55==m.operations.createSurface &&
        p56==m.operations.createError &&
        p57==m.operations.templateCreateError &&
        p58==m.operations.moreFunctionsExpanded &&
        p59==m.operations.functionFocus?.roleId &&
        p60==m.protocol.uiEditorEpoch &&
        p61==m.protocol.busy &&
        p62==m.protocol.loading &&
        p63==m.protocol.detailLoading &&
        p64==m.protocol.error &&
        p65==m.protocol.confirmingMistaken &&
        p66==m.protocol.moreFields &&
        p67==m.protocol.moreTemplates &&
        p68==m.protocol.history &&
        p69==m.expenses.uiEditorEpoch &&
        p70==m.expenses.busy &&
        p71==m.expenses.error &&
        p72==m.expenses.loading &&
        p73==m.material.uiEditorEpoch &&
        p74==m.material.busy &&
        p75==m.material.error &&
        p76==m.material.loading &&
        p77==m.memoryReports.uiEditorEpoch &&
        p78==m.memoryReports.busy &&
        p79==m.memoryReports.loading &&
        p80==m.memoryReports.error &&
        p81==m.memoryReports.confirmingComplete &&
        p82==m.memoryReports.confirmingDiscard &&
        p83==m.workspaceError &&
        p84==m.workspaceRefusal
}
