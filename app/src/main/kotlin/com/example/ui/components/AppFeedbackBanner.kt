package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.FeedbackSeverity
import com.example.util.HapticManager
import com.example.util.UserFeedbackMessage
import kotlinx.coroutines.delay

@Composable
fun AppFeedbackBanner(
    feedbackMessage: UserFeedbackMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            when (feedbackMessage.severity) {
                FeedbackSeverity.SUCCESS -> HapticManager.performSuccess(context)
                FeedbackSeverity.ERROR -> HapticManager.performError(context)
                FeedbackSeverity.WARNING -> HapticManager.performSwipeTick(context)
                FeedbackSeverity.INFO -> HapticManager.performClick(context)
            }
            delay(feedbackMessage.durationMs)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = feedbackMessage != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        if (feedbackMessage != null) {
            val containerColor = when (feedbackMessage.severity) {
                FeedbackSeverity.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                FeedbackSeverity.INFO -> MaterialTheme.colorScheme.secondaryContainer
                FeedbackSeverity.WARNING -> Color(0xFFFEF3C7) // Amber light tint
                FeedbackSeverity.ERROR -> MaterialTheme.colorScheme.errorContainer
            }

            val contentColor = when (feedbackMessage.severity) {
                FeedbackSeverity.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
                FeedbackSeverity.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
                FeedbackSeverity.WARNING -> Color(0xFF92400E) // Amber dark text
                FeedbackSeverity.ERROR -> MaterialTheme.colorScheme.onErrorContainer
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = containerColor,
                contentColor = contentColor,
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .bounceClick()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = feedbackMessage.icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = feedbackMessage.message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (feedbackMessage.actionLabel != null && feedbackMessage.onAction != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                feedbackMessage.onAction.invoke()
                                onDismiss()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = feedbackMessage.actionLabel,
                                color = contentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
