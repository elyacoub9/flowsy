package com.elyacoub9.flowsy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.data.AppLanguage
import com.elyacoub9.flowsy.ui.about.AppSignature
import com.elyacoub9.flowsy.ui.backup.BackupEvent
import com.elyacoub9.flowsy.ui.backup.RestoreDialogs
import com.elyacoub9.flowsy.ui.backup.rememberBackupLauncher
import com.elyacoub9.flowsy.ui.backup.rememberRestoreLauncher
import com.elyacoub9.flowsy.ui.common.CurrencyList
import com.elyacoub9.flowsy.ui.common.LanguagePicker
import com.elyacoub9.flowsy.ui.common.currentLocale
import com.elyacoub9.flowsy.ui.common.NavRow
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme
import java.util.Currency

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restoreState by viewModel.restore.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel, resources) {
        viewModel.backup.events.collect { event ->
            val message = when (event) {
                BackupEvent.Saved -> R.string.backup_saved
                BackupEvent.Failed -> R.string.backup_failed
            }
            snackbarHostState.showSnackbar(resources.getString(message))
        }
    }
    val backupLauncher = rememberBackupLauncher(viewModel.backup::export)
    val restoreLauncher = rememberRestoreLauncher(viewModel.restore::check)
    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSelectLanguage = viewModel::selectLanguage,
        onSelectCurrency = viewModel::selectCurrency,
        onOpenCategories = onOpenCategories,
        onOpenAbout = onOpenAbout,
        onBackup = { backupLauncher(viewModel.today()) },
        onRestore = restoreLauncher,
    )
    RestoreDialogs(restoreState, onConfirm = viewModel.restore::confirm, onDismiss = viewModel.restore::dismiss)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAbout: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    var pickCurrency by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state == null) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(stringResource(R.string.settings_language)) {
                LanguagePicker(selected = state.language, onSelect = onSelectLanguage)
            }
            SettingsSection(stringResource(R.string.settings_budget)) {
                val locale = currentLocale()
                val currency = Currency.getInstance(state.currencyCode)
                // Changeable only while no income, expense or pot exists.
                CurrencyRow(
                    value = "${currency.currencyCode} — ${currency.getDisplayName(locale)}",
                    locked = state.currencyLocked,
                    onClick = { pickCurrency = true },
                )
                NavRow(R.string.settings_categories, onOpenCategories, Modifier.padding(horizontal = 16.dp))
            }
            SettingsSection(stringResource(R.string.settings_backup)) {
                NavRow(R.string.action_backup, onBackup, Modifier.padding(horizontal = 16.dp))
                NavRow(R.string.action_restore_backup, onRestore, Modifier.padding(horizontal = 16.dp))
            }
            SettingsSection(stringResource(R.string.settings_about)) {
                NavRow(R.string.settings_about, onOpenAbout, Modifier.padding(horizontal = 16.dp))
            }
            AppSignature(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp))
        }
        if (pickCurrency && !state.currencyLocked) {
            CurrencyDialog(
                selectedCode = state.currencyCode,
                onSelect = { onSelectCurrency(it); pickCurrency = false },
                onDismiss = { pickCurrency = false },
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        content()
    }
}

/** Currency, tappable while unlocked; when locked it says why (text, not only a disabled look). */
@Composable
private fun CurrencyRow(value: String, locked: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (locked) Modifier.semantics(mergeDescendants = true) {} else Modifier.clickable(role = Role.Button, onClick = onClick))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.settings_currency), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!locked) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
        if (locked) {
            Text(
                stringResource(R.string.currency_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The onboarding currency list in a full-screen dialog. */
@Composable
private fun CurrencyDialog(selectedCode: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                    Text(stringResource(R.string.onboarding_currency_title), style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(8.dp))
                CurrencyList(selectedCode, query, { query = it }, onSelect)
            }
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    FlowsyTheme {
        SettingsScreen(
            state = SettingsUiState(
                language = AppLanguage.ENGLISH,
                currencyCode = "MAD",
                currencyLocked = true,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onSelectLanguage = {},
            onSelectCurrency = {},
            onOpenCategories = {},
            onOpenAbout = {},
            onBackup = {},
            onRestore = {},
        )
    }
}
