package com.experiencingyah.bibliCal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object BibliCalShapes {
    val radiusCard = 16.dp
    val radiusChip = 12.dp
    val radiusHero = 24.dp
    val hairline = 1.dp

    val Card = RoundedCornerShape(radiusCard)
    val Chip = RoundedCornerShape(radiusChip)
    val Hero = RoundedCornerShape(radiusHero)
}

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = BibliCalShapes.Chip,
    medium = BibliCalShapes.Card,
    large = BibliCalShapes.Hero,
    extraLarge = RoundedCornerShape(28.dp)
)
