package com.monday8am.edgelab.explorer.ui.screens.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.edgelab.data.model.ModelConfiguration
import com.monday8am.edgelab.presentation.testing.TestUiState
import com.monday8am.edgelab.presentation.testing.TestViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AndroidTestViewModel(
    private val impl: TestViewModel,
    private val selectedModel: ModelConfiguration,
) : ViewModel(), TestViewModel by impl {
    override val uiState: StateFlow<TestUiState> =
        impl.uiState.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = TestUiState(selectedModel = selectedModel),
        )

    override fun onCleared() {
        super.onCleared()
        impl.dispose()
    }
}
