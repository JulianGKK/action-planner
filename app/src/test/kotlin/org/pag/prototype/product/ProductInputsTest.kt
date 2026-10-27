package org.pag.prototype.product

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test
import org.pag.domain.LeadershipRules

/** UI policy tests, not a claim of end-to-end Compose/IME instrumentation. */
class ProductInputsTest {
    @Test fun revealRequiresHeldPressAndReleaseMasks(){val hold=HoldToReveal();assertFalse(hold.visible);hold.press();assertTrue(hold.visible);hold.end();assertFalse(hold.visible)}
    @Test fun leavingIconDoesNotRevealAgainOnReentry(){val hold=HoldToReveal();hold.press();hold.move(false);assertFalse(hold.visible);hold.move(true);assertFalse(hold.visible);hold.end();hold.press();assertTrue(hold.visible)}
    @Test fun cancellationFocusNavigationAndLifecycleResetAreIrreversibleUntilNewPress(){val hold=HoldToReveal();repeat(5){hold.press();hold.end();assertFalse(hold.visible);hold.move(true);assertFalse(hold.visible)}}
    @Test fun counterAndValidatorShareUnicodeCodepoints(){for(n in listOf(12,16,128,129)){val value="😀".repeat(n);val bytes=value.toCharArray();try{assertEquals(n,LeadershipRules.codePointCount(value));assertTrue(passphraseCounter(value).startsWith("$n / 128"));assertEquals(n in 16..128,LeadershipRules.validPassphrase(bytes))}finally{bytes.fill('\u0000')}}}
    @Test fun counterDescribesTechnicalBoundsWithoutStrengthPromise(){assertTrue(passphraseCounter("x".repeat(12)).endsWith("mindestens 16"));assertTrue(passphraseCounter("x".repeat(16)).endsWith("Mindestlänge erreicht"));assertTrue(passphraseCounter("x".repeat(128)).endsWith("Maximum erreicht"));assertTrue(passphraseCounter("x".repeat(129)).endsWith("Maximum überschritten"))}
    @Test fun backgroundTapDismissesButTextFieldTapDoesNot(){var dismissed=0;val input=FormInput{dismissed++};val id=Any();input.focus(id,Rect(0f,0f,10f,10f),true);input.backgroundTap(Offset(5f,5f),false,false);assertEquals(0,dismissed);input.backgroundTap(Offset(20f,20f),false,false);assertEquals(1,dismissed);assertNull(input.focused)}
    @Test fun consumedButtonsHoldGesturesAndScrollingAreNotIntercepted(){var dismissed=0;val input=FormInput{dismissed++};input.focus(Any(),Rect(0f,0f,10f,10f),true);input.backgroundTap(Offset(20f,20f),true,false);input.backgroundTap(Offset(20f,20f),false,true);assertEquals(0,dismissed)}
    @Test fun fieldTransferDoesNotDismissKeyboard(){var dismissed=0;val input=FormInput{dismissed++};val first=Any();val next=Any();input.focus(first,Rect.Zero,true);input.focus(next,Rect.Zero,true);input.focus(first,Rect.Zero,false);assertSame(next,input.focused);assertEquals(0,dismissed)}
    @Test fun saveCancelNavigationAndImeCompletionDismissAndDropFocus(){var dismissed=0;val input=FormInput{dismissed++};repeat(4){input.focus(Any(),Rect.Zero,true);input.finish();assertNull(input.focused);assertNull(input.bounds)};assertEquals(4,dismissed)}
    @Test fun idleNavigationNeedsNoImeCallButLosingAFieldStillRequiresCleanup(){
        val requests=mutableListOf<Boolean>();val input=FormInput{requests.add(it)}
        input.finish();input.finish()
        val id=Any();input.focus(id,Rect.Zero,true);input.focus(id,Rect.Zero,false)
        input.finish();input.finish()
        assertEquals(listOf(false,false,true,false),requests)
        assertNull(input.focused);assertNull(input.bounds)
    }

}
