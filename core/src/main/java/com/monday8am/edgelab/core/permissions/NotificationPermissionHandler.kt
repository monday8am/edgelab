package com.monday8am.edgelab.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * Screen-scoped helper that gates an action behind the POST_NOTIFICATIONS runtime permission on
 * Android 13+.
 *
 * Wire it to an [ActivityResultLauncher] created in your Composable, then call [request] before
 * the guarded action.
 */
class NotificationPermissionHandler(
    private val context: Context,
    private val onDenied: (() -> Unit)? = null,
) {
    private var pendingAction: (() -> Unit)? = null
    private var launcher: ActivityResultLauncher<String>? = null

    fun attachLauncher(l: ActivityResultLauncher<String>) {
        launcher = l
    }

    fun detachLauncher() {
        launcher = null
    }

    /** Request permission if needed, then run [action]. */
    fun request(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            action()
            return
        }
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
            return
        }
        pendingAction = action
        launcher?.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Call this from the ActivityResult callback. */
    fun onPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            pendingAction?.invoke()
        } else {
            onDenied?.invoke()
        }
        pendingAction = null
    }
}
