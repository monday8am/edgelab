# Add Cancel Button and Tap Handling to Download Notification

## Changes Required

### 1. CREATE: `core/src/main/java/com/monday8am/edgelab/core/download/CancelDownloadReceiver.kt`

```kotlin
package com.monday8am.edgelab.core.download

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

class CancelDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        WorkManager.getInstance(context).cancelUniqueWork("model-download-$modelId")

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    companion object {
        const val ACTION = "com.monday8am.edgelab.CANCEL_DOWNLOAD"
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
```

### 2. MODIFY: `core/src/main/AndroidManifest.xml`

Replace the current content:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <receiver
            android:name=".download.CancelDownloadReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="com.monday8am.edgelab.CANCEL_DOWNLOAD" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

### 3. MODIFY: `core/src/main/java/com/monday8am/edgelab/core/download/DownloadWorker.kt`

In `createForegroundInfo()`:

**Add imports** at top:
```kotlin
import android.app.PendingIntent
import android.content.Intent
import androidx.work.WorkManager
```

**Modify the builder chain** (lines 133-141). Replace the current `build()` call with:

```kotlin
val notification =
    NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
        .setContentTitle("Downloading model")
        .setContentText(if (progress < 100) "$progress%" else "Download complete")
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setProgress(100, progress, progress == 0)
        .setOngoing(progress < 100)
        .setOnlyAlertOnce(true)
        .also { builder ->
            // Tap to foreground
            val launchIntent = applicationContext.packageManager
                .getLaunchIntentForPackage(applicationContext.packageName)
                ?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            if (launchIntent != null) {
                val contentPendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                builder.setContentIntent(contentPendingIntent)
            }

            // Cancel button (only during download, not on "Download complete")
            if (progress < 100) {
                val cancelIntent =
                    Intent(applicationContext, CancelDownloadReceiver::class.java).apply {
                        action = CancelDownloadReceiver.ACTION
                        putExtra(CancelDownloadReceiver.EXTRA_MODEL_ID, modelId)
                        putExtra(CancelDownloadReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                    }
                val cancelPendingIntent =
                    PendingIntent.getBroadcast(
                        applicationContext,
                        notificationId,
                        cancelIntent,
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                builder.addAction(
                    android.R.drawable.ic_delete,
                    "Cancel",
                    cancelPendingIntent,
                )
            }
        }
        .build()
```

## After Implementation

1. Run formatting: `./gradlew ktfmtFormat`
2. Verify build: `./gradlew :core:assembleDebug`
3. Stage all: `git add -A`
4. Commit and push

## PR Branch

`feature/add-cancel-download-to-notif` already exists.

## Suggested commit message

```
Add Cancel button and tap-to-foreground to download notification

- CancelDownloadReceiver cancels the specific WorkManager work and
  dismisses the notification on receiving the cancel action
- Notification includes a Cancel action button (hidden when done)
- Tapping the notification brings the app to foreground via
  getLaunchIntentForPackage
- Receiver registered in core module manifest (auto-merged into
  both Explorer and Copilot apps)
```
