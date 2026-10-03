package com.elyacoub9.flowsy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elyacoub9.flowsy.container
import com.elyacoub9.flowsy.data.CategoryRepository
import com.elyacoub9.flowsy.domain.category.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [canDelete] false: the category has expenses and can only be archived. */
data class CategoryDeleteRequest(val category: Category, val canDelete: Boolean)

data class CategoriesUiState(
    val active: List<Category>,
    val archived: List<Category>,
    val deleteRequest: CategoryDeleteRequest? = null,
)

/** Values from the category dialog, already validated. */
data class CategoryInput(
    val name: String,
    val iconKey: String,
    val colorKey: String,
)

class CategoriesViewModel(
    private val repository: CategoryRepository,
) : ViewModel() {

    private val deleteRequest = MutableStateFlow<CategoryDeleteRequest?>(null)

    val state: StateFlow<CategoriesUiState?> = combine(repository.observeAll(), deleteRequest) { categories, request ->
        CategoriesUiState(
            active = categories.filterNot { it.archived },
            archived = categories.filter { it.archived },
            deleteRequest = request,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(input: CategoryInput) = viewModelScope.launch {
        repository.add(input.name, input.iconKey, input.colorKey)
    }

    fun update(id: Long, input: CategoryInput) = viewModelScope.launch {
        repository.update(id, input.name, input.iconKey, input.colorKey)
    }

    fun move(id: Long, up: Boolean) = viewModelScope.launch { repository.move(id, up) }

    fun setArchived(id: Long, archived: Boolean) = viewModelScope.launch { repository.setArchived(id, archived) }

    fun requestDelete(category: Category) = viewModelScope.launch {
        deleteRequest.value = CategoryDeleteRequest(category, repository.canDelete(category.id))
    }

    /** Deletes, or archives when the category has expenses. */
    fun confirmDelete() = viewModelScope.launch {
        val request = deleteRequest.value ?: return@launch
        deleteRequest.value = null
        if (request.canDelete) repository.delete(request.category.id)
        else repository.setArchived(request.category.id, true)
    }

    fun dismissDelete() {
        deleteRequest.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container()
                CategoriesViewModel(c.categoryRepository)
            }
        }
    }
}
