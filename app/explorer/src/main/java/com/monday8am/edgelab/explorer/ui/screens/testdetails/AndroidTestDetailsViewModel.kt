package com.monday8am.edgelab.explorer.ui.screens.testdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.edgelab.data.testing.TestDomain
import com.monday8am.edgelab.presentation.testdetails.TestDetailsUiState
import com.monday8am.edgelab.presentation.testdetails.TestDetailsViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AndroidTestDetailsViewModel(
    private val impl: TestDetailsViewModel,
) : ViewModel(), TestDetailsViewModel by impl {

    override val uiState: StateFlow<TestDetailsUiState> =
        impl.uiState.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue =
                TestDetailsUiState(
                    tests = persistentListOf(),
                    availableDomains = persistentListOf(),
                    filterDomain = null,
                    isLoading = true,
                ),
        )

    override fun onCleared() {
        super.onCleared()
        impl.dispose()
    }
}
