package com.arttvad9r.mealio.ui.screens.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arttvad9r.mealio.data.repository.ShoppingRepository
import com.arttvad9r.mealio.domain.model.ShoppingItem
import com.arttvad9r.mealio.domain.model.ShoppingListDetail
import com.arttvad9r.mealio.domain.model.ShoppingListSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShoppingUiState(
    val lists: List<ShoppingListSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: Throwable? = null,
)

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    private val _state = MutableStateFlow(ShoppingUiState())
    val state: StateFlow<ShoppingUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = it.lists.isEmpty(), error = null) }
        viewModelScope.launch {
            runCatching { repository.lists() }
                .onSuccess { lists ->
                    _state.update { it.copy(lists = lists, isLoading = false, isRefreshing = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, isRefreshing = false, error = e) }
                }
        }
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        load()
    }
}

data class ShoppingListDetailUiState(
    val list: ShoppingListDetail? = null,
    val isLoading: Boolean = true,
    val error: Throwable? = null,
)

class ShoppingListDetailViewModel(
    private val repository: ShoppingRepository,
    private val listId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ShoppingListDetailUiState())
    val state: StateFlow<ShoppingListDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = it.list == null, error = null) }
        viewModelScope.launch {
            runCatching { repository.list(listId) }
                .onSuccess { detail -> _state.update { it.copy(list = detail, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e) } }
        }
    }

    fun toggle(item: ShoppingItem) {
        val newChecked = !item.checked
        applyLocalCheck(item.id, newChecked)
        viewModelScope.launch {
            runCatching { repository.setChecked(item, newChecked) }
                .onFailure {
                    // revert on failure
                    applyLocalCheck(item.id, item.checked)
                }
        }
    }

    private fun applyLocalCheck(itemId: String, checked: Boolean) {
        _state.update { current ->
            val list = current.list ?: return@update current
            current.copy(
                list = list.copy(
                    items = list.items.map {
                        if (it.id == itemId) it.copy(checked = checked) else it
                    },
                ),
            )
        }
    }
}
