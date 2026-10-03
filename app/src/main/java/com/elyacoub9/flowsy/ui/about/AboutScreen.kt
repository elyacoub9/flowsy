package com.elyacoub9.flowsy.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elyacoub9.flowsy.BuildConfig
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.ui.theme.Background
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme

/** Developer name: shown as is in every language. */
const val DEVELOPER = "elyacoub9"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The adaptive launcher icon itself can't be drawn by Compose: same layers, drawn here.
            Box(
                Modifier
                    .size(96.dp)
                    .background(Background, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(96.dp))
            }
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.about_offline), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Text(stringResource(R.string.about_by, DEVELOPER), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Flowsy 1.0.0 · by elyacoub9" at the bottom of Settings. */
@Composable
fun AppSignature(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.about_signature, stringResource(R.string.app_name), BuildConfig.VERSION_NAME, DEVELOPER),
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Preview
@Composable
private fun AboutScreenPreview() {
    FlowsyTheme { AboutScreen(onBack = {}) }
}

@Preview(locale = "fr")
@Composable
private fun AboutScreenFrenchPreview() {
    FlowsyTheme { AboutScreen(onBack = {}) }
}

@Preview
@Composable
private fun AppSignaturePreview() {
    FlowsyTheme { Surface { AppSignature(Modifier.padding(16.dp)) } }
}

@Preview(locale = "fr")
@Composable
private fun AppSignatureFrenchPreview() {
    FlowsyTheme { Surface { AppSignature(Modifier.padding(16.dp)) } }
}
