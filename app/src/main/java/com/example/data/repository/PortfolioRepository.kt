package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AssetClass
import com.example.data.model.PortfolioHolding
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

class PortfolioRepository(
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
      ?: throw IllegalStateException("User must be signed in before accessing portfolio holdings.")
  }

  fun observeHoldings(userId: String): Flow<List<PortfolioHolding>> = flow {
    val path = "users/$userId/portfolio_holdings"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("portfolio_holdings")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              PortfolioHolding::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedBy { it.symbol }
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

  suspend fun addHolding(
    symbol: String,
    displayName: String,
    assetClass: AssetClass,
    shares: Double,
    avgCostBasis: Double,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val cleanSymbol = symbol.trim().uppercase().take(20)
    val cleanName = displayName.trim().take(80).ifEmpty { cleanSymbol }
    require(cleanSymbol.isNotEmpty()) { "Ticker symbol cannot be empty" }
    require(shares > 0.0 && shares <= 100_000_000.0) { "Shares must be positive" }
    require(avgCostBasis >= 0.0 && avgCostBasis <= 10_000_000.0) { "Cost basis cannot be negative" }

    val docRef = db.collection("users").document(uid).collection("portfolio_holdings").document()
    val payload =
      mapOf(
        "userId" to uid,
        "symbol" to cleanSymbol,
        "displayName" to cleanName,
        "assetClass" to assetClass.wireValue,
        "shares" to shares,
        "avgCostBasis" to avgCostBasis,
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

  suspend fun updateHolding(
    holdingId: String,
    displayName: String,
    assetClass: AssetClass,
    shares: Double,
    avgCostBasis: Double,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val cleanName = displayName.trim().take(80)
    require(cleanName.isNotEmpty()) { "Asset name cannot be empty" }
    require(shares > 0.0 && shares <= 100_000_000.0) { "Shares must be positive" }
    require(avgCostBasis >= 0.0 && avgCostBasis <= 10_000_000.0) { "Cost basis cannot be negative" }

    val docRef =
      db.collection("users").document(uid).collection("portfolio_holdings").document(holdingId)
    try {
      docRef
        .update(
          mapOf(
            "displayName" to cleanName,
            "assetClass" to assetClass.wireValue,
            "shares" to shares,
            "avgCostBasis" to avgCostBasis,
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

  suspend fun deleteHolding(holdingId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef =
      db.collection("users").document(uid).collection("portfolio_holdings").document(holdingId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  suspend fun getHoldingById(holdingId: String): Result<PortfolioHolding?> = runCatching {
    val uid = requireUserId()
    val docRef =
      db.collection("users").document(uid).collection("portfolio_holdings").document(holdingId)
    try {
      docRef.get().await().toObject(PortfolioHolding::class.java)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }
}
