package cast.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cast.android.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base for screens that load a single value into a [UiState]. Centralizes the shared loading rules:
 * show [UiState.Loading] only on a cold start, keep already-loaded data visible while a refresh runs,
 * and surface [UiState.Error] only when there is nothing to fall back to.
 */
abstract class LoadableViewModel<T>(initial: UiState<T>) : ViewModel() {

    protected val _uiState = MutableStateFlow(initial)
    val uiState: StateFlow<UiState<T>> = _uiState.asStateFlow()

    protected fun load(errorMessage: String, fetch: suspend () -> T) {
        viewModelScope.launch {
            if (_uiState.value !is UiState.Success) _uiState.value = UiState.Loading
            _uiState.value = try {
                UiState.Success(fetch())
            } catch (e: Exception) {
                if (_uiState.value is UiState.Success) _uiState.value
                else UiState.Error(e.message ?: errorMessage)
            }
        }
    }
}
