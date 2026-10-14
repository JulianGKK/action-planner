package org.pag.data.product
import org.pag.domain.*
import org.junit.Assert.*
import org.junit.Test
class MemoryAnswerCodecTest {
    @Test fun everyQuestionAndTypeRoundTrips() {
        MemoryQuestionnaireV1.questions.forEach { q ->
            val value:MemoryAnswerValue=when(q.type){
                MemoryAnswerType.BOOLEAN_UNCERTAIN,MemoryAnswerType.SINGLE_CHOICE->MemoryAnswerValue.Option(q.options.first().key)
                MemoryAnswerType.MULTI_CHOICE->MemoryAnswerValue.Multi(listOf(q.options.first().key))
                MemoryAnswerType.OPTIONAL_COUNT->MemoryAnswerValue.Count(0)
                else->MemoryAnswerValue.Text("synthetic, 🦋\n  memory")}
            val e=MemoryAnswerCodec.encode(q.key,value)!!;assertEquals(value,MemoryAnswerCodec.decode(q.key,e.valueKind,e.payload))
        }
    }
    @Test fun booleanAndSpecialChoices() {
        for(key in listOf("B_AGREEMENT_OR_CONDITION","A_ALLEGATION","C_WAIT_DURATION","O_RELEASE_TIME","L_PHOTO_COUNT")) {
            MemoryQuestionnaireV1.question(key)!!.options.forEach { val v=MemoryAnswerValue.Option(it.key);val e=MemoryAnswerCodec.encode(key,v)!!;assertEquals(v,MemoryAnswerCodec.decode(key,e.valueKind,e.payload)) }
        }
        assertNull(MemoryAnswerCodec.decode("A_ALLEGATION","OPTION","YES"));assertNull(MemoryAnswerCodec.decode("B_ENDING_MODE","OPTION","UNCLEAR"))
    }
    @Test fun multiCanonicalOrderAndExclusiveUnknown() {
        val e=MemoryAnswerCodec.encode("L_ED_MEASURES",MemoryAnswerValue.Multi(listOf("OTHER_PHOTOS","FACE_PHOTOS")))!!
        assertEquals("FACE_PHOTOS,OTHER_PHOTOS",e.payload)
        assertNull(MemoryAnswerCodec.decode("L_ED_MEASURES","MULTI","OTHER_PHOTOS,FACE_PHOTOS"))
        for(s in listOf("FACE_PHOTOS,FACE_PHOTOS","FACE_PHOTOS,BOGUS","FACE_PHOTOS,UNCLEAR","","FACE_PHOTOS,"))assertNull(MemoryAnswerCodec.decode("L_ED_MEASURES","MULTI",s))
    }
    @Test fun countCanonicalBoundsAndWrongKinds() {
        for(n in listOf(0,99_999))assertEquals(MemoryAnswerValue.Count(n),MemoryAnswerCodec.decode("L_PHOTO_COUNT","COUNT",n.toString()))
        for(s in listOf("00","01","-1","100000","1.0","+1"," 1","99999999999999999999"))assertNull(MemoryAnswerCodec.decode("L_PHOTO_COUNT","COUNT",s))
        assertNull(MemoryAnswerCodec.decode("A_FREE_NARRATIVE","COUNT","1"));assertNull(MemoryAnswerCodec.decode("L_PHOTO_COUNT","TEXT","1"));assertNull(MemoryAnswerCodec.decode("BOGUS","TEXT","x"));assertNull(MemoryAnswerCodec.decode("A_FREE_NARRATIVE","TEXT"," x "))
        assertEquals("Keine Angabe",MemoryAnswerCodec.label("L_PHOTO_COUNT",null));assertEquals("0",MemoryAnswerCodec.label("L_PHOTO_COUNT",MemoryAnswerValue.Count(0)))
    }
}
