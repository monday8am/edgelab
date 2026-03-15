package com.monday8am.edgelab.explorer.ui.screens.modelselector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.edgelab.presentation.modelselector.ModelSelectorViewModel
import com.monday8am.edgelab.presentation.modelselector.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AndroidModelSelectorViewModel(private val impl: ModelSelectorViewModel) :
    ViewModel(), ModelSelectorViewModel by impl {
    override val uiState: StateFlow<UiState> =
        impl.uiState.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState(),
        )

    override fun onCleared() {
        super.onCleared()
        impl.dispose()
    }
}
