package org.pag.prototype.product
import androidx.compose.ui.unit.Constraints
import org.junit.Assert.*
import org.junit.Test
class ProductViewportTest {
    @Test fun exactSamsungOldContentHeightCannotBeRepresented(){
        assertThrows(IllegalArgumentException::class.java){Constraints(minWidth=0,maxWidth=954,minHeight=269909,maxHeight=Constraints.Infinity)}
    }
    @Test fun loadingUsesRealViewportAndNotContentHeight(){
        val minimum=loadingViewportMinimum(2100,144)
        val c=Constraints(minWidth=0,maxWidth=954,minHeight=minimum,maxHeight=Constraints.Infinity)
        assertEquals(1956,c.minHeight)
    }
    @Test fun infiniteOrTinyViewportDoesNotInventLargeMinimum(){assertEquals(0,loadingViewportMinimum(Constraints.Infinity,144));assertEquals(0,loadingViewportMinimum(30,144))}
}
