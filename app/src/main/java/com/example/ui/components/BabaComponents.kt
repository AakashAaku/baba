package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.MediaValidator
import com.example.domain.model.AiTurn
import com.example.domain.model.FamilyMedia
import com.example.domain.model.IdentityMode
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.AlertCoralSoft
import com.example.ui.theme.ConnectionMint
import com.example.ui.theme.ConnectionMintDeep
import com.example.ui.theme.ConnectionMintSoft
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TrustNavy
import com.example.ui.theme.TrustNavyDark
import com.example.ui.theme.WarmGold
import com.example.ui.theme.WarmGoldDeep
import com.example.ui.theme.WarmGoldSoft

@Composable
fun IdentityModeBadge(
    mode: IdentityMode,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor, icon) = when (mode) {
        IdentityMode.DIGITAL_BABA -> Triple(
            TrustNavy.copy(alpha = 0.12f),
            TrustNavy,
            Icons.Default.Psychology
        )
        IdentityMode.BABA_COMPANION -> Triple(
            ConnectionMintSoft,
            ConnectionMintDeep,
            Icons.Default.AutoAwesome
        )
        IdentityMode.REAL_BABA -> Triple(
            WarmGoldSoft,
            WarmGoldDeep,
            Icons.Default.VerifiedUser
        )
    }
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = mode.label,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = mode.label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ModeDisclosureHeader(
    mode: IdentityMode,
    subtitle: String? = null,
    isPrivate: Boolean = false
) {
    val gradient = when (mode) {
        IdentityMode.DIGITAL_BABA -> listOf(TrustNavy, TrustNavyDark)
        IdentityMode.BABA_COMPANION -> listOf(ConnectionMintDeep, TrustNavy)
        IdentityMode.REAL_BABA -> listOf(WarmGoldDeep, TrustNavy)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(gradient))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = PureWhite.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Shield,
                            contentDescription = "Disclosure badge",
                            tint = ConnectionMint,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = mode.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (isPrivate) {
                    Surface(
                        color = WarmGold.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "PRIVATE TO YOU",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = WarmGoldSoft
                        )
                    }
                }
            }
            Text(
                text = mode.disclosureText,
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiTurnBubble(
    turn: AiTurn,
    onRequestLiveGuidance: ((String) -> Unit)? = null
) {
    val isUser = turn.senderType == "user"
    val mode = IdentityMode.fromApi(turn.identityMode)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            tonalElevation = if (isUser) 0.dp else 2.dp,
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IdentityModeBadge(mode = mode)
                        if (turn.safetyStatus == "blocked") {
                            Surface(color = AlertCoralSoft, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "Policy Guard",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AlertCoral
                                )
                            }
                        }
                    }
                    Text(
                        text = turn.disclosure,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = turn.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )

                if (!isUser && turn.sourceReferences.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        turn.sourceReferences.forEach { refId ->
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = "Source: $refId",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Verified source",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                if (!isUser && turn.requiresHumanInput && onRequestLiveGuidance != null) {
                    Surface(
                        color = WarmGoldSoft,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = "Live Guidance",
                                    tint = WarmGoldDeep
                                )
                                Text(
                                    text = "No approved guidance found. Escalated to Live Guidance.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = WarmGoldDeep
                                )
                            }
                            TextButton(onClick = { onRequestLiveGuidance(turn.text) }) {
                                Text("Log Ask")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isBusy: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onSend: () -> Unit,
    inputTestTag: String,
    sendTestTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
            Surface(
                color = AlertCoralSoft,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = AlertCoral)
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlertCoral
                        )
                    }
                    TextButton(onClick = onDismissError) {
                        Text("Dismiss")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(inputTestTag),
                shape = RoundedCornerShape(16.dp),
                maxLines = 4
            )
            Button(
                onClick = onSend,
                enabled = !isBusy && value.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier
                    .size(52.dp)
                    .testTag(sendTestTag),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun VoiceNoteControlRow(
    isRecording: Boolean,
    recordedDurationSec: Long,
    hasRecording: Boolean,
    isPlaying: Boolean,
    onToggleRecord: () -> Unit,
    onTogglePlay: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleRecord,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isRecording) AlertCoral else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("voice_record_button")
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop recording" else "Record genuine voice"
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isRecording) "Stop" else "Record Voice")
                }
                if (hasRecording) {
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Play recorded voice message"
                        )
                    }
                    Text(
                        text = "${recordedDurationSec}s genuine audio",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Text(
                text = "Human voice only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.AutoAwesome
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatusBannerBar(
    message: String?,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(visible = !message.isNullOrBlank()) {
        Surface(
            color = ConnectionMintSoft,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ConnectionMintDeep.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TrustNavyDark,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text("OK", color = ConnectionMintDeep)
                }
            }
        }
    }
}

@Composable
fun AsyncDrawableImage(
    @androidx.annotation.DrawableRes resId: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmapState = androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = resId
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            android.graphics.BitmapFactory.decodeResource(context.resources, resId)?.asImageBitmap()
        }
    }
    val bmp = bitmapState.value
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(listOf(TrustNavy, TrustNavyDark))
            )
        )
    }
}

@Composable
fun SyncedMediaImage(
    media: FamilyMedia?,
    onRequestLoad: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    androidx.compose.runtime.LaunchedEffect(media == null) {
        if (media == null) onRequestLoad()
    }
    val bitmapState = androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = media?.id,
        key2 = media?.payloadBase64
    ) {
        val base64 = media?.payloadBase64.orEmpty()
        value = if (base64.isNotBlank()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                MediaValidator.decodeBase64Bitmap(base64)?.asImageBitmap()
            }
        } else null
    }
    val bmp = bitmapState.value
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(12.dp),
            modifier = modifier
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Loading synced photo...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
