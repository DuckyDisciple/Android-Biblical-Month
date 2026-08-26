package com.experiencingyah.bibliCal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.experiencingyah.bibliCal.ui.theme.BibliCalShapes
import com.experiencingyah.bibliCal.ui.theme.BibliCalThemeTokens

@Composable
fun CelCard(
    modifier: Modifier = Modifier,
    containerColor: Color = BibliCalThemeTokens.colors.surfaceElevated,
    elevated: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = BibliCalShapes.Card,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = if (elevated) 1.dp else 0.dp,
        border = BorderStroke(BibliCalShapes.hairline, BibliCalThemeTokens.colors.outlineSoft),
    ) {
        content()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun BrandTopBar(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    imageRes: Int? = null,
    cta: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        imageRes?.let {
            Image(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier.size(160.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        cta?.invoke()
    }
}

@Composable
fun CelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = BibliCalShapes.Chip,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        content = content,
    )
}
