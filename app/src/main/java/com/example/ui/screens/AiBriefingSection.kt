package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.MarketAiBriefing
import com.example.ui.theme.LocalMarketColors
import com.example.ui.viewmodel.AiAssistantUiState
import com.example.ui.viewmodel.UiState

@Composable
fun AiAssistantView(
  briefingState: UiState<MarketAiBriefing>?,
  onGenerateBriefing: () -> Unit,
  aiState: AiAssistantUiState,
  onAskAi: (String) -> Unit,
) {
  var promptText by remember { mutableStateOf("") }
  val suggestedQuestions =
    listOf(
      "Why is the market moving?",
      "Why is VIX up or down today?",
      "What economic events matter today?",
      "What earnings are impacting the market?",
    )

  Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).testTag("ai_assistant_view"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    LazyColumn(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(bottom = 8.dp),
    ) {
      item {
        AiDailyBriefingCard(
          briefingState = briefingState,
          onGenerateBriefing = onGenerateBriefing,
        )
      }

      item {
        Text(
          text = "Ask Follow-Up Question",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(suggestedQuestions) { q ->
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              modifier =
                Modifier.clip(RoundedCornerShape(999.dp))
                  .clickable {
                    promptText = q
                    onAskAi(q)
                  },
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.AutoAwesome,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(14.dp),
                )
                Text(text = q, style = MaterialTheme.typography.labelLarge)
              }
            }
          }
        }
      }

      item {
        when (aiState) {
          is AiAssistantUiState.Idle -> {
            Card(
              shape = RoundedCornerShape(18.dp),
              colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = "Ask any follow-up about today's tape",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "Grounded in live indices, sector breadth, breaking news, and macro calendar data.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          is AiAssistantUiState.Loading -> {
            Row(
              modifier = Modifier.fillMaxWidth().padding(20.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(10.dp))
              Text("Analyzing live market tape...", style = MaterialTheme.typography.bodyMedium)
            }
          }
          is AiAssistantUiState.Streaming -> {
            AiResponseCard(question = aiState.question, answer = aiState.answer, isStreaming = true)
          }
          is AiAssistantUiState.Success -> {
            AiResponseCard(question = aiState.question, answer = aiState.answer, isStreaming = false)
          }
          is AiAssistantUiState.Error -> {
            Card(
              shape = RoundedCornerShape(18.dp),
              colors =
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                ),
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = aiState.question,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = aiState.message,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = promptText,
        onValueChange = { promptText = it },
        placeholder = { Text("Ask about indices, VIX, Fed, or earnings...") },
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("ai_question_input"),
        shape = RoundedCornerShape(16.dp),
      )
      IconButton(
        onClick = {
          if (promptText.isNotBlank()) {
            val q = promptText
            promptText = ""
            onAskAi(q)
          }
        },
        modifier =
          Modifier.size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .testTag("ai_send_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.Send,
          contentDescription = stringResource(R.string.cd_ai_send),
          tint = MaterialTheme.colorScheme.onPrimary,
        )
      }
    }
  }
}

@Composable
fun AiDailyBriefingCard(
  briefingState: UiState<MarketAiBriefing>?,
  onGenerateBriefing: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val marketColors = LocalMarketColors.current
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = modifier.fillMaxWidth().testTag("ai_briefing_card"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "AI Market Briefing",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
        IconButton(
          onClick = onGenerateBriefing,
          modifier = Modifier.testTag("refresh_ai_briefing_button"),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = "Refresh AI Briefing")
        }
      }

      when (briefingState) {
        null -> {
          Text(
            text =
              "Generate a structured plain-English summary analyzing today's index moves, sector breadth, VIX, and macro catalysts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(
            onClick = onGenerateBriefing,
            modifier = Modifier.testTag("generate_ai_briefing_button"),
          ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Generate AI Briefing")
          }
        }
        is UiState.Loading -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 8.dp),
          ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
              text = "Synthesizing live indices, breadth & news...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        is UiState.Error -> {
          Text(
            text = briefingState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
          Button(onClick = onGenerateBriefing) {
            Text("Retry AI Briefing")
          }
        }
        is UiState.Success -> {
          val briefing = briefingState.data
          val tagColor =
            when {
              briefing.sentimentTag.contains("On", ignoreCase = true) -> marketColors.gain
              briefing.sentimentTag.contains("Off", ignoreCase = true) ||
                briefing.sentimentTag.contains("Def", ignoreCase = true) -> marketColors.loss
              else -> marketColors.amber
            }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = tagColor.copy(alpha = 0.16f),
            ) {
              Text(
                text = briefing.sentimentTag,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = tagColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
            Text(
              text = "Updated ${briefing.generatedAtLabel}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Text(
            text = briefing.headline,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = briefing.executiveSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )

          if (briefing.keyDrivers.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
              text = "Key Market Drivers",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
            briefing.keyDrivers.forEach { bullet ->
              Text(
                text = "• $bullet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }

          if (briefing.watchNext.isNotEmpty()) {
            Text(
              text = "What to Watch Next",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = marketColors.amber,
            )
            briefing.watchNext.forEach { item ->
              Text(
                text = "• $item",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AiResponseCard(
  question: String,
  answer: String,
  isStreaming: Boolean,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = question,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        if (isStreaming) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Text(
        text = answer,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
