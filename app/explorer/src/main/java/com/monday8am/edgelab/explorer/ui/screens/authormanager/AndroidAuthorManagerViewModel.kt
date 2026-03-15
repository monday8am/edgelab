package com.monday8am.edgelab.explorer.ui.screens.authormanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.edgelab.presentation.authormanager.AuthorManagerViewModel
import com.monday8am.edgelab.presentation.authormanager.AuthorUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AndroidAuthorManagerViewModel(private val impl: AuthorManagerViewModel) :
    ViewModel(), AuthorManagerViewModel by impl {
    override val uiState: StateFlow<AuthorUiState> =
        impl.uiState.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AuthorUiState(),
        )

    override fun onCleared() {
        super.onCleared()
        impl.dispose()
    }
}
