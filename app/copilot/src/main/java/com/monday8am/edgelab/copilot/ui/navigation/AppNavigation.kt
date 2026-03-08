package com.monday8am.edgelab.copilot.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.monday8am.edgelab.copilot.Dependencies
import com.monday8am.edgelab.copilot.ui.screens.liveride.LiveRideScreen
import com.monday8am.edgelab.copilot.ui.screens.onboard.AndroidOnboardViewModel
import com.monday8am.edgelab.copilot.ui.screens.onboard.OnboardScreen
import com.monday8am.edgelab.copilot.ui.screens.ridesetup.RideSetupScreen
import com.monday8am.edgelab.presentation.onboard.OnboardViewModelImpl
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    onboardViewModel: AndroidOnboardViewModel =
        viewModel {
            AndroidOnboardViewModel(
                OnboardViewModelImpl(
                    modelDownloadManager = Dependencies.modelDownloadManager,
                    authRepository = Dependencies.authRepository,
                )
            )
        },
) {

    val startDestination by
        produceState<Route?>(initialValue = null) {
            value =
                onboardViewModel.uiState
                    .filter { it.models.isNotEmpty() }
                    .map { state ->
                        if (state.isLoggedIn && state.models.all { it.isDownloaded }) Route.RideSetup
                        else Route.Onboard
                    }
                    .first()
        }

    if (startDestination == null) return

    NavHost(
        navController = navController,
        startDestination = startDestination!!,
        modifier = modifier,
    ) {
        composable<Route.Onboard> {
            OnboardScreen(
                onNavigateToSetup = { navController.navigate(Route.RideSetup) }
            )
        }

        composable<Route.RideSetup> {
            RideSetupScreen(
                onNavigateToLiveRide = { routeId, speed ->
                    navController.navigate(Route.LiveRide(routeId, speed))
                }
            )
        }

        composable<Route.LiveRide> { backStackEntry ->
            val args = backStackEntry.toRoute<Route.LiveRide>()
            LiveRideScreen(
                routeId = args.routeId,
                playbackSpeed = args.playbackSpeed,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
