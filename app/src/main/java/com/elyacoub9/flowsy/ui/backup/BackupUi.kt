package com.elyacoub9.flowsy.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.data.AppLanguage
import com.elyacoub9.flowsy.data.LanguageManager
import com.elyacoub9.flowsy.data.backup.BackupRepository
import com.elyacoub9.flowsy.data.backup.DocumentStore
import com.elyacoub9.flowsy.domain.backup.BackupData
import com.elyacoub9.flowsy.domain.backup.BackupException
import com.elyacoub9.flowsy.domain.backup.BackupFileName
import com.elyacoub9.flowsy.domain.backup.BackupPreview
import com.elyacoub9.flowsy.domain.backup.BackupProblem
import com.elyacoub9.flowsy.ui.common.currentLocale
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency

sealed interface RestoreState {
    data object Idle : RestoreState
    data object Working : RestoreState
    data class Preview(val preview: BackupPreview, val data: BackupData) : RestoreState
    data class Refused(val problem: BackupProblem) : RestoreState
    data object Failed : RestoreState
}

/**
 * Restore in two steps: [check] reads and validates the whole file (nothing is written), then
 * [confirm] replaces all data in one transaction, applies the backup's language and restarts the screens.
 * Shared by Settings and onboarding.
 */
class RestoreController(
    private val repository: BackupRepository,
    private val documents: DocumentStore,
    private val languageManager: LanguageManager,
    private val restoreCount: MutableStateFlow<Int>,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val state: StateFlow<RestoreState> = _state.asStateFlow()

    fun check(uri: Uri) {
        _state.value = RestoreState.Working
        scope.launch {
            _state.value = try {
                val data = documents.openInput(uri).use { repository.read(it) }
                RestoreState.Preview(BackupPreview.of(data), data)
            } catch (e: BackupException) {
                RestoreState.Refused(e.problem)
            } catch (e: IOException) {
                RestoreState.Refused(BackupProblem.CORRUPT)
            } catch (e: IllegalArgumentException) {
                RestoreState.Refused(BackupProblem.CORRUPT)
            }
        }
    }

    fun confirm() {
        val preview = _state.value as? RestoreState.Preview ?: return
        _state.value = RestoreState.Working
        scope.launch {
            try {
                repository.replaceAll(preview.data)
            } catch (e: Exception) {
                // The transaction rolled back: the current data is untouched.
                _state.value = RestoreState.Failed
                return@launch
            }
            _state.value = RestoreState.Idle
            applyLanguage(preview.data.settings.single().languageTag)
            restoreCount.update { it + 1 }
        }
    }

    fun dismiss() {
        _state.value = RestoreState.Idle
    }

    private fun applyLanguage(tag: String?) {
        val language = languageFor(tag)
        if (language == null) languageManager.followSystem() else languageManager.apply(language)
    }

    companion object {
        /** Language to apply after a restore; null (blank, missing or unknown tag) = follow the phone. */
        fun languageFor(tag: String?): AppLanguage? = tag?.takeIf { it.isNotBlank() }?.let(AppLanguage::fromTag)
    }
}

sealed interface BackupEvent {
    data object Saved : BackupEvent
    data object Failed : BackupEvent
}

/** Writes a backup to a file chosen by the user; a failed write deletes the partial file. */
class BackupController(
    private val repository: BackupRepository,
    private val documents: DocumentStore,
    private val scope: CoroutineScope,
) {
    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = _events.receiveAsFlow()

    fun export(uri: Uri) {
        scope.launch {
            val ok = try {
                documents.openOutput(uri).use { repository.export(it) }
                true
            } catch (e: Exception) {
                documents.delete(uri)
                false
            }
            _events.send(if (ok) BackupEvent.Saved else BackupEvent.Failed)
        }
    }
}

/** Opens the system "save as" screen with today's file name. */
@Composable
fun rememberBackupLauncher(onPicked: (Uri) -> Unit): (LocalDate) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(onPicked)
    }
    return { today -> launcher.launch(BackupFileName.forDate(today)) }
}

/** Opens the system file picker for a backup zip. */
@Composable
fun rememberRestoreLauncher(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onPicked) }
    return { launcher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
}

/** Dialogs for every restore step: working, preview with "Replace all data", refused, failed. */
@Composable
fun RestoreDialogs(state: RestoreState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        RestoreState.Idle -> Unit
        RestoreState.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.restore_working))
                }
            },
        )
        is RestoreState.Preview -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.restore_title)) },
            text = { PreviewContent(state.preview) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.action_replace_all), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        is RestoreState.Refused -> MessageDialog(R.string.restore_refused_title, problemMessage(state.problem), onDismiss)
        RestoreState.Failed -> MessageDialog(R.string.restore_refused_title, R.string.restore_failed, onDismiss)
    }
}

@Composable
private fun PreviewContent(preview: BackupPreview) {
    val locale = currentLocale()
    val date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
        .format(preview.exportDate.atZone(ZoneId.systemDefault()))
    val currency = Currency.getInstance(preview.currencyCode)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.restore_preview_date, date))
        Text(stringResource(R.string.restore_preview_currency, "${currency.currencyCode} — ${currency.getDisplayName(locale)}"))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.restore_count_income, preview.income))
        Text(stringResource(R.string.restore_count_expenses, preview.expenses))
        Text(stringResource(R.string.restore_count_categories, preview.categories))
        Text(stringResource(R.string.restore_count_pots, preview.pots))
        Text(stringResource(R.string.restore_count_movements, preview.movements))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.restore_warning), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MessageDialog(@StringRes title: Int, @StringRes message: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@StringRes
private fun problemMessage(problem: BackupProblem): Int = when (problem) {
    BackupProblem.CORRUPT -> R.string.restore_error_corrupt
    BackupProblem.MISSING_FILE -> R.string.restore_error_missing
    BackupProblem.TOO_LARGE -> R.string.restore_error_too_large
    BackupProblem.UNKNOWN_FORMAT -> R.string.restore_error_format
    BackupProblem.NEWER_SCHEMA -> R.string.restore_error_newer
    BackupProblem.TOO_OLD_SCHEMA -> R.string.restore_error_too_old
    BackupProblem.SETTINGS -> R.string.restore_error_settings
    BackupProblem.CURRENCY -> R.string.restore_error_currency
    BackupProblem.DUPLICATE_ID -> R.string.restore_error_duplicate
    BackupProblem.BROKEN_LINK -> R.string.restore_error_link
    BackupProblem.AMOUNT -> R.string.restore_error_amount
    BackupProblem.DATE -> R.string.restore_error_date
    BackupProblem.NEGATIVE_POT -> R.string.restore_error_pot
    BackupProblem.BUILT_IN_KEYS -> R.string.restore_error_built_in
    BackupProblem.OTHER_MISSING -> R.string.restore_error_other
    BackupProblem.CATEGORY_NAME -> R.string.restore_error_category_name
}

@Preview
@Composable
private fun RestorePreviewDialogPreview() {
    FlowsyTheme {
        Surface {
            PreviewContent(BackupPreview(Instant.parse("2026-10-12T10:00:00Z"), "MAD", 11, 7, 4, 2, 5))
        }
    }
}
