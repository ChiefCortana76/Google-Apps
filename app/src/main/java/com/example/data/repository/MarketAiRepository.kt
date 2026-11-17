package com.example.data.repository

import android.util.Log
import com.example.data.model.MarketAiBriefing
import com.example.data.model.MarketCommandSnapshot
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class MarketAiRepository {
  private val model by lazy {
    Firebase.ai.generativeModel("gemini-3.8-flash")
  }

  private val briefingSchema by lazy {
    Schema.obj(
      mapOf(
        "headline" to Schema.string("Short crisp market briefing headline (max 12 words)"),
        "sentimentTag" to Schema.string("One of: Risk-On, Mixed Rotation, Risk-Off, Defensive"),
        "executiveSummary" to
          Schema.string("2-3 plain-English sentences explaining what is moving markets right now."),
        "keyDrivers" to
          Schema.array(
            Schema.string("Concise single-sentence market-moving driver with numbers or sectors"),
          ),
        "watchNext" to
          Schema.array(
            Schema.string("Upcoming macro catalyst, earnings report, or key technical level to monitor"),
          ),
      )
    )
  }

  private val structuredBriefingModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = briefingSchema
        },
    )
  }

  suspend fun generateMarketBriefing(snapshot: MarketCommandSnapshot?): MarketAiBriefing {
    val contextSummary = buildContextPrompt(snapshot)
    val prompt =
      """
      You are an institutional market desk analyst preparing a concise plain-English "AI Briefing" for an equity and macro investor.
      Synthesize the live market snapshot below into:
      1) A punchy headline.
      2) Overall regime sentimentTag ("Risk-On", "Mixed Rotation", "Risk-Off", or "Defensive").
      3) A 2-3 sentence executiveSummary explaining why indices, breadth, and sectors are moving.
      4) 3–4 bullet strings in keyDrivers citing concrete index %, breadth/VIX, sector leaders/laggards, and breaking news.
      5) 2–3 bullet strings in watchNext citing upcoming economic events or earnings.
      Strictly remain informational (no financial or trading advice).

      LIVE MARKET SNAPSHOT:
      $contextSummary
      """.trimIndent()

    return try {
      val responseText =
        withTimeout(60_000L) {
          structuredBriefingModel.generateContent(prompt).text.orEmpty()
        }
      if (responseText.isBlank()) {
        throw IllegalStateException("Gemini returned an empty market briefing.")
      }
      val json = JSONObject(responseText)
      val driversJson = json.optJSONArray("keyDrivers")
      val watchJson = json.optJSONArray("watchNext")
      val keyDrivers =
        buildList {
          if (driversJson != null) {
            for (i in 0 until driversJson.length()) {
              driversJson.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
            }
          }
        }
      val watchNext =
        buildList {
          if (watchJson != null) {
            for (i in 0 until watchJson.length()) {
              watchJson.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
            }
          }
        }
      MarketAiBriefing(
        headline = json.optString("headline").ifBlank { "Market Briefing" },
        sentimentTag = json.optString("sentimentTag").ifBlank { "Mixed Rotation" },
        executiveSummary = json.optString("executiveSummary").ifBlank { responseText },
        keyDrivers = keyDrivers,
        watchNext = watchNext,
        generatedAtLabel = snapshot?.lastUpdatedLabel ?: "Live",
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Structured AI briefing timed out", e)
      throw IllegalStateException("AI Briefing timed out after 60 seconds. Please try again.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Structured AI briefing error", e)
      throw e
    }
  }

  fun streamMarketAnswer(
    question: String,
    snapshot: MarketCommandSnapshot?,
  ): Flow<String> = flow {
    val contextSummary = buildContextPrompt(snapshot)
    val prompt =
      """
      You are an institutional-grade market intelligence analyst inside a personal market command center.
      Answer the user's question clearly in plain English with concise bullet points.
      Use the live market context below when relevant, and focus strictly on market drivers, macro events, breadth, and earnings (never give personalized investment or trading advice).

      LIVE MARKET CONTEXT:
      $contextSummary

      USER QUESTION:
      $question
      """.trimIndent()

    val accumulated = StringBuilder()
    try {
      withTimeout(60_000L) {
        model.generateContentStream(prompt).collect { chunk ->
          val text = chunk.text.orEmpty()
          if (text.isNotEmpty()) {
            accumulated.append(text)
            emit(accumulated.toString())
          }
        }
      }
      if (accumulated.isBlank()) {
        throw IllegalStateException("Gemini returned an empty response.")
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "AI market brief timed out", e)
      throw IllegalStateException("Analysis timed out after 60 seconds. Please try again.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Firebase AI Logic error", e)
      throw e
    }
  }

  private fun buildContextPrompt(snapshot: MarketCommandSnapshot?): String {
    if (snapshot == null) return "Market snapshot is currently loading."
    val indicesLine =
      snapshot.indices.joinToString("; ") {
        String.format(
          Locale.US,
          "%s: %.2f (%+.2f%%)",
          it.displaySymbol,
          it.price,
          it.changePercent,
        )
      }
    val futuresLine =
      snapshot.futures.joinToString("; ") {
        String.format(
          Locale.US,
          "%s: %.2f (%+.2f%%)",
          it.displaySymbol,
          it.price,
          it.changePercent,
        )
      }
    val breadth = snapshot.breadth
    val breadthLine =
      String.format(
        Locale.US,
        "A/D Ratio %.2f (%d adv / %d dec), 52W Highs %d vs Lows %d, Equity Put/Call %.2f, Fear & Greed %d (%s), McClellan %+.1f",
        breadth.advanceDeclineRatio,
        breadth.advancingCount,
        breadth.decliningCount,
        breadth.newHighs52W,
        breadth.newLows52W,
        breadth.putCallEquityRatio,
        breadth.fearGreedScore,
        breadth.fearGreedLabel,
        breadth.mcclellanOscillator,
      )
    val sectorsLine =
      breadth.sectors.sortedByDescending { it.changePercent }.let { sorted ->
        val leaders = sorted.take(3).joinToString(", ") {
          String.format(Locale.US, "%s (%+.2f%%)", it.name, it.changePercent)
        }
        val laggards = sorted.takeLast(3).joinToString(", ") {
          String.format(Locale.US, "%s (%+.2f%%)", it.name, it.changePercent)
        }
        "Leaders: $leaders | Laggards: $laggards"
      }
    val headlinesLine =
      snapshot.news.take(5).joinToString(" | ") { "[${it.category.label}] ${it.title}" }
    val eventsLine =
      snapshot.economicEvents.take(5).joinToString("; ") {
        "${it.timeEt} ${it.title} (Fcst ${it.forecast}, Prev ${it.previous}${it.actual?.let { a -> ", Act $a" } ?: ""})"
      }
    val earningsLine =
      snapshot.earnings.take(5).joinToString("; ") {
        "${it.ticker} (${it.session.shortCode}) Exp EPS ${it.expectedEps}, Rev ${it.expectedRevenue}"
      }
    return """
      Indices: $indicesLine
      Futures: $futuresLine
      Breadth: $breadthLine
      Sectors: $sectorsLine
      Top News: $headlinesLine
      Key Economic Events: $eventsLine
      Key Earnings: $earningsLine
      """.trimIndent()
  }

  private companion object {
    const val TAG = "MarketAiRepo"
  }
}
