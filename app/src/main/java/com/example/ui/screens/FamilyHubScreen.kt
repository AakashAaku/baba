package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.FamilyTimeZoneHelper
import com.example.core.LocalePreferenceStore
import com.example.core.VoiceRecorderHelper
import com.example.domain.model.IdentityMode
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.IdentityModeBadge
import com.example.ui.components.StatusBannerBar
import com.example.ui.components.SyncedMediaImage
import com.example.ui.components.VoiceNoteControlRow
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.ConnectionMintSoft
import com.example.ui.theme.TrustNavyDark
import com.example.ui.viewmodel.BabaWorkspaceData
import com.example.ui.viewmodel.BabaWorkspaceViewModel
import kotlinx.coroutines.launch
import java.util.TimeZone

@OptIn(ExperimentalLayoutApi::class)
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
    val vmLocale by viewModel.activeLocale.collectAsStateWithLifecycle()
    val mediaCache by viewModel.mediaCache.collectAsStateWithLifecycle()

    val playerHelper = remember(context) { VoiceRecorderHelper(context) }
    DisposableEffect(playerHelper) {
        onDispose { playerHelper.release() }
    }
    var playingId by remember { mutableStateOf<String?>(null) }
    var audioNote by remember { mutableStateOf<String?>(null) }

    // Voice recorder for bidirectional family communication
    var isRecording by remember { mutableStateOf(false) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var recordedPath by remember { mutableStateOf("") }
    var recordedDurationSec by remember { mutableStateOf(0L) }
    var voiceError by remember { mutableStateOf<String?>(null) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            playerHelper.startRecording()
                .onSuccess {
                    isRecording = true
                    voiceError = null
                }
                .onFailure { voiceError = it.localizedMessage }
        } else {
            voiceError = "Microphone permission is required to record a genuine voice message."
        }
    }

    // Distance Bridge real timezones
    val defaultHomeZone = remember { TimeZone.getDefault().id }
    var homeZoneId by remember { mutableStateOf(LocalePreferenceStore.getHomeTimezone(context, defaultHomeZone)) }
    var workZoneId by remember { mutableStateOf(LocalePreferenceStore.getWorkTimezone(context)) }
    var showZonePickerDialog by remember { mutableStateOf(false) }

    var quickNote by remember { mutableStateOf("") }
    var eventTitle by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf("") }
    var eventChildVisible by remember { mutableStateOf(true) }

    if (showZonePickerDialog) {
        AlertDialog(
            onDismissRequest = { showZonePickerDialog = false },
            title = { Text(stringResource(R.string.hub_distance_bridge_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.hub_home_time_label), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FamilyTimeZoneHelper.commonZones.forEach { z ->
                            FilterChip(
                                selected = homeZoneId == z.zoneId,
                                onClick = {
                                    homeZoneId = z.zoneId
                                    LocalePreferenceStore.saveHomeTimezone(context, z.zoneId)
                                },
                                label = { Text(z.label) }
                            )
                        }
                    }
                    Text(stringResource(R.string.hub_abroad_time_label), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FamilyTimeZoneHelper.commonZones.forEach { z ->
                            FilterChip(
                                selected = workZoneId == z.zoneId,
                                onClick = {
                                    workZoneId = z.zoneId
                                    LocalePreferenceStore.saveWorkTimezone(context, z.zoneId)
                                },
                                label = { Text(z.label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showZonePickerDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

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
                text = stringResource(R.string.hub_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (data.families.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.hub_switch_workspace), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.families.forEach { fam ->
                            FilterChip(
                                selected = fam.id == family.id,
                                onClick = { viewModel.switchActiveFamily(fam) },
                                label = { Text(fam.name) }
                            )
                        }
                    }
                }
            }

            // Distance Bridge card solving separated-family timezone & check-in gap
            Card(
                modifier = Modifier.fillMaxWidth().testTag("hub_distance_bridge_card"),
                colors = CardDefaults.cardColors(containerColor = ConnectionMintSoft.copy(alpha = 0.7f)),
                border = BorderStroke(1.dp, TrustNavyDark.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Public, contentDescription = "Time zones", tint = TrustNavyDark)
                            Text(
                                text = stringResource(R.string.hub_distance_bridge_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TrustNavyDark
                            )
                        }
                        TextButton(onClick = { showZonePickerDialog = true }) {
                            Text("Regions")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = stringResource(R.string.hub_home_time_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = FamilyTimeZoneHelper.zoneDisplayName(homeZoneId),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${FamilyTimeZoneHelper.formatLocalTime(homeZoneId, vmLocale)} • ${FamilyTimeZoneHelper.dayPartLabel(homeZoneId)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = stringResource(R.string.hub_abroad_time_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = FamilyTimeZoneHelper.zoneDisplayName(workZoneId),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${FamilyTimeZoneHelper.formatLocalTime(workZoneId, vmLocale)} • ${FamilyTimeZoneHelper.dayPartLabel(workZoneId)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.sendSafeCheckInPulse(
                                    family = family,
                                    member = currentMember,
                                    note = "${currentMember.displayName} sent a warm check-in across ${FamilyTimeZoneHelper.zoneDisplayName(homeZoneId)} <-> ${FamilyTimeZoneHelper.zoneDisplayName(workZoneId)}!"
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("hub_checkin_pulse_button")
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = "Safe check-in")
                            Spacer(Modifier.padding(horizontal = 3.dp))
                            Text(stringResource(R.string.hub_checkin_button), maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                if (isAdult) {
                                    viewModel.addCalendarEvent(
                                        family = family,
                                        title = "Family Video Call",
                                        description = "Home (${FamilyTimeZoneHelper.zoneDisplayName(homeZoneId)}) <-> Abroad (${FamilyTimeZoneHelper.zoneDisplayName(workZoneId)})",
                                        dateLabel = FamilyTimeZoneHelper.formatLocalTime(workZoneId, vmLocale),
                                        category = "call",
                                        childVisible = true
                                    )
                                } else {
                                    viewModel.requestLiveGuidance(
                                        family = family,
                                        topic = "Video Call Request",
                                        question = "${currentMember.displayName} would like a video call with Dad!"
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("hub_request_call_button")
                        ) {
                            Icon(Icons.Default.VideoCall, contentDescription = "Video call")
                            Spacer(Modifier.padding(horizontal = 3.dp))
                            Text(stringResource(R.string.hub_request_call_button), maxLines = 1)
                        }
                    }
                }
            }

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
                        Text(stringResource(R.string.hub_send_note_title), style = MaterialTheme.typography.titleMedium)
                    }
                    VoiceNoteControlRow(
                        isRecording = isRecording,
                        recordedDurationSec = recordedDurationSec,
                        hasRecording = recordedPath.isNotBlank(),
                        isPlaying = isPreviewPlaying,
                        onToggleRecord = {
                            if (isRecording) {
                                val res = playerHelper.stopRecording()
                                isRecording = false
                                if (res != null) {
                                    recordedPath = res.first
                                    recordedDurationSec = res.second
                                }
                            } else {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    playerHelper.startRecording()
                                        .onSuccess {
                                            isRecording = true
                                            voiceError = null
                                        }
                                        .onFailure { voiceError = it.localizedMessage }
                                } else {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        onTogglePlay = {
                            if (isPreviewPlaying) {
                                playerHelper.stopPlayback()
                                isPreviewPlaying = false
                            } else if (recordedPath.isNotBlank()) {
                                isPreviewPlaying = true
                                playerHelper.playFile(
                                    path = recordedPath,
                                    onComplete = { isPreviewPlaying = false },
                                    onError = { msg ->
                                        isPreviewPlaying = false
                                        voiceError = msg
                                    }
                                )
                            }
                        }
                    )
                    if (!voiceError.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = "Audio warning", tint = AlertCoral)
                            Text(voiceError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = AlertCoral)
                        }
                    }
                    OutlinedTextField(
                        value = quickNote,
                        onValueChange = { quickNote = it },
                        label = { Text("Write a genuine family message or voice caption...") },
                        modifier = Modifier.fillMaxWidth().testTag("hub_message_input")
                    )
                    Button(
                        onClick = {
                            viewModel.sendFamilyMessage(
                                family = family,
                                member = currentMember,
                                text = quickNote,
                                recipientScope = "family",
                                selectedRecipientUids = family.memberUids,
                                isVoiceNote = recordedPath.isNotBlank(),
                                audioDurationSec = recordedDurationSec,
                                localAudioPath = recordedPath
                            )
                            quickNote = ""
                            recordedPath = ""
                            recordedDurationSec = 0L
                        },
                        modifier = Modifier.fillMaxWidth().testTag("hub_send_message_button")
                    ) {
                        Text(stringResource(R.string.hub_send_note_button))
                    }
                }
            }

            // Shared Photo Memories Strip
            val photoMemories = data.memories.filter {
                it.reviewStatus == "approved" &&
                    it.mediaId.isNotBlank() &&
                    (isAdult || (it.sharingScope != "private" && it.memoryClass != "wife_father"))
            }
            if (photoMemories.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Shared family photos")
                    Text("Shared Family Photo Moments (${photoMemories.size})", style = MaterialTheme.typography.titleLarge)
                }
                photoMemories.take(4).forEach { mem ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(mem.title, style = MaterialTheme.typography.titleMedium)
                            SyncedMediaImage(
                                media = mediaCache[mem.mediaId],
                                onRequestLoad = { viewModel.ensureMediaLoaded(family.id, mem.mediaId) },
                                contentDescription = "Shared photo ${mem.title}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Text(mem.content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Text(
                "${stringResource(R.string.hub_recent_messages_title)} (${data.familyMessages.size})",
                style = MaterialTheme.typography.titleLarge
            )
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
                Text(
                    "${stringResource(R.string.hub_calendar_title)} (${data.familyEvents.size})",
                    style = MaterialTheme.typography.titleLarge
                )
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
                            label = { Text("New Event / Call Window / Milestone") },
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
                            Text(stringResource(R.string.action_save))
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
                Text(
                    "${stringResource(R.string.hub_members_title)} (${data.members.size})",
                    style = MaterialTheme.typography.titleLarge
                )
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
