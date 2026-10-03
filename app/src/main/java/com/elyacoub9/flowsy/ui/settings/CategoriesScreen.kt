package com.elyacoub9.flowsy.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.domain.category.CategoryRules
import com.elyacoub9.flowsy.ui.common.CategoryBadge
import com.elyacoub9.flowsy.ui.common.CategoryColors
import com.elyacoub9.flowsy.ui.common.ColorPicker
import com.elyacoub9.flowsy.ui.common.ConfirmDialog
import com.elyacoub9.flowsy.ui.common.FormDialog
import com.elyacoub9.flowsy.ui.common.IconPicker
import com.elyacoub9.flowsy.ui.common.displayName
import com.elyacoub9.flowsy.ui.common.nameRes
import com.elyacoub9.flowsy.ui.month.PreviewData
import com.elyacoub9.flowsy.ui.common.ActionButton
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme

private sealed interface CategoryDialog {
    data object Add : CategoryDialog
    data class Edit(val category: Category) : CategoryDialog
}

@Composable
fun CategoriesRoute(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CategoriesScreen(
        state = state,
        onBack = onBack,
        onAdd = viewModel::add,
        onUpdate = viewModel::update,
        onMove = viewModel::move,
        onSetArchived = viewModel::setArchived,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: CategoriesUiState?,
    onBack: () -> Unit,
    onAdd: (CategoryInput) -> Unit,
    onUpdate: (Long, CategoryInput) -> Unit,
    onMove: (Long, Boolean) -> Unit,
    onSetArchived: (Long, Boolean) -> Unit,
    onRequestDelete: (Category) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    var dialog by remember { mutableStateOf<CategoryDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_categories)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = CategoryDialog.Add },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_category)) },
            )
        },
    ) { padding ->
        if (state == null) return@Scaffold
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.active, key = { _, category -> category.id }) { index, category ->
                CategoryRow(
                    category = category,
                    onClick = { dialog = CategoryDialog.Edit(category) },
                    trailing = {
                        val name = category.displayName()
                        IconButton(onClick = { onMove(category.id, true) }, enabled = index > 0) {
                            Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.action_move_up_named, name))
                        }
                        IconButton(onClick = { onMove(category.id, false) }, enabled = index < state.active.lastIndex) {
                            Icon(painterResource(R.drawable.ic_arrow_down), stringResource(R.string.action_move_down_named, name))
                        }
                    },
                )
            }
            if (state.archived.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.categories_archived_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(state.archived, key = { it.id }) { category ->
                    CategoryRow(
                        category = category,
                        onClick = { dialog = CategoryDialog.Edit(category) },
                        trailing = {
                            IconButton(onClick = { onSetArchived(category.id, false) }) {
                                Icon(
                                    painterResource(R.drawable.ic_unarchive),
                                    stringResource(R.string.action_restore_named, category.displayName()),
                                )
                            }
                        },
                    )
                }
            }
        }

        when (val d = dialog) {
            CategoryDialog.Add -> CategoryEditDialog(
                existing = null,
                onSave = { onAdd(it); dialog = null },
                onSetArchived = {},
                onDelete = {},
                onDismiss = { dialog = null },
            )
            is CategoryDialog.Edit -> CategoryEditDialog(
                existing = d.category,
                onSave = { onUpdate(d.category.id, it); dialog = null },
                onSetArchived = { onSetArchived(d.category.id, it); dialog = null },
                onDelete = { onRequestDelete(d.category); dialog = null },
                onDismiss = { dialog = null },
            )
            null -> Unit
        }

        state.deleteRequest?.let { request ->
            val name = request.category.displayName()
            if (request.canDelete) {
                ConfirmDialog(
                    title = stringResource(R.string.dialog_delete_title, name),
                    message = stringResource(R.string.dialog_delete_category_message),
                    confirmRes = R.string.action_delete,
                    onConfirm = onConfirmDelete,
                    onDismiss = onDismissDelete,
                )
            } else {
                ConfirmDialog(
                    title = stringResource(R.string.dialog_cannot_delete_title),
                    message = stringResource(R.string.dialog_cannot_delete_category_message, name),
                    confirmRes = R.string.action_archive,
                    onConfirm = onConfirmDelete,
                    onDismiss = onDismissDelete,
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(category)
                Spacer(Modifier.width(12.dp))
                Text(category.displayName(), style = MaterialTheme.typography.bodyLarge)
            }
            trailing()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditDialog(
    existing: Category?,
    onSave: (CategoryInput) -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val builtIn = existing?.builtIn
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: "other") }
    var colorKey by remember { mutableStateOf(existing?.colorKey ?: CategoryColors.options.first().key) }
    var nameError by remember { mutableStateOf(false) }

    FormDialog(
        title = stringResource(if (existing == null) R.string.dialog_category_add_title else R.string.dialog_category_edit_title),
        onConfirm = {
            nameError = !CategoryRules.isValidName(name, isBuiltIn = builtIn != null)
            if (!nameError) onSave(CategoryInput(name, iconKey, colorKey))
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = false },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.field_name)) },
            // A built-in shows its translated name when the field is left empty.
            placeholder = builtIn?.let { { Text(stringResource(it.nameRes())) } },
            isError = nameError,
            supportingText = when {
                nameError -> { { Text(stringResource(R.string.error_name_required)) } }
                builtIn != null -> { { Text(stringResource(R.string.category_default_name_hint)) } }
                else -> null
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.field_icon), style = MaterialTheme.typography.labelLarge)
        IconPicker(selectedKey = iconKey, onSelect = { iconKey = it })
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.field_color), style = MaterialTheme.typography.labelLarge)
        ColorPicker(selectedKey = colorKey, onSelect = { colorKey = it })
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(iconKey, colorKey)
            Spacer(Modifier.width(12.dp))
            Text(name.ifBlank { builtIn?.let { stringResource(it.nameRes()) }.orEmpty() }, style = MaterialTheme.typography.bodyLarge)
        }
        if (existing != null && !existing.isOther) {
            Row {
                if (existing.archived) {
                    ActionButton(R.drawable.ic_unarchive, R.string.action_restore) { onSetArchived(false) }
                } else if (CategoryRules.canArchive(existing)) {
                    ActionButton(R.drawable.ic_archive, R.string.action_archive) { onSetArchived(true) }
                }
                ActionButton(R.drawable.ic_delete, R.string.action_delete, onDelete)
            }
        }
    }
}

@Preview
@Composable
private fun CategoriesScreenPreview() {
    FlowsyTheme {
        CategoriesScreen(
            state = CategoriesUiState(
                active = PreviewData.categories.drop(1),
                archived = listOf(PreviewData.categories[0].copy(archived = true)),
            ),
            onBack = {}, onAdd = {}, onUpdate = { _, _ -> }, onMove = { _, _ -> }, onSetArchived = { _, _ -> },
            onRequestDelete = {}, onConfirmDelete = {}, onDismissDelete = {},
        )
    }
}
