package org.pag.prototype.product

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Enabled Material3 token pairs actually used by ProductScreen; not a pixel/whole-app audit. */
class P116ContrastTest {
 @Test fun enabledProductTextTokensMeetNormalTextContrast(){
  val c=lightColorScheme()
  val pairs=listOf("surface" to (c.onSurface to c.surface),"primary" to (c.onPrimary to c.primary),
   "error" to (c.onError to c.error),"secondaryContainer" to (c.onSecondaryContainer to c.secondaryContainer),
   "surfaceVariant" to (c.onSurfaceVariant to c.surfaceVariant))
  for((name,pair) in pairs){val ratio=contrast(pair.first,pair.second);println("P116_CONTRAST $name $ratio");assertTrue("$name: $ratio",ratio>=4.5f)}
 }
 private fun contrast(a:Color,b:Color):Float{val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+0.05f)/(minOf(x,y)+0.05f)}
}
