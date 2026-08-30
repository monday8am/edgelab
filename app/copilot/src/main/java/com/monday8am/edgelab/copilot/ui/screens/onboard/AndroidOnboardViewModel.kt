package com.monday8am.edgelab.copilot.ui.screens.onboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monday8am.edgelab.presentation.onboard.OnboardViewModel
import com.monday8am.edgelab.presentation.onboard.UiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Android wrapper ViewModel for Onboard screen. Delegates to platform-agnostic OnboardViewModel
 * from :presentation module. Follows edgelab pattern with stateIn for lifecycle-aware state
 * collection.
 */
class AndroidOnboardViewModel(private val impl: OnboardViewModel) :
    ViewModel(), OnboardViewModel by impl {

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
