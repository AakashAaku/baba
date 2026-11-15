package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.VoiceRecorderHelper
import com.example.domain.model.IdentityMode
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.IdentityModeBadge
import com.example.ui.components.StatusBannerBar
import com.example.ui.theme.AlertCoral
import com.example.ui.viewmodel.BabaWorkspaceData
import com.example.ui.viewmodel.BabaWorkspaceViewModel
import kotlinx.coroutines.launch

@Composable
fun FamilyHubScreen(
    data: BabaWorkspaceData,
    viewModel: BabaWorkspaceViewModel,
    statusBanner: String?
) {
    val family = data.activeFamily ?: return
    val currentMember = data.currentMember ?: return
    val isAdult = currentMember.role != "child"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerHelper = remember(context) { VoiceRecorderHelper(context) }
    DisposableEffect(playerHelper) {
        onDispose { playerHelper.release() }
    }
    var playingId by remember { mutableStateOf<String?>(null) }
    var audioNote by remember { mutableStateOf<String?>(null) }

    var quickNote by remember { mutableStateOf("") }
    var eventTitle by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf("") }
    var eventChildVisible by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusBannerBar(message = statusBanner, onDismiss = viewModel::clearBanner)

            Text(
                text = family.name,
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "Shared Family Timeline, Events, and Genuine Human Notes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Forum, contentDescription = "Send family note")
                        Text("Send Genuine Family Note", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedTextField(
                        value = quickNote,
                        onValueChange = { quickNote = it },
                        label = { Text("Write a genuine family message...") },
                        modifier = Modifier.fillMaxWidth().testTag("hub_message_input")
                    )
                    Button(
                        onClick = {
                            viewModel.sendFamilyMessage(
                                family = family,
                                member = currentMember,
                                text = quickNote,
                                recipientScope = "family",
                                selectedRecipientUids = family.memberUids
                            )
                            quickNote = ""
                        },
                        modifier = Modifier.fillMaxWidth().testTag("hub_send_message_button")
                    ) {
                        Text("Post Verified Human Message")
                    }
                }
            }

            Text("Recent Human Family Messages (${data.familyMessages.size})", style = MaterialTheme.typography.titleLarge)
            if (!audioNote.isNullOrBlank()) {
                Text(audioNote.orEmpty(), style = MaterialTheme.typography.bodySmall, color = AlertCoral)
            }
            if (data.familyMessages.isEmpty()) {
                EmptyStateCard(
                    title = "No messages in Family Hub yet",
                    description = "Send a genuine family message above to start your shared timeline."
                )
            } else {
                data.familyMessages.forEach { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IdentityModeBadge(mode = IdentityMode.REAL_BABA)
                                Text(
                                    text = "${msg.senderName} (${msg.senderRole})",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Text(msg.textContent, style = MaterialTheme.typography.bodyLarge)
                            if (msg.messageKind == "voice_note") {
                                val isPlaying = playingId == msg.id
                                OutlinedButton(
                                    onClick = {
                                        if (isPlaying) {
                                            playerHelper.stopPlayback()
                                            playingId = null
                                        } else {
                                            playingId = msg.id
                                            audioNote = null
                                            scope.launch {
                                                viewModel.resolveAudioPlaybackPath(
                                                    context = context,
                                                    familyId = family.id,
                                                    localAudioPath = msg.localAudioPath,
                                                    mediaId = msg.mediaId
                                                ).onSuccess { path ->
                                                    playerHelper.playFile(
                                                        path = path,
                                                        onComplete = { playingId = null },
                                                        onError = { err ->
                                                            playingId = null
                                                            audioNote = err
                                                        }
                                                    )
                                                }.onFailure { err ->
                                                    playingId = null
                                                    audioNote = err.localizedMessage ?: "Audio unavailable."
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = "Play voice message"
                                    )
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Text(if (isPlaying) "Stop Audio" else "Play Voice Note (${msg.audioDurationSec}s)")
                                }
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Shared calendar")
                Text("Shared Family Calendar (${data.familyEvents.size})", style = MaterialTheme.typography.titleLarge)
            }

            if (isAdult) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = eventTitle,
                            onValueChange = { eventTitle = it },
                            label = { Text("New Event / Milestone") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = eventDate,
                            onValueChange = { eventDate = it },
                            label = { Text("Date & Time") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = eventChildVisible,
                                onCheckedChange = { eventChildVisible = it }
                            )
                            Text("Share with children")
                        }
                        Button(
                            onClick = {
                                viewModel.addCalendarEvent(
                                    family = family,
                                    title = eventTitle,
                                    description = "",
                                    dateLabel = eventDate,
                                    category = "milestone",
                                    childVisible = eventChildVisible
                                )
                                eventTitle = ""
                                eventDate = ""
                            },
                            modifier = Modifier.fillMaxWidth().testTag("hub_add_event_button")
                        ) {
                            Text("Save Event")
                        }
                    }
                }
            }

            if (data.familyEvents.isEmpty()) {
                EmptyStateCard(
                    title = "No upcoming family events",
                    description = "Milestones, check-in schedules, and birthdays added by family adults appear here."
                )
            } else {
                data.familyEvents.forEach { ev ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(ev.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${ev.eventDateLabel} • ${if (ev.childVisible) "Family visible" else "Adults only"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (ev.description.isNotBlank()) {
                                Text(ev.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Group, contentDescription = "Family members")
                Text("Family Members (${data.members.size})", style = MaterialTheme.typography.titleLarge)
            }
            data.members.forEach { m ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(m.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Role: ${m.role.uppercase()} • Age Band: ${m.ageBand}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Consent: ${m.consentState}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
