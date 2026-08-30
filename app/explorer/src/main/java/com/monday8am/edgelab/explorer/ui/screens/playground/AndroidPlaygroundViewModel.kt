package com.monday8am.edgelab.explorer.ui.screens.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.edgelab.presentation.playground.PlaygroundUiState
import com.monday8am.edgelab.presentation.playground.PlaygroundViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AndroidPlaygroundViewModel(private val impl: PlaygroundViewModel) :
    ViewModel(), PlaygroundViewModel by impl {

    override val uiState: StateFlow<PlaygroundUiState> =
        impl.uiState.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = PlaygroundUiState(),
        )

    override fun onCleared() {
        super.onCleared()
        impl.dispose()
    }
}
