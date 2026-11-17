package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AssetClass
import com.example.data.model.Watchlist
import com.example.data.model.WatchlistItem
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

class WatchlistRepository(
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
      ?: throw IllegalStateException("User must be signed in with Google before accessing watchlists.")
  }

  fun observeWatchlists(userId: String): Flow<List<Watchlist>> = flow {
    val path = "users/$userId/watchlists"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("watchlists")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              Watchlist::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedWith(compareByDescending<Watchlist> { it.pinnedToTop }.thenBy { it.sortOrder })
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

  fun observeWatchlistItems(userId: String): Flow<List<WatchlistItem>> = flow {
    val path = "users/$userId/watchlist_items"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("watchlist_items")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              WatchlistItem::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedWith(
              compareByDescending<WatchlistItem> { it.pinnedToTop }.thenBy { it.sortOrder }
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

  suspend fun seedDefaultWatchlistsIfEmpty(userId: String) {
    val uid = requireUserId()
    require(uid == userId) { "Authenticated user mismatch" }
    val watchlistsCol = db.collection("users").document(uid).collection("watchlists")
    val itemsCol = db.collection("users").document(uid).collection("watchlist_items")
    try {
      val existing = watchlistsCol.limit(1).get().await()
      if (!existing.isEmpty) return

      val batch = db.batch()
      val coreRef = watchlistsCol.document("core_markets")
      batch.set(
        coreRef,
        mapOf(
          "userId" to uid,
          "name" to "Core Tech & Indices",
          "pinnedToTop" to true,
          "sortOrder" to 0L,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      val macroRef = watchlistsCol.document("macro_hedge")
      batch.set(
        macroRef,
        mapOf(
          "userId" to uid,
          "name" to "Macro, Crypto & Metals",
          "pinnedToTop" to false,
          "sortOrder" to 1L,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      val coreItems =
        listOf(
          Triple("SPY", "SPDR S&P 500 ETF Trust", AssetClass.ETF),
          Triple("QQQ", "Invesco QQQ Trust", AssetClass.ETF),
          Triple("NVDA", "NVIDIA Corporation", AssetClass.STOCK),
          Triple("AAPL", "Apple Inc.", AssetClass.STOCK),
          Triple("MSFT", "Microsoft Corporation", AssetClass.STOCK),
          Triple("^GSPC", "S&P 500 Index", AssetClass.INDEX),
        )
      coreItems.forEachIndexed { idx, (sym, name, assetClass) ->
        batch.set(
          itemsCol.document("core_${sym.replace("^", "idx_").replace("-", "_")}"),
          mapOf(
            "userId" to uid,
            "watchlistId" to "core_markets",
            "symbol" to sym,
            "displayName" to name,
            "assetClass" to assetClass.wireValue,
            "pinnedToTop" to (idx == 0),
            "sortOrder" to idx.toLong(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      val macroItems =
        listOf(
          Triple("BTC-USD", "Bitcoin USD", AssetClass.CRYPTO),
          Triple("ETH-USD", "Ethereum USD", AssetClass.CRYPTO),
          Triple("GC=F", "Gold Futures", AssetClass.COMMODITY),
          Triple("CL=F", "Crude Oil WTI Futures", AssetClass.COMMODITY),
          Triple("TLT", "iShares 20+ Year Treasury Bond", AssetClass.ETF),
        )
      macroItems.forEachIndexed { idx, (sym, name, assetClass) ->
        batch.set(
          itemsCol.document("macro_${sym.replace("=", "_").replace("-", "_")}"),
          mapOf(
            "userId" to uid,
            "watchlistId" to "macro_hedge",
            "symbol" to sym,
            "displayName" to name,
            "assetClass" to assetClass.wireValue,
            "pinnedToTop" to false,
            "sortOrder" to idx.toLong(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, watchlistsCol.path)
      throw e
    }
  }

  suspend fun createWatchlist(name: String, sortOrder: Long): Result<String> = runCatching {
    val uid = requireUserId()
    val cleanName = name.trim().take(50)
    require(cleanName.isNotEmpty()) { "Watchlist name cannot be empty" }
    val docRef = db.collection("users").document(uid).collection("watchlists").document()
    val payload =
      mapOf(
        "userId" to uid,
        "name" to cleanName,
        "pinnedToTop" to false,
        "sortOrder" to sortOrder.coerceIn(0L, 10000L),
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

  suspend fun renameWatchlist(watchlistId: String, newName: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val cleanName = newName.trim().take(50)
    require(cleanName.isNotEmpty()) { "Watchlist name cannot be empty" }
    val docRef = db.collection("users").document(uid).collection("watchlists").document(watchlistId)
    try {
      docRef.update(mapOf("name" to cleanName, "updatedAt" to FieldValue.serverTimestamp())).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun toggleWatchlistPin(watchlistId: String, pinned: Boolean): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("watchlists").document(watchlistId)
    try {
      docRef.update(mapOf("pinnedToTop" to pinned, "updatedAt" to FieldValue.serverTimestamp())).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteWatchlist(watchlistId: String, itemIds: List<String>): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val userDoc = db.collection("users").document(uid)
      try {
        val batch = db.batch()
        itemIds.forEach { itemId ->
          batch.delete(userDoc.collection("watchlist_items").document(itemId))
        }
        batch.delete(userDoc.collection("watchlists").document(watchlistId))
        batch.commit().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "users/$uid/watchlists/$watchlistId")
        throw e
      }
    }

  suspend fun addWatchlistItem(
    watchlistId: String,
    symbol: String,
    displayName: String,
    assetClass: AssetClass,
    sortOrder: Long,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val cleanSymbol = symbol.trim().uppercase().take(20)
    val cleanName = displayName.trim().take(80).ifEmpty { cleanSymbol }
    require(cleanSymbol.isNotEmpty()) { "Symbol cannot be empty" }
    val docRef = db.collection("users").document(uid).collection("watchlist_items").document()
    val payload =
      mapOf(
        "userId" to uid,
        "watchlistId" to watchlistId,
        "symbol" to cleanSymbol,
        "displayName" to cleanName,
        "assetClass" to assetClass.wireValue,
        "pinnedToTop" to false,
        "sortOrder" to sortOrder.coerceIn(0L, 10000L),
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

  suspend fun toggleItemPin(itemId: String, pinned: Boolean): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("watchlist_items").document(itemId)
    try {
      docRef.update(mapOf("pinnedToTop" to pinned, "updatedAt" to FieldValue.serverTimestamp())).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun reorderWatchlistItems(orderedItemIds: List<String>): Result<Unit> = runCatching {
    val uid = requireUserId()
    val itemsCol = db.collection("users").document(uid).collection("watchlist_items")
    try {
      val batch = db.batch()
      orderedItemIds.forEachIndexed { index, id ->
        batch.update(
          itemsCol.document(id),
          mapOf("sortOrder" to index.toLong(), "updatedAt" to FieldValue.serverTimestamp()),
        )
      }
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, itemsCol.path)
      throw e
    }
  }

  suspend fun removeWatchlistItem(itemId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("watchlist_items").document(itemId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  suspend fun getWatchlistById(watchlistId: String): Result<Watchlist?> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("watchlists").document(watchlistId)
    try {
      docRef.get().await().toObject(Watchlist::class.java)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }
}
