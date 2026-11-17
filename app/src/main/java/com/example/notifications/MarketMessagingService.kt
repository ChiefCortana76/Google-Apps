package com.example.notifications

import android.util.Log
import com.example.data.repository.AlertsRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MarketMessagingService : FirebaseMessagingService() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    val user = Firebase.auth.currentUser
    if (user != null) {
      serviceScope.launch {
        runCatching {
          AlertsRepository(applicationContext).upsertFcmToken(token).getOrThrow()
        }.onFailure { error ->
          Log.w(TAG, "Failed syncing refreshed FCM token", error)
        }
      }
    }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    val title =
      message.notification?.title
        ?: message.data["title"]
        ?: message.data["symbol"]?.let { "$it Price Alert" }
        ?: "Squawk on the Street Price Alert"
    val body =
      message.notification?.body
        ?: message.data["body"]
        ?: "A price threshold on your watchlist has been triggered."

    val notifId = (message.messageId?.hashCode() ?: System.currentTimeMillis().toInt())
    PriceAlertNotificationHelper.showPriceAlertNotification(
      context = applicationContext,
      notificationId = notifId,
      title = title,
      body = body,
    )
  }

  override fun onDestroy() {
    super.onDestroy()
    serviceScope.cancel()
  }

  private companion object {
    const val TAG = "MarketMessagingSvc"
  }
}
