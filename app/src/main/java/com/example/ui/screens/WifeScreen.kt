package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ai.OrchestratorTaskType
import com.example.domain.model.AiTurn
import com.example.domain.model.IdentityMode
import com.example.domain.model.SourceType
import com.example.ui.components.AiTurnBubble
import com.example.ui.components.ChatComposerBar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ModeDisclosureHeader
import com.example.ui.components.StatusBannerBar
import com.example.ui.viewmodel.BabaWorkspaceData
import com.example.ui.viewmodel.BabaWorkspaceViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WifeScreen(
    data: BabaWorkspaceData,
    viewModel: BabaWorkspaceViewModel,
    aiBusy: Boolean,
    aiError: String?,
    statusBanner: String?
) {
    val family = data.activeFamily ?: return
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Private Companion", "Digital Baba", "Shared Planning")

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title) },
                    modifier = Modifier.testTag("wife_tab_$idx")
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
                0 -> {
                    var prompt by remember { mutableStateOf("") }
                    ModeDisclosureHeader(
                        mode = IdentityMode.BABA_COMPANION,
                        isPrivate = true,
                        subtitle = "Owner-only private sanctuary — never readable by Father, children, or family admins."
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "Help me organize our family week calmly",
                            "I need emotional encouragement today",
                            "Suggest a supportive family routine"
                        ).forEach { suggestion ->
                            AssistChip(
                                onClick = { prompt = suggestion },
                                label = { Text(suggestion, maxLines = 1) }
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
                        if (data.privateMessages.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "Private Companion Space",
                                    description = "Ask for household planning, thoughtful reflection, or daily support. Conversations here are strictly scoped to your account.",
                                    icon = Icons.Default.Lock
                                )
                            }
                        } else {
                            items(data.privateMessages, key = { it.id }) { msg ->
                                AiTurnBubble(
                                    turn = AiTurn(
                                        id = msg.id,
                                        senderType = msg.senderType,
                                        text = msg.text,
                                        identityMode = IdentityMode.BABA_COMPANION.apiValue,
                                        sourceType = SourceType.GENERAL_AI.apiValue,
                                        safetyStatus = msg.safetyStatus,
                                        disclosure = IdentityMode.BABA_COMPANION.disclosureText
                                    )
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    ChatComposerBar(
                        value = prompt,
                        onValueChange = { prompt = it },
                        placeholder = "Message Private BABA Companion...",
                        isBusy = aiBusy,
                        errorMessage = aiError,
                        onDismissError = viewModel::clearAiError,
                        onSend = {
                            val toSend = prompt
                            prompt = ""
                            viewModel.sendPrivateWifeCompanionMessage(data, toSend)
                        },
                        inputTestTag = "wife_private_input",
                        sendTestTag = "wife_private_send_button"
                    )
                }

                1 -> {
                    var prompt by remember { mutableStateOf("") }
                    ModeDisclosureHeader(
                        mode = IdentityMode.DIGITAL_BABA,
                        subtitle = "Grounded strictly in Father-approved guidance & shared memories. Missing topics escalate to Live Guidance."
                    )
                    Spacer(Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (data.digitalBabaTurns.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "Digital Baba Reflection",
                                    description = "Ask about Father's documented principles, family priorities, or memories. If no approved stance exists, a Live Guidance request will be created."
                                )
                            }
                        } else {
                            items(data.digitalBabaTurns, key = { it.id }) { turn ->
                                AiTurnBubble(
                                    turn = turn,
                                    onRequestLiveGuidance = { text ->
                                        viewModel.requestLiveGuidance(family, "Follow-up from Wife", text)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    ChatComposerBar(
                        value = prompt,
                        onValueChange = { prompt = it },
                        placeholder = "Ask Digital Baba...",
                        isBusy = aiBusy,
                        errorMessage = aiError,
                        onDismissError = viewModel::clearAiError,
                        onSend = {
                            val toSend = prompt
                            prompt = ""
                            viewModel.sendFamilyAiChat(
                                data = data,
                                taskType = OrchestratorTaskType.DIGITAL_BABA_CHAT,
                                prompt = toSend
                            )
                        },
                        inputTestTag = "wife_digital_baba_input",
                        sendTestTag = "wife_digital_baba_send_button"
                    )
                }

                2 -> {
                    var memTitle by remember { mutableStateOf("") }
                    var memContent by remember { mutableStateOf("") }
                    var memScope by remember { mutableStateOf("family") }
                    var eventTitle by remember { mutableStateOf("") }
                    var eventDate by remember { mutableStateOf("") }
                    var eventDesc by remember { mutableStateOf("") }
                    var eventChildVisible by remember { mutableStateOf(true) }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val wifePhotoPicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickVisualMedia()
                    ) { uri ->
                        if (uri != null) {
                            viewModel.uploadPhotoAndCreateMemory(
                                context = context,
                                family = family,
                                title = memTitle,
                                content = memContent,
                                imageUri = uri,
                                memoryClass = "shared_family",
                                sharingScope = memScope,
                                allowedMemberIds = emptyList(),
                                provenanceNote = "Contributed by Wife",
                                approveImmediately = true
                            )
                            memTitle = ""
                            memContent = ""
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
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
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Contribute memory")
                                    Text("Contribute Shared Family Memory", style = MaterialTheme.typography.titleMedium)
                                }
                                OutlinedTextField(
                                    value = memTitle,
                                    onValueChange = { memTitle = it },
                                    label = { Text("Memory Title") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = memContent,
                                    onValueChange = { memContent = it },
                                    label = { Text("Story or Tradition") },
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("family", "private").forEach { s ->
                                        FilterChip(
                                            selected = memScope == s,
                                            onClick = { memScope = s },
                                            label = { Text(if (s == "family") "Whole Family" else "Only Me") }
                                        )
                                    }
                                }
                                OutlinedButton(
                                    enabled = memTitle.isNotBlank() && memContent.isNotBlank(),
                                    onClick = {
                                        wifePhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach photo")
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    Text("Attach Photo from Gallery")
                                }
                                Button(
                                    onClick = {
                                        viewModel.addMemory(
                                            family = family,
                                            title = memTitle,
                                            content = memContent,
                                            memoryClass = "shared_family",
                                            sharingScope = memScope,
                                            allowedMemberIds = emptyList(),
                                            provenanceNote = "Contributed by Wife",
                                            approveImmediately = true
                                        )
                                        memTitle = ""
                                        memContent = ""
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("wife_save_memory_button")
                                ) {
                                    Text("Save Memory with Sharing Control")
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
                                    Icon(Icons.Default.Event, contentDescription = "Add event")
                                    Text("Add Shared Calendar Event", style = MaterialTheme.typography.titleMedium)
                                }
                                OutlinedTextField(
                                    value = eventTitle,
                                    onValueChange = { eventTitle = it },
                                    label = { Text("Event Title") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = eventDate,
                                    onValueChange = { eventDate = it },
                                    label = { Text("Date / Time (e.g., Sunday 6:00 PM)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = eventDesc,
                                    onValueChange = { eventDesc = it },
                                    label = { Text("Notes") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = eventChildVisible,
                                        onCheckedChange = { eventChildVisible = it }
                                    )
                                    Text("Visible to children in Family Hub")
                                }
                                Button(
                                    onClick = {
                                        viewModel.addCalendarEvent(
                                            family = family,
                                            title = eventTitle,
                                            description = eventDesc,
                                            dateLabel = eventDate,
                                            category = "family_routine",
                                            childVisible = eventChildVisible
                                        )
                                        eventTitle = ""
                                        eventDate = ""
                                        eventDesc = ""
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("wife_add_event_button")
                                ) {
                                    Text("Add to Shared Calendar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
