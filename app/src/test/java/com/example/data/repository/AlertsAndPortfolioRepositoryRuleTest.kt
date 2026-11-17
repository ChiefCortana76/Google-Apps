package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AlertCondition
import com.example.data.model.AssetClass
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsAndPortfolioRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createAndTriggerPriceAlert_authenticatedOwner_succeedsAndObservesRealtime() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = AlertsRepository(firestore, auth)

    val alertId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createPriceAlert("NVDA", "NVIDIA Corp.", AlertCondition.ABOVE, 145.5).getOrThrow()
      }
    assertTrue(alertId.isNotBlank())

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.markAlertTriggered(alertId, 146.2).getOrThrow()
    }

    val observed =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observePriceAlerts(uid).first { list ->
          list.any { it.id == alertId && it.triggered }
        }
      }
    val item = observed.first { it.id == alertId }
    assertEquals("NVDA", item.symbol)
    assertTrue(item.triggered)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.upsertFcmToken("sample_device_token_abcdefghij").getOrThrow()
    }
  }

  @Test
  fun addAndUpdatePortfolioHolding_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = PortfolioRepository(firestore, auth)

    val holdingId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.addHolding("AAPL", "Apple Inc.", AssetClass.STOCK, 15.0, 210.25).getOrThrow()
      }
    assertTrue(holdingId.isNotBlank())

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateHolding(holdingId, "Apple Inc.", AssetClass.STOCK, 20.0, 214.5).getOrThrow()
    }

    val holdings =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeHoldings(uid).first { list ->
          list.any { it.id == holdingId && it.shares == 20.0 }
        }
      }
    assertTrue(holdings.any { it.id == holdingId && it.avgCostBasis == 214.5 })
  }

  @Test
  fun unauthenticatedAndCrossUserReads_failWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceAlerts = AlertsRepository(firestore, auth)
    val alicePortfolio = PortfolioRepository(firestore, auth)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceAlerts.createPriceAlert("SPY", "SPDR S&P 500", AlertCondition.BELOW, 550.0).getOrThrow()
      alicePortfolio.addHolding("QQQ", "Invesco QQQ", AssetClass.ETF, 5.0, 480.0).getOrThrow()
    }

    // Unauthenticated denied
    auth.signOut()
    var unauthThrown: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { alicePortfolio.observeHoldings(aliceUid).first() }
    } catch (e: Exception) {
      unauthThrown = e
    }
    assertNotNull("Expected PERMISSION_DENIED for unauthenticated read", unauthThrown)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      unauthThrown!!.unwrapFirestoreException()?.code,
    )

    // Cross-user denied
    signInTestUser(BOB_EMAIL)
    val bobAlerts = AlertsRepository(firestore, auth)
    var crossThrown: Exception? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { bobAlerts.observePriceAlerts(aliceUid).first() }
    } catch (e: Exception) {
      crossThrown = e
    }
    assertNotNull("Expected PERMISSION_DENIED for cross-user alerts read", crossThrown)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      crossThrown!!.unwrapFirestoreException()?.code,
    )
  }

  private companion object {
    const val ALICE_EMAIL = "alice_alerts@test.com"
    const val BOB_EMAIL = "bob_alerts@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
