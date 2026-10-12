package com.hirusha.labellens.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Turns an outline from [ShapePoints] into a shape, rounding through the midpoints. */
private fun polygonShape(points: FloatArray): Shape = GenericShape { size, _ ->
    val n = points.size / 2
    fun x(i: Int) = points[(i % n) * 2] / 100f * size.width
    fun y(i: Int) = points[(i % n) * 2 + 1] / 100f * size.height
    moveTo((x(0) + x(1)) / 2f, (y(0) + y(1)) / 2f)
    for (i in 1..n) {
        quadraticTo(x(i), y(i), (x(i) + x(i + 1)) / 2f, (y(i) + y(i + 1)) / 2f)
    }
    close()
}

/** The Material 3 Expressive shapes the designs use for heroes, avatars and loaders. */
object ExpressiveShapes {
    val Cookie9: Shape = polygonShape(ShapePoints.cookie9)
    val Sunny8: Shape = polygonShape(ShapePoints.sunny8)
    val Clover4: Shape = polygonShape(ShapePoints.clover4)
    val Burst12: Shape = polygonShape(ShapePoints.burst12)
}

/**
 * Corner shape for a row in a segmented (grouped) list: 28dp on the outside
 * corners of the group, 8dp between rows.
 */
fun segShape(index: Int, count: Int, outer: Dp = 28.dp, inner: Dp = 8.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}
