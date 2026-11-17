package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.OrchestratorTaskType
import com.example.core.VoiceRecorderHelper
import com.example.domain.model.AgeBand
import com.example.domain.model.FamilyMember
import com.example.domain.model.FamilyRole
import com.example.domain.model.IdentityMode
import com.example.ui.components.AiTurnBubble
import com.example.ui.components.ChatComposerBar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.IdentityModeBadge
import com.example.ui.components.ModeDisclosureHeader
import com.example.ui.components.StatusBannerBar
import com.example.ui.components.SyncedMediaImage
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.AlertCoralSoft
import com.example.ui.viewmodel.BabaWorkspaceData
import com.example.ui.viewmodel.BabaWorkspaceViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun KidsScreen(
    data: BabaWorkspaceData,
    viewModel: BabaWorkspaceViewModel,
    supervisedChildId: String?,
    aiBusy: Boolean,
    aiError: String?,
    statusBanner: String?
) {
    val family = data.activeFamily ?: return
    val currentMember = data.currentMember ?: return
    val childMembers = data.members.filter { it.role == FamilyRole.CHILD.apiValue }

    val effectiveChild: FamilyMember = if (currentMember.role == FamilyRole.CHILD.apiValue) {
        currentMember
    } else {
        childMembers.firstOrNull { it.id == supervisedChildId || it.uid == supervisedChildId }
            ?: childMembers.firstOrNull()
            ?: currentMember.copy(
                displayName = "Child Preview",
                role = FamilyRole.CHILD.apiValue,
                ageBand = AgeBand.AGE_9_12.apiValue,
                consentState = "granted"
            )
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.kids_tab_digital_baba),
        stringResource(R.string.kids_tab_storyland),
        stringResource(R.string.kids_tab_learning),
        stringResource(R.string.kids_tab_real_baba),
        stringResource(R.string.kids_tab_memories)
    )

    var showReportDialog by remember { mutableStateOf(false) }
    var reportNote by remember { mutableStateOf("") }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text(stringResource(R.string.kids_help_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.kids_help_dialog_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = reportNote,
                        onValueChange = { reportNote = it },
                        label = { Text("Optional short note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reportChildConcern(family, reportNote.ifBlank { "Requested adult help from Kids space" })
                        reportNote = ""
                        showReportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertCoral)
                ) {
                    Text(stringResource(R.string.kids_help_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${effectiveChild.displayName} • Age Band ${effectiveChild.ageBand}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (currentMember.role == FamilyRole.CHILD.apiValue) "Child-Protected Mode" else "Guardian-Supervised Kids View",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { showReportDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = AlertCoralSoft,
                        contentColor = AlertCoral
                    ),
                    modifier = Modifier.testTag("kids_help_button")
                ) {
                    Icon(Icons.Default.ReportProblem, contentDescription = stringResource(R.string.kids_help_button))
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text(stringResource(R.string.kids_help_button))
                }
            }
        }

        if (currentMember.role != FamilyRole.CHILD.apiValue && childMembers.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                childMembers.forEach { child ->
                    FilterChip(
                        selected = effectiveChild.id == child.id,
                        onClick = { viewModel.selectSupervisedChild(child.id) },
                        label = { Text("${child.displayName} (${child.ageBand})") }
                    )
                }
            }
        }

        PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title) },
                    modifier = Modifier.testTag("kids_tab_$idx")
                )
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            StatusBannerBar(message = statusBanner, onDismiss = viewModel::clearBanner)

            when (selectedTab) {
                0 -> KidsAiChatTab(
                    mode = IdentityMode.DIGITAL_BABA,
                    subtitle = "Grounded strictly in Father's approved guidance & shared family memories.",
                    turns = data.digitalBabaTurns,
                    suggestions = listOf(
                        "What did Father say about honesty?",
                        "Tell me a favorite family memory",
                        "Are you really my dad?"
                    ),
                    placeholder = "Ask Digital Baba safely...",
                    aiBusy = aiBusy,
                    aiError = aiError,
                    onDismissError = viewModel::clearAiError,
                    onSend = { prompt ->
                        viewModel.sendFamilyAiChat(data, OrchestratorTaskType.DIGITAL_BABA_CHAT, prompt, effectiveChild)
                    },
                    onRequestLiveGuidance = { txt ->
                        viewModel.requestLiveGuidance(family, "Question from ${effectiveChild.displayName}", txt)
                    }
                )

                1 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        com.example.ui.components.AsyncDrawableImage(
                            resId = R.drawable.img_storyland_banner,
                            contentDescription = "Storyland illustrated bedtime sky",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    KidsAiChatTab(
                        mode = IdentityMode.BABA_COMPANION,
                        subtitle = "Storyland weaves age-appropriate bedtime stories from child-permitted approved family memories.",
                        turns = data.kidsStoryTurns,
                        suggestions = listOf(
                            "Tell a cozy bedtime story about curiosity",
                            "Turn an approved family memory into a fable",
                            "Tell a short story about teamwork"
                        ),
                        placeholder = "Ask Storyland for a story...",
                        aiBusy = aiBusy,
                        aiError = aiError,
                        onDismissError = viewModel::clearAiError,
                        onSend = { prompt ->
                            viewModel.sendFamilyAiChat(data, OrchestratorTaskType.KIDS_STORYLAND, prompt, effectiveChild)
                        },
                        onRequestLiveGuidance = null
                    )
                }

                2 -> {
                    var selectedSubject by remember { mutableStateOf("Math") }
                    val subjectStarters = when (selectedSubject) {
                        "Math" -> listOf(
                            "Help me understand fractions step-by-step for age ${effectiveChild.ageBand}",
                            "Give me a 3-question word problem quiz"
                        )
                        "Science" -> listOf(
                            "Give me a fun science riddle for age ${effectiveChild.ageBand}",
                            "Explain how water turns into rain"
                        )
                        "Reading" -> listOf(
                            "Help me practice reading comprehension & vocabulary",
                            "Ask me 3 questions about honesty and kindness"
                        )
                        else -> listOf(
                            "Quiz me on space and constellations",
                            "Give me a quick 3-question study check"
                        )
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Math", "Science", "Reading", "Daily Quiz").forEach { subj ->
                                FilterChip(
                                    selected = selectedSubject == subj,
                                    onClick = { selectedSubject = subj },
                                    label = { Text(subj) }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        KidsAiChatTab(
                            mode = IdentityMode.BABA_COMPANION,
                            subtitle = "Learning Academy ($selectedSubject) adapts explanations & mini-quizzes for Age Band ${effectiveChild.ageBand}.",
                            turns = data.kidsLearningTurns,
                            suggestions = subjectStarters,
                            placeholder = "Ask Learning Academy ($selectedSubject)...",
                            aiBusy = aiBusy,
                            aiError = aiError,
                            onDismissError = viewModel::clearAiError,
                            onSend = { prompt ->
                                viewModel.sendFamilyAiChat(data, OrchestratorTaskType.KIDS_LEARNING, prompt, effectiveChild)
                            },
                            onRequestLiveGuidance = null
                        )
                    }
                }

                3 -> KidsRealBabaMessagesTab(data = data, viewModel = viewModel)

                4 -> KidsMemoriesTab(data = data, viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KidsAiChatTab(
    mode: IdentityMode,
    subtitle: String,
    turns: List<com.example.domain.model.AiTurn>,
    suggestions: List<String>,
    placeholder: String,
    aiBusy: Boolean,
    aiError: String?,
    onDismissError: () -> Unit,
    onSend: (String) -> Unit,
    onRequestLiveGuidance: ((String) -> Unit)?
) {
    var prompt by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize()) {
        ModeDisclosureHeader(mode = mode, subtitle = subtitle)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { s ->
                AssistChip(
                    onClick = { prompt = s },
                    label = { Text(s, maxLines = 1) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (turns.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "Ready when you are",
                        description = "Tap one of the safe starters above or type your own question below.",
                        icon = if (mode == IdentityMode.DIGITAL_BABA) Icons.Default.AutoStories else Icons.Default.School
                    )
                }
            } else {
                items(turns, key = { it.id }) { turn ->
                    AiTurnBubble(turn = turn, onRequestLiveGuidance = onRequestLiveGuidance)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ChatComposerBar(
            value = prompt,
            onValueChange = { prompt = it },
            placeholder = placeholder,
            isBusy = aiBusy,
            errorMessage = aiError,
            onDismissError = onDismissError,
            onSend = {
                val p = prompt
                prompt = ""
                onSend(p)
            },
            inputTestTag = "kids_chat_input",
            sendTestTag = "kids_chat_send_button"
        )
    }
}

@Composable
private fun KidsRealBabaMessagesTab(
    data: BabaWorkspaceData,
    viewModel: BabaWorkspaceViewModel
) {
    val family = data.activeFamily ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerHelper = remember(context) { VoiceRecorderHelper(context) }
    DisposableEffect(playerHelper) {
        onDispose { playerHelper.release() }
    }
    var playingId by remember { mutableStateOf<String?>(null) }
    var audioNote by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ModeDisclosureHeader(
            mode = IdentityMode.REAL_BABA,
            subtitle = "Real messages & genuine voice recordings from your family. Audio never auto-plays."
        )
        if (!audioNote.isNullOrBlank()) {
            Text(audioNote.orEmpty(), style = MaterialTheme.typography.bodySmall, color = AlertCoral)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (data.familyMessages.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No Real Baba messages yet",
                        description = "Genuine human messages sent to the family will appear here with explicit human-authored badges."
                    )
                }
            } else {
                items(data.familyMessages, key = { it.id }) { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IdentityModeBadge(mode = IdentityMode.REAL_BABA)
                                Text(
                                    text = "From ${msg.senderName}",
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
                                                val pathResult = viewModel.resolveAudioPlaybackPath(
                                                    context = context,
                                                    familyId = family.id,
                                                    localAudioPath = msg.localAudioPath,
                                                    mediaId = msg.mediaId
                                                )
                                                pathResult.onSuccess { resolvedPath ->
                                                    playerHelper.playFile(
                                                        path = resolvedPath,
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
                                    },
                                    modifier = Modifier.testTag("kids_play_voice_${msg.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = "Play genuine voice message"
                                    )
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Text(if (isPlaying) "Stop Audio" else "Play Voice Note (${msg.audioDurationSec}s)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KidsMemoriesTab(
    data: BabaWorkspaceData,
    viewModel: BabaWorkspaceViewModel
) {
    val family = data.activeFamily ?: return
    val mediaCache by viewModel.mediaCache.collectAsStateWithLifecycle()
    val childPermitted = data.memories.filter {
        it.reviewStatus == "approved" && it.sharingScope != "private" && it.memoryClass != "wife_father"
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (childPermitted.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No child-permitted memories yet",
                    description = "Approved shared stories will appear here once shared by a parent."
                )
            }
        } else {
            items(childPermitted, key = { it.id }) { mem ->
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
                        if (mem.mediaId.isNotBlank()) {
                            SyncedMediaImage(
                                media = mediaCache[mem.mediaId],
                                onRequestLoad = { viewModel.ensureMediaLoaded(family.id, mem.mediaId) },
                                contentDescription = "Family photo ${mem.title}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        Text(mem.content, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Source: ${mem.provenanceNote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
