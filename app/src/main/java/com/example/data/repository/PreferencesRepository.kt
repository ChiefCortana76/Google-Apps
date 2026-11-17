package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.R
import com.example.audio.BundledAlarmTone
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.RefreshInterval
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferencesDoc
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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "market_pulse_prefs")

val DEFAULT_BOTTOM_BAR_ORDER =
  listOf("dashboard", "markets", "futures", "watchlists", "calendar", "news", "breadth")

data class AppPreferences(
  val themeMode: ThemeMode = ThemeMode.MARKET_AUTO,
  val marketOpenMinutesEt: Int = 570, // 09:30 ET
  val marketCloseMinutesEt: Int = 960, // 16:00 ET
  val refreshInterval: RefreshInterval = RefreshInterval.SEC_30,
  val alarmEnabled: Boolean = true,
  val alarmToneId: String = BundledAlarmTone.OPENING_BELL.id,
  val alarmCustomUri: String? = null,
  val alarmCustomLabel: String? = null,
  val bottomBarVisible: Boolean = true,
  val bottomBarTabOrder: List<String> = DEFAULT_BOTTOM_BAR_ORDER,
  val hiddenBottomBarTabs: Set<String> = emptySet(),
)

class PreferencesRepository(
  private val context: Context,
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    context.applicationContext,
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth,
  )

  private val dataStore = context.applicationContext.dataStore

  val localPreferences: Flow<AppPreferences> =
    dataStore.data.map { prefs ->
      val savedOrder =
        prefs[KEY_BOTTOM_BAR_ORDER]
          ?.split(",")
          ?.map { it.trim() }
          ?.filter { it.isNotEmpty() }
          ?.takeIf { it.isNotEmpty() }
          ?: DEFAULT_BOTTOM_BAR_ORDER
      AppPreferences(
        themeMode = ThemeMode.fromWire(prefs[KEY_THEME_MODE] ?: ThemeMode.MARKET_AUTO.wireValue),
        marketOpenMinutesEt = (prefs[KEY_MARKET_OPEN] ?: 570).coerceIn(0, 1439),
        marketCloseMinutesEt = (prefs[KEY_MARKET_CLOSE] ?: 960).coerceIn(0, 1439),
        refreshInterval = RefreshInterval.fromSeconds(prefs[KEY_REFRESH_SECONDS] ?: 30),
        alarmEnabled = prefs[KEY_ALARM_ENABLED] ?: true,
        alarmToneId = prefs[KEY_ALARM_TONE_ID] ?: BundledAlarmTone.OPENING_BELL.id,
        alarmCustomUri = prefs[KEY_ALARM_CUSTOM_URI],
        alarmCustomLabel = prefs[KEY_ALARM_CUSTOM_LABEL],
        bottomBarVisible = prefs[KEY_BOTTOM_BAR_VISIBLE] ?: true,
        bottomBarTabOrder = savedOrder,
        hiddenBottomBarTabs = prefs[KEY_BOTTOM_BAR_HIDDEN] ?: emptySet(),
      )
    }

  fun observeRemotePreferences(userId: String): Flow<AppPreferences?> = flow {
    val docPath = "users/$userId/preferences/app"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("preferences")
        .document("app")
        .snapshots()
        .map { snapshot ->
          if (!snapshot.exists()) null
          else {
            val doc =
              snapshot.toObject(
                UserPreferencesDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
              )
            doc?.let {
              AppPreferences(
                themeMode = ThemeMode.fromWire(it.themeMode),
                marketOpenMinutesEt = it.marketOpenMinutesEt.toInt().coerceIn(0, 1439),
                marketCloseMinutesEt = it.marketCloseMinutesEt.toInt().coerceIn(0, 1439),
                refreshInterval = RefreshInterval.fromSeconds(it.refreshIntervalSeconds.toInt()),
              )
            }
          }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val toLog = firestoreError ?: (error as? Exception)
          if (toLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(toLog, OperationType.GET, docPath)
          }
          throw error
        }
    )
  }

  suspend fun saveLocalPreferences(prefs: AppPreferences) {
    dataStore.edit { store ->
      store[KEY_THEME_MODE] = prefs.themeMode.wireValue
      store[KEY_MARKET_OPEN] = prefs.marketOpenMinutesEt
      store[KEY_MARKET_CLOSE] = prefs.marketCloseMinutesEt
      store[KEY_REFRESH_SECONDS] = prefs.refreshInterval.seconds
      store[KEY_ALARM_ENABLED] = prefs.alarmEnabled
      store[KEY_ALARM_TONE_ID] = prefs.alarmToneId
      if (prefs.alarmCustomUri != null) {
        store[KEY_ALARM_CUSTOM_URI] = prefs.alarmCustomUri
      } else {
        store.remove(KEY_ALARM_CUSTOM_URI)
      }
      if (prefs.alarmCustomLabel != null) {
        store[KEY_ALARM_CUSTOM_LABEL] = prefs.alarmCustomLabel
      } else {
        store.remove(KEY_ALARM_CUSTOM_LABEL)
      }
      store[KEY_BOTTOM_BAR_VISIBLE] = prefs.bottomBarVisible
      store[KEY_BOTTOM_BAR_ORDER] = prefs.bottomBarTabOrder.joinToString(",")
      store[KEY_BOTTOM_BAR_HIDDEN] = prefs.hiddenBottomBarTabs
    }
  }

  suspend fun savePreferences(prefs: AppPreferences): Result<Unit> = runCatching {
    saveLocalPreferences(prefs)
    val uid = auth.currentUser?.uid ?: return@runCatching
    val docRef =
      db.collection("users").document(uid).collection("preferences").document("app")
    try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef
          .update(
            mapOf(
              "themeMode" to prefs.themeMode.wireValue,
              "marketOpenMinutesEt" to prefs.marketOpenMinutesEt.toLong(),
              "marketCloseMinutesEt" to prefs.marketCloseMinutesEt.toLong(),
              "refreshIntervalSeconds" to prefs.refreshInterval.seconds.toLong(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .set(
            mapOf(
              "userId" to uid,
              "themeMode" to prefs.themeMode.wireValue,
              "marketOpenMinutesEt" to prefs.marketOpenMinutesEt.toLong(),
              "marketCloseMinutesEt" to prefs.marketCloseMinutesEt.toLong(),
              "refreshIntervalSeconds" to prefs.refreshInterval.seconds.toLong(),
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

  private companion object {
    val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    val KEY_MARKET_OPEN = intPreferencesKey("market_open_minutes_et")
    val KEY_MARKET_CLOSE = intPreferencesKey("market_close_minutes_et")
    val KEY_REFRESH_SECONDS = intPreferencesKey("refresh_interval_seconds")
    val KEY_ALARM_ENABLED = booleanPreferencesKey("alarm_audio_enabled")
    val KEY_ALARM_TONE_ID = stringPreferencesKey("alarm_tone_id")
    val KEY_ALARM_CUSTOM_URI = stringPreferencesKey("alarm_custom_uri")
    val KEY_ALARM_CUSTOM_LABEL = stringPreferencesKey("alarm_custom_label")
    val KEY_BOTTOM_BAR_VISIBLE = booleanPreferencesKey("bottom_bar_visible")
    val KEY_BOTTOM_BAR_ORDER = stringPreferencesKey("bottom_bar_tab_order")
    val KEY_BOTTOM_BAR_HIDDEN = stringSetPreferencesKey("bottom_bar_hidden_tabs")
  }
}
