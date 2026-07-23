package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standard Material 3 Expressive Dialog component for SecondBrain.
 * Provides 28.dp rounded corners, surfaceContainerHigh background,
 * expressive icon container, and styled action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: @Composable (() -> Unit)? = null,
    icon: Painter? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    dismissButtonText: String? = "Cancel",
    onDismiss: (() -> Unit)? = onDismissRequest,
    isDestructive: Boolean = false,
    confirmButtonEnabled: Boolean = true,
    confirmTestTag: String? = null,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = icon?.let {
            {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isDestructive) MaterialTheme.colorScheme.errorContainer else iconContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = it,
                        contentDescription = null,
                        tint = if (isDestructive) MaterialTheme.colorScheme.onErrorContainer else iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = text,
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmButtonEnabled,
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = confirmButtonText,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = dismissButtonText?.let {
            {
                TextButton(
                    onClick = { onDismiss?.invoke() ?: onDismissRequest() },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        modifier = modifier
    )
}

/**
 * Convenience Expressive Confirmation Dialog for destructive or key actions (Delete, Sign Out, etc).
 */
@Composable
fun ExpressiveConfirmationDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmButtonText: String = "Delete",
    onConfirm: () -> Unit,
    dismissButtonText: String = "Cancel",
    isDestructive: Boolean = true,
    icon: Painter? = null
) {
    ExpressiveAlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        icon = icon,
        confirmButtonText = confirmButtonText,
        onConfirm = onConfirm,
        dismissButtonText = dismissButtonText,
        isDestructive = isDestructive
    )
}
