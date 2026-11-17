package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AlertCondition
import com.example.data.model.PriceAlert
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AlertsRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in before managing price alerts.")
  }

  fun observePriceAlerts(userId: String): Flow<List<PriceAlert>> = flow {
    val path = "users/$userId/price_alerts"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("price_alerts")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              PriceAlert::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedWith(
              compareByDescending<PriceAlert> { it.enabled && !it.triggered }
                .thenBy { it.triggered }
                .thenBy { it.symbol }
            )
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val toLog = firestoreError ?: (error as? Exception)
          if (toLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(toLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun createPriceAlert(
    symbol: String,
    displayName: String,
    condition: AlertCondition,
    targetPrice: Double,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val cleanSymbol = symbol.trim().uppercase().take(20)
    val cleanName = displayName.trim().take(80).ifEmpty { cleanSymbol }
    require(cleanSymbol.isNotEmpty()) { "Ticker symbol cannot be empty" }
    require(targetPrice > 0.0 && targetPrice <= 10_000_000.0) { "Target price must be greater than zero" }

    val docRef = db.collection("users").document(uid).collection("price_alerts").document()
    val payload =
      mapOf(
        "userId" to uid,
        "symbol" to cleanSymbol,
        "displayName" to cleanName,
        "condition" to condition.wireValue,
        "targetPrice" to targetPrice,
        "enabled" to true,
        "triggered" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun toggleAlertEnabled(alertId: String, enabled: Boolean): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("price_alerts").document(alertId)
    try {
      docRef.update(mapOf("enabled" to enabled, "updatedAt" to FieldValue.serverTimestamp())).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun rearmAlert(
    alertId: String,
    condition: AlertCondition,
    targetPrice: Double,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(targetPrice > 0.0 && targetPrice <= 10_000_000.0) { "Target price must be greater than zero" }
    val docRef = db.collection("users").document(uid).collection("price_alerts").document(alertId)
    try {
      docRef
        .update(
          mapOf(
            "condition" to condition.wireValue,
            "targetPrice" to targetPrice,
            "enabled" to true,
            "triggered" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun markAlertTriggered(alertId: String, currentPrice: Double): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(currentPrice >= 0.0 && currentPrice <= 10_000_000.0) { "Invalid price" }
    val docRef = db.collection("users").document(uid).collection("price_alerts").document(alertId)
    try {
      docRef
        .update(
          mapOf(
            "triggered" to true,
            "lastTriggeredPrice" to currentPrice,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deletePriceAlert(alertId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("price_alerts").document(alertId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  suspend fun getAlertById(alertId: String): Result<PriceAlert?> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("price_alerts").document(alertId)
    try {
      docRef.get().await().toObject(PriceAlert::class.java)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }

  suspend fun upsertFcmToken(token: String, docId: String = "android_primary"): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val cleanToken = token.trim().take(512)
      require(cleanToken.length >= 10) { "Invalid FCM device token" }
      val docRef = db.collection("users").document(uid).collection("fcm_tokens").document(docId)
      try {
        val existing = docRef.get().await()
        if (existing.exists()) {
          docRef
            .update(
              mapOf(
                "token" to cleanToken,
                "platform" to "android",
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        } else {
          docRef
            .set(
              mapOf(
                "userId" to uid,
                "token" to cleanToken,
                "platform" to "android",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, docRef.path)
        throw e
      }
    }
}
