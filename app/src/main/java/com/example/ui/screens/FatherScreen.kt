package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.VoiceRecorderHelper
import com.example.domain.model.AgeBand
import com.example.domain.model.AiIdentity
import com.example.domain.model.FamilyGuidance
import com.example.domain.model.FamilyRole
import com.example.domain.model.IdentityMode
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ModeDisclosureHeader
import com.example.ui.components.StatusBannerBar
import com.example.ui.components.SyncedMediaImage
import com.example.ui.components.VoiceNoteControlRow
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.WarmGoldDeep
import com.example.ui.theme.WarmGoldSoft
import com.example.ui.viewmodel.BabaWorkspaceData
import com.example.ui.viewmodel.BabaWorkspaceViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FatherScreen(
    data: BabaWorkspaceData,
    viewModel: BabaWorkspaceViewModel,
    aiBusy: Boolean,
    previewResponse: String?,
    statusBanner: String?
) {
    val family = data.activeFamily ?: return
    val member = data.currentMember ?: return
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Identity", "Memories", "Live Guidance", "Real Baba", "Family & Kids")

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title) },
                    modifier = Modifier.testTag("father_tab_$idx")
                )
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusBannerBar(message = statusBanner, onDismiss = viewModel::clearBanner)

            when (selectedTab) {
                0 -> FatherIdentityStudioTab(
                    data = data,
                    viewModel = viewModel,
                    aiBusy = aiBusy,
                    previewResponse = previewResponse
                )
                1 -> FatherMemoryLibraryTab(data = data, viewModel = viewModel)
                2 -> FatherLiveGuidanceTab(data = data, viewModel = viewModel)
                3 -> FatherRealBabaStudioTab(data = data, viewModel = viewModel, senderName = member.displayName)
                4 -> FatherFamilyMembersTab(data = data, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun FatherIdentityStudioTab(
    data: BabaWorkspaceData,
    viewModel: BabaWorkspaceViewModel,
    aiBusy: Boolean,
    previewResponse: String?
) {
    val family = data.activeFamily ?: return
    val latestIdentity = data.identities.firstOrNull()
    var style by remember(latestIdentity?.id, latestIdentity?.activeVersion) {
        mutableStateOf(latestIdentity?.communicationStyle ?: "Warm, steady, reflective, honest, and encouraging")
    }
    var values by remember(latestIdentity?.id, latestIdentity?.activeVersion) {
        mutableStateOf(latestIdentity?.values ?: "Integrity, kindness, curiosity, gratitude, and accountability")
    }
    var interests by remember(latestIdentity?.id, latestIdentity?.activeVersion) {
        mutableStateOf(latestIdentity?.interests ?: "Stargazing, reading history, woodworking, family meals")
    }
    var bio by remember(latestIdentity?.id, latestIdentity?.activeVersion) {
        mutableStateOf(latestIdentity?.bioSummary ?: "")
    }
    var sampleQuestion by remember { mutableStateOf("What value matters most when someone makes a mistake?") }

    ModeDisclosureHeader(
        mode = IdentityMode.DIGITAL_BABA,
        subtitle = "Active Status: ${latestIdentity?.status?.uppercase() ?: "NOT CREATED"} (v${latestIdentity?.activeVersion ?: 0}) • Text voice-cloning disabled in v1."
    )

    Surface(
        color = WarmGoldSoft,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.RecordVoiceOver, contentDescription = "Voice policy", tint = WarmGoldDeep)
            Text(
                text = "Voice Cloning Policy (v1): Synthetic voice replication is disabled. Family audio is limited to genuine human recordings.",
                style = MaterialTheme.typography.bodyMedium,
                color = WarmGoldDeep
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Psychology, contentDescription = "Digital Baba studio")
                Text("Digital Baba Identity Setup", style = MaterialTheme.typography.titleLarge)
            }

            OutlinedTextField(
                value = style,
                onValueChange = { style = it },
                label = { Text("Communication Style") },
                modifier = Modifier.fillMaxWidth().testTag("identity_style_input")
            )
            OutlinedTextField(
                value = values,
                onValueChange = { values = it },
                label = { Text("Core Values") },
                modifier = Modifier.fillMaxWidth().testTag("identity_values_input")
            )
            OutlinedTextField(
                value = interests,
                onValueChange = { interests = it },
                label = { Text("Interests & Storytelling Themes") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Approved Father Bio & Principles") },
                placeholder = { Text("Describe your real perspective, traditions, and how Digital Baba should frame your guidance...") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("identity_bio_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        viewModel.saveAndMaybeApproveIdentity(
                            family = family,
                            existing = latestIdentity,
                            communicationStyle = style,
                            values = values,
                            interests = interests,
                            bioSummary = bio,
                            approveNow = false
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("save_identity_draft_button")
                ) {
                    Text("Save Draft")
                }
                Button(
                    onClick = {
                        viewModel.saveAndMaybeApproveIdentity(
                            family = family,
                            existing = latestIdentity,
                            communicationStyle = style,
                            values = values,
                            interests = interests,
                            bioSummary = bio,
                            approveNow = true
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("approve_identity_button")
                ) {
                    Text("Approve & Publish")
                }
            }

            if (latestIdentity != null && latestIdentity.status == "approved") {
                OutlinedButton(
                    onClick = { viewModel.suspendIdentity(family, latestIdentity) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertCoral),
                    modifier = Modifier.fillMaxWidth().testTag("suspend_identity_button")
                ) {
                    Text("Suspend Digital Baba Immediately")
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
            Text("Live Safety & Grounding Preview", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = sampleQuestion,
                onValueChange = { sampleQuestion = it },
                label = { Text("Test Question") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val draft = AiIdentity(
                        id = latestIdentity?.id ?: "preview",
                        familyId = family.id,
                        communicationStyle = style,
                        values = values,
                        interests = interests,
                        bioSummary = bio
                    )
                    viewModel.previewDigitalBabaSample(data, sampleQuestion, draft)
                },
                enabled = !aiBusy,
                modifier = Modifier.fillMaxWidth().testTag("preview_identity_button")
            ) {
                Text(if (aiBusy) "Generating Preview..." else "Preview Digital Baba Response")
            }
            if (!previewResponse.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = previewResponse,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FatherMemoryLibraryTab(
    data: BabaWorkspaceData,
    viewModel: BabaWorkspaceViewModel
) {
    val family = data.activeFamily ?: return
    val context = LocalContext.current
    val mediaCache by viewModel.mediaCache.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var memoryClass by remember { mutableStateOf("shared_family") }
    var sharingScope by remember { mutableStateOf("family") }
    var provenance by remember { mutableStateOf("") }
    var ownershipConfirmed by remember { mutableStateOf(true) }
    val selectedMemberIds = remember { mutableStateListOf<String>() }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadPhotoAndCreateMemory(
                context = context,
                family = family,
                title = title,
                content = content,
                imageUri = uri,
                memoryClass = memoryClass,
                sharingScope = sharingScope,
                allowedMemberIds = selectedMemberIds.toList(),
                provenanceNote = provenance,
                approveImmediately = true
            )
            title = ""
            content = ""
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Provenance-Tagged Memory", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Memory Title") },
                modifier = Modifier.fillMaxWidth().testTag("memory_title_input")
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Memory Story or Value Lesson") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("memory_content_input")
            )
            OutlinedTextField(
                value = provenance,
                onValueChange = { provenance = it },
                label = { Text("Provenance Note (optional)") },
                placeholder = { Text("e.g., Written by Father, family archive 2024") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Memory Class", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("shared_family", "storyland", "wife_father", "private_vault").forEach { cls ->
                    FilterChip(
                        selected = memoryClass == cls,
                        onClick = { memoryClass = cls },
                        label = { Text(cls.replace('_', ' ')) }
                    )
                }
            }

            Text("Audience Scope", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("family", "selected_members", "private").forEach { scope ->
                    FilterChip(
                        selected = sharingScope == scope,
                        onClick = { sharingScope = scope },
                        label = { Text(scope.replace('_', ' ')) }
                    )
                }
            }

            if (sharingScope == "selected_members") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.members.forEach { m ->
                        val picked = m.uid in selectedMemberIds
                        FilterChip(
                            selected = picked,
                            onClick = {
                                if (picked) selectedMemberIds.remove(m.uid) else selectedMemberIds.add(m.uid)
                            },
                            label = { Text("${m.displayName} (${m.role})") }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ownershipConfirmed,
                    onCheckedChange = { ownershipConfirmed = it }
                )
                Text(
                    text = "I confirm family ownership/right to share this memory.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            OutlinedButton(
                enabled = ownershipConfirmed && title.isNotBlank() && content.isNotBlank(),
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth().testTag("attach_photo_memory_button")
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach photo")
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Attach & Approve Photo Memory")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    enabled = ownershipConfirmed,
                    onClick = {
                        viewModel.addMemory(
                            family = family,
                            title = title,
                            content = content,
                            memoryClass = memoryClass,
                            sharingScope = sharingScope,
                            allowedMemberIds = selectedMemberIds.toList(),
                            provenanceNote = provenance,
                            approveImmediately = false
                        )
                        title = ""
                        content = ""
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Draft")
                }
                Button(
                    enabled = ownershipConfirmed,
                    onClick = {
                        viewModel.addMemory(
                            family = family,
                            title = title,
                            content = content,
                            memoryClass = memoryClass,
                            sharingScope = sharingScope,
                            allowedMemberIds = selectedMemberIds.toList(),
                            provenanceNote = provenance,
                            approveImmediately = true
                        )
                        title = ""
                        content = ""
                    },
                    modifier = Modifier.weight(1f).testTag("approve_memory_button")
                ) {
                    Text("Approve Memory")
                }
            }
        }
    }

    Text("Memory Vault (${data.memories.size})", style = MaterialTheme.typography.titleLarge)
    if (data.memories.isEmpty()) {
        EmptyStateCard(
            title = "No memories added yet",
            description = "Add approved memories above so Digital Baba and Storyland can ground responses in real family stories."
        )
    } else {
        data.memories.forEach { mem ->
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
                        Text(mem.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${mem.reviewStatus.uppercase()} • ${mem.sharingScope}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (mem.mediaId.isNotBlank()) {
                        SyncedMediaImage(
                            media = mediaCache[mem.mediaId],
                            onRequestLoad = { viewModel.ensureMediaLoaded(family.id, mem.mediaId) },
                            contentDescription = "Photo memory ${mem.title}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Text(mem.content, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Provenance: ${mem.provenanceNote}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (mem.reviewStatus != "approved") {
                            TextButton(onClick = { viewModel.updateMemoryStatus(family, mem.id, "approved") }) {
                                Text("Approve")
                            }
                        }
                        if (mem.reviewStatus != "revoked") {
                            TextButton(onClick = { viewModel.updateMemoryStatus(family, mem.id, "revoked") }) {
                                Text("Revoke", color = AlertCoral)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FatherLiveGuidanceTab(
    data: BabaWorkspaceData,
    viewModel: BabaWorkspaceViewModel
) {
    val family = data.activeFamily ?: return
    var editingGuidance by remember { mutableStateOf<FamilyGuidance?>(null) }
    var topic by remember(editingGuidance?.id) { mutableStateOf(editingGuidance?.topic ?: "") }
    var question by remember(editingGuidance?.id) { mutableStateOf(editingGuidance?.question ?: "") }
    var answerText by remember(editingGuidance?.id) { mutableStateOf(editingGuidance?.answerText ?: "") }
    var audience by remember(editingGuidance?.id) { mutableStateOf(editingGuidance?.audience ?: "family") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (editingGuidance != null) "Respond to Escalated Question" else "Publish Approved Guidance",
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Topic") },
                modifier = Modifier.fillMaxWidth().testTag("guidance_topic_input")
            )
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Family Question") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = answerText,
                onValueChange = { answerText = it },
                label = { Text("Father's Verified Guidance") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("guidance_answer_input")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("family", "wife", "child").forEach { aud ->
                    FilterChip(
                        selected = audience == aud,
                        onClick = { audience = aud },
                        label = { Text(aud.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (editingGuidance != null) {
                    OutlinedButton(onClick = { editingGuidance = null }) {
                        Text("Clear Selection")
                    }
                }
                Button(
                    onClick = {
                        viewModel.approveLiveGuidance(
                            family = family,
                            guidanceId = editingGuidance?.id,
                            topic = topic,
                            question = question,
                            answerText = answerText,
                            audience = audience,
                            existingVersion = editingGuidance?.version ?: 0L
                        )
                        editingGuidance = null
                        topic = ""
                        question = ""
                        answerText = ""
                    },
                    modifier = Modifier.weight(1f).testTag("publish_guidance_button")
                ) {
                    Text("Approve & Publish Guidance")
                }
            }
        }
    }

    Text("Live Guidance Queue (${data.guidanceList.size})", style = MaterialTheme.typography.titleLarge)
    if (data.guidanceList.isEmpty()) {
        EmptyStateCard(
            title = "No guidance items yet",
            description = "When Digital Baba cannot verify your view on a topic, it queues questions here for your direct approval."
        )
    } else {
        data.guidanceList.forEach { g ->
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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(g.topic, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${g.status.uppercase()} • v${g.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (g.status == "requested") WarmGoldDeep else MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text("Q: ${g.question}", style = MaterialTheme.typography.bodyMedium)
                    if (g.answerText.isNotBlank()) {
                        Text("Approved: ${g.answerText}", style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { editingGuidance = g }) {
                        Text(if (g.status == "requested") "Answer & Approve" else "Update Guidance")
                    }
                }
            }
        }
    }
}

@Composable
private fun FatherRealBabaStudioTab(
    data: BabaWorkspaceData,
    viewModel: BabaWorkspaceViewModel,
    senderName: String
) {
    val family = data.activeFamily ?: return
    val member = data.currentMember ?: return
    val context = LocalContext.current
    val recorderHelper = remember(context) { VoiceRecorderHelper(context) }
    DisposableEffect(recorderHelper) {
        onDispose { recorderHelper.release() }
    }

    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var recordedPath by remember { mutableStateOf("") }
    var recordedDurationSec by remember { mutableStateOf(0L) }
    var messageText by remember { mutableStateOf("") }
    var recipientScope by remember { mutableStateOf("family") }
    var voiceError by remember { mutableStateOf<String?>(null) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            recorderHelper.startRecording()
                .onSuccess {
                    isRecording = true
                    voiceError = null
                }
                .onFailure { voiceError = it.localizedMessage ?: "Unable to start microphone recording." }
        } else {
            voiceError = "Microphone permission is required to record a genuine human voice message."
        }
    }

    ModeDisclosureHeader(
        mode = IdentityMode.REAL_BABA,
        subtitle = "Record your genuine voice or write a personal message from $senderName."
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Compose Genuine Human Message", style = MaterialTheme.typography.titleLarge)

            VoiceNoteControlRow(
                isRecording = isRecording,
                recordedDurationSec = recordedDurationSec,
                hasRecording = recordedPath.isNotBlank(),
                isPlaying = isPlaying,
                onToggleRecord = {
                    if (isRecording) {
                        val result = recorderHelper.stopRecording()
                        isRecording = false
                        if (result != null) {
                            recordedPath = result.first
                            recordedDurationSec = result.second
                        }
                    } else {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) {
                            recorderHelper.startRecording()
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
                    if (isPlaying) {
                        recorderHelper.stopPlayback()
                        isPlaying = false
                    } else if (recordedPath.isNotBlank()) {
                        isPlaying = true
                        recorderHelper.playFile(
                            path = recordedPath,
                            onComplete = { isPlaying = false },
                            onError = { msg ->
                                isPlaying = false
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
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("Personal Note or Voice Transcript") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("real_baba_message_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("family", "wife").forEach { scope ->
                    FilterChip(
                        selected = recipientScope == scope,
                        onClick = { recipientScope = scope },
                        label = { Text(if (scope == "family") "Whole Family" else "Wife / Adults Only") }
                    )
                }
            }

            Button(
                onClick = {
                    val targetUids = if (recipientScope == "wife") family.adultUids else family.memberUids
                    viewModel.sendFamilyMessage(
                        family = family,
                        member = member,
                        text = messageText,
                        recipientScope = recipientScope,
                        selectedRecipientUids = targetUids,
                        isVoiceNote = recordedPath.isNotBlank(),
                        audioDurationSec = recordedDurationSec,
                        localAudioPath = recordedPath
                    )
                    messageText = ""
                    recordedPath = ""
                    recordedDurationSec = 0L
                },
                modifier = Modifier.fillMaxWidth().testTag("send_real_baba_button")
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Send genuine message")
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Send Verified Real Baba Message")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FatherFamilyMembersTab(
    data: BabaWorkspaceData,
    viewModel: BabaWorkspaceViewModel
) {
    val family = data.activeFamily ?: return
    var inviteEmail by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf(FamilyRole.WIFE) }

    var childName by remember { mutableStateOf("") }
    var childAgeBand by remember { mutableStateOf(AgeBand.AGE_9_12) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Invite Family Adult", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = inviteEmail,
                onValueChange = { inviteEmail = it },
                label = { Text("Google Account Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("invite_email_input")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(FamilyRole.WIFE, FamilyRole.FATHER).forEach { r ->
                    FilterChip(
                        selected = inviteRole == r,
                        onClick = { inviteRole = r },
                        label = { Text(r.label) }
                    )
                }
            }
            Button(
                onClick = {
                    viewModel.inviteMember(family, inviteEmail, inviteRole, AgeBand.ADULT)
                    inviteEmail = ""
                },
                modifier = Modifier.fillMaxWidth().testTag("send_invite_button")
            ) {
                Text("Create Expiring Single-Use Invite")
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
                Icon(Icons.Default.AddCircleOutline, contentDescription = "Add child profile")
                Text("Adult-Authorized Child Onboarding", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = childName,
                onValueChange = { childName = it },
                label = { Text("Child First Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("child_name_input")
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AgeBand.AGE_6_8, AgeBand.AGE_9_12, AgeBand.AGE_13_15, AgeBand.AGE_16_17).forEach { band ->
                    FilterChip(
                        selected = childAgeBand == band,
                        onClick = { childAgeBand = band },
                        label = { Text(band.label) }
                    )
                }
            }
            Button(
                onClick = {
                    viewModel.addChildProfile(family, childName, childAgeBand)
                    childName = ""
                },
                modifier = Modifier.fillMaxWidth().testTag("add_child_profile_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Confirm guardian consent")
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Add Child Profile with Guardian Consent")
            }
        }
    }
}
