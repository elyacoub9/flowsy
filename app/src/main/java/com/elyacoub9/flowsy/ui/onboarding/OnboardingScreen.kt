package com.elyacoub9.flowsy.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.data.AppLanguage
import com.elyacoub9.flowsy.ui.backup.RestoreDialogs
import com.elyacoub9.flowsy.ui.backup.rememberRestoreLauncher
import com.elyacoub9.flowsy.ui.common.ActionButton
import com.elyacoub9.flowsy.ui.common.CurrencyList
import com.elyacoub9.flowsy.ui.common.LanguagePicker
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme

@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restoreState by viewModel.restore.state.collectAsStateWithLifecycle()
    val restoreLauncher = rememberRestoreLauncher(viewModel.restore::check)
    BackHandler(enabled = state.step != OnboardingStep.LANGUAGE) { viewModel.back() }
    OnboardingScreen(
        state = state,
        onSelectLanguage = viewModel::selectLanguage,
        onCurrencyQuery = viewModel::updateCurrencyQuery,
        onSelectCurrency = viewModel::selectCurrency,
        onBack = { viewModel.back() },
        onNext = viewModel::next,
        onFinish = viewModel::finish,
        onRestore = restoreLauncher,
    )
    RestoreDialogs(restoreState, onConfirm = viewModel.restore::confirm, onDismiss = viewModel.restore::dismiss)
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onSelectLanguage: (AppLanguage) -> Unit,
    onCurrencyQuery: (String) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onRestore: () -> Unit,
) {
    val steps = OnboardingStep.entries
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.size(16.dp))
        Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.onboarding_step, state.step.ordinal + 1, steps.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(24.dp))

        Box(Modifier.weight(1f)) {
            when (state.step) {
                OnboardingStep.LANGUAGE -> LanguageStep(state.language, onSelectLanguage, onRestore)
                OnboardingStep.CURRENCY -> CurrencyStep(
                    selectedCode = state.currencyCode,
                    query = state.currencyQuery,
                    onQuery = onCurrencyQuery,
                    onSelect = onSelectCurrency,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.step != OnboardingStep.LANGUAGE) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.action_back)) }
            }
            val isLast = state.step == steps.last()
            Button(
                onClick = if (isLast) onFinish else onNext,
                enabled = !state.saving,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) { Text(stringResource(if (isLast) R.string.action_start else R.string.action_next)) }
        }
    }
}

@Composable
private fun StepTitle(title: String, hint: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    if (hint != null) {
        Spacer(Modifier.size(4.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.size(16.dp))
}

@Composable
private fun LanguageStep(selected: AppLanguage, onSelect: (AppLanguage) -> Unit, onRestore: () -> Unit) {
    Column {
        StepTitle(stringResource(R.string.onboarding_language_title))
        LanguagePicker(selected = selected, onSelect = onSelect)
        Spacer(Modifier.size(24.dp))
        // New phone: restore everything from a backup instead of starting empty.
        ActionButton(R.drawable.ic_unarchive, R.string.action_restore_backup, onRestore)
    }
}

@Composable
private fun CurrencyStep(
    selectedCode: String,
    query: String,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    Column {
        StepTitle(
            stringResource(R.string.onboarding_currency_title),
            stringResource(R.string.onboarding_currency_hint),
        )
        CurrencyList(selectedCode, query, onQuery, onSelect)
    }
}

@Composable
private fun OnboardingPreview(step: OnboardingStep) {
    FlowsyTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            OnboardingScreen(
                state = OnboardingUiState(
                    step = step,
                    language = AppLanguage.ENGLISH,
                    currencyCode = "MAD",
                ),
                onSelectLanguage = {}, onCurrencyQuery = {}, onSelectCurrency = {},
                onBack = {}, onNext = {}, onFinish = {}, onRestore = {},
            )
        }
    }
}

@Preview
@Composable
private fun OnboardingLanguagePreview() = OnboardingPreview(OnboardingStep.LANGUAGE)

@Preview
@Composable
private fun OnboardingCurrencyPreview() = OnboardingPreview(OnboardingStep.CURRENCY)

