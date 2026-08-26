package com.experiencingyah.bibliCal.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.experiencingyah.bibliCal.ui.components.CelCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

data class RecommendedResource(
    val title: String,
    val description: String,
    val url: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendedResourcesScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = listOf(
        RecommendedResource(
            title = "The Scriptures Bible",
            description = "The Scriptures restores Hebrew names and concepts, including refrences to the renewed moon and aviv barley.",
            url = "https://amzn.to/3OWNF1M",
        ),
        RecommendedResource(
            title = "Moon Phase Puzzle",
            description = "A fun and educational puzzle that helps you learn about the moon's phases.",
            url = "https://amzn.to/4cHmzWp",
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recommended Resources") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "As an Amazon Associate I earn from qualifying purchases.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            resources.forEach { resource ->
                CelCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                    Text(
                        text = resource.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resource.url))
                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                context.startActivity(intent)
                            },
                    )
                    Text(
                        text = resource.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    }
                }
            }
        }
    }
}
