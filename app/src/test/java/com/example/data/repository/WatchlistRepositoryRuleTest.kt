package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AssetClass
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createWatchlistAndAddItem_authenticatedOwner_succeedsAndObservesRealtime() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = WatchlistRepository(firestore, auth)

    val wlName = "Momentum_${UUID.randomUUID().toString().take(6)}"
    val wlId = withTimeout(DEFAULT_TIMEOUT_MS) { repo.createWatchlist(wlName, 0L).getOrThrow() }
    assertTrue(wlId.isNotBlank())

    val itemId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.addWatchlistItem(wlId, "NVDA", "NVIDIA Corp.", AssetClass.STOCK, 0L).getOrThrow()
      }
    assertTrue(itemId.isNotBlank())

    val watchlists =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeWatchlists(uid).first { list -> list.any { it.id == wlId } }
      }
    assertTrue(watchlists.any { it.id == wlId && it.name == wlName })

    val items =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeWatchlistItems(uid).first { list -> list.any { it.id == itemId } }
      }
    assertTrue(items.any { it.id == itemId && it.symbol == "NVDA" })
  }

  @Test
  fun observeWatchlists_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = WatchlistRepository(firestore, auth)

    var thrown: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeWatchlists("some_uid_123").first() }
    } catch (e: Exception) {
      thrown = e
    }
    assertNotNull("Expected PERMISSION_DENIED exception", thrown)
    val firestoreException = thrown!!.unwrapFirestoreException()
    assertNotNull("Expected nested FirebaseFirestoreException, got $thrown", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
  }

  @Test
  fun crossUserRead_differentUser_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = WatchlistRepository(firestore, auth)
    val wlId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo.createWatchlist("Alice Secret_${UUID.randomUUID().toString().take(4)}", 0L)
          .getOrThrow()
      }

    signInTestUser(BOB_EMAIL)
    val bobRepo = WatchlistRepository(firestore, auth)
    var thrown: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { bobRepo.observeWatchlists(aliceUid).first() }
    } catch (e: Exception) {
      thrown = e
    }
    assertNotNull("Expected PERMISSION_DENIED for cross-user read", thrown)
    val firestoreException = thrown!!.unwrapFirestoreException()
    assertNotNull("Expected nested FirebaseFirestoreException, got $thrown", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)

    // Verify Alice can still read her own watchlist
    signInTestUser(ALICE_EMAIL)
    val fetched = withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.getWatchlistById(wlId).getOrThrow() }
    assertNotNull(fetched)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_mp@test.com"
    const val BOB_EMAIL = "bob_mp@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
