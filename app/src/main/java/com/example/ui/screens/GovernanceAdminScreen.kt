package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.domain.model.PlanQuotaTier
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBannerBar
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.ConnectionMintSoft
import com.example.ui.theme.TrustNavyDark
import com.example.ui.viewmodel.BabaWorkspaceData
import com.example.ui.viewmodel.BabaWorkspaceViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GovernanceAdminScreen(
    data: BabaWorkspaceData,
    viewModel: BabaWorkspaceViewModel,
    statusBanner: String?
) {
    val family = data.activeFamily ?: return
    val context = LocalContext.current
    val quietHours = data.userAccount?.quietHoursEnabled ?: false
    val currentLocale = data.userAccount?.locale ?: "en"
    var requestNote by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val tier = PlanQuotaTier.fromPlanId(family.planId)
    val periodKey = viewModel.currentPeriodKey()
    val monthlyAiUsed = data.usageRecords
        .filter { it.periodKey == periodKey && it.service == "ai_turn" }
        .sumOf { it.units }
    val monthlyMediaUsed = data.usageRecords
        .filter { it.periodKey == periodKey && (it.service == "voice_note" || it.service == "media_upload") }
        .sumOf { it.units }
    val estimatedCostCents = data.usageRecords
        .filter { it.periodKey == periodKey }
        .sumOf { it.estimatedCostUsdCents }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Your Private & Owned Data?") },
            text = {
                Text(
                    "This immediately deletes your private BABA Companion conversations, deletes memories and media you own, revokes your active consents, and logs a completed deletion request."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePersonalData(
                            family = family,
                            notes = requestNote.ifBlank { "Cascading self-deletion executed from Safety & Admin" }
                        )
                        requestNote = ""
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertCoral)
                ) {
                    Text("Confirm Permanent Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") }
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = "Governance & safety")
                Text("Safety, Privacy & Governance", style = MaterialTheme.typography.headlineLarge)
            }

            Surface(
                color = ConnectionMintSoft,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Private boundary", tint = TrustNavyDark)
                    Text(
                        text = "Privacy Isolation Guarantee: Admin governance displays safety escalation metadata, consents, and audit logs only. Private Wife Companion conversations remain invisible here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TrustNavyDark
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.NotificationsPaused, contentDescription = "Quiet hours")
                        Column {
                            Text("Quiet Hours Protection", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Pause non-urgent family prompts & AI reminders overnight.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = quietHours,
                        onCheckedChange = { viewModel.toggleQuietHours(it) },
                        modifier = Modifier.testTag("quiet_hours_switch")
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = "Language preference")
                        Text("Language / اللغة", style = MaterialTheme.typography.titleMedium)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = currentLocale != "ar",
                            onClick = { viewModel.updateLanguage(context, "en") },
                            label = { Text("English") },
                            modifier = Modifier.testTag("locale_en_chip")
                        )
                        FilterChip(
                            selected = currentLocale == "ar",
                            onClick = { viewModel.updateLanguage(context, "ar") },
                            label = { Text("العربية") },
                            modifier = Modifier.testTag("locale_ar_chip")
                        )
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.DataUsage, contentDescription = "Plan & quota metering")
                            Text("Plan & Usage Quotas ($periodKey)", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "${tier.displayName} (${tier.monthlyPriceLabel})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    val progress = (monthlyAiUsed.toFloat() / tier.monthlyAiUnitsLimit.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "AI turns this month: $monthlyAiUsed / ${tier.monthlyAiUnitsLimit} • Media uploads: $monthlyMediaUsed • Est. metering: $${"%.2f".format(estimatedCostCents / 100.0)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlanQuotaTier.entries.forEach { plan ->
                            FilterChip(
                                selected = family.planId == plan.planId,
                                onClick = { viewModel.switchPlan(family, plan.planId) },
                                label = { Text("${plan.displayName} (${plan.monthlyAiUnitsLimit})") },
                                modifier = Modifier.testTag("plan_chip_${plan.planId}")
                            )
                        }
                    }
                }
            }

            Text("Consent Records (${data.consents.size})", style = MaterialTheme.typography.titleLarge)
            if (data.consents.isEmpty()) {
                EmptyStateCard(
                    title = "No consent records yet",
                    description = "Adult and guardian child consent statuses are tracked here."
                )
            } else {
                data.consents.forEach { consent ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(consent.purpose, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Subject: ${consent.subjectUid} • Status: ${consent.status.uppercase()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { viewModel.toggleConsent(family, consent) }) {
                                Text(if (consent.status == "granted") "Revoke" else "Grant")
                            }
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
                        Icon(Icons.Default.Download, contentDescription = "Data export & deletion")
                        Text("Privacy Export & Deletion Requests", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedTextField(
                        value = requestNote,
                        onValueChange = { requestNote = it },
                        label = { Text("Scope or Reason (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                val note = requestNote.ifBlank { "Family JSON archive export" }
                                viewModel.exportUserData(context, family, note) { file ->
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "BABA Family Privacy Export (${family.name})")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(shareIntent, "Share or Save BABA JSON Export"))
                                    }
                                }
                                requestNote = ""
                            },
                            modifier = Modifier.weight(1f).testTag("request_export_button")
                        ) {
                            Text("Export JSON Archive")
                        }
                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertCoral),
                            modifier = Modifier.weight(1f).testTag("request_delete_button")
                        ) {
                            Text("Delete My Data")
                        }
                    }
                    data.dataRequests.forEach { req ->
                        Text(
                            text = "• ${req.requestType.uppercase()} (${req.status}) — ${req.notes}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = "Safety queue")
                Text("Safety Escalation Queue (${data.safetyEvents.size})", style = MaterialTheme.typography.titleLarge)
            }
            if (data.safetyEvents.isEmpty()) {
                EmptyStateCard(
                    title = "No safety escalations",
                    description = "High-risk child events, emergency escalations, or user help reports appear here with minimal metadata."
                )
            } else {
                data.safetyEvents.forEach { ev ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${ev.category.uppercase()} • Severity: ${ev.severity} • ${ev.status.uppercase()}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(ev.summaryMetadata, style = MaterialTheme.typography.bodyMedium)
                            if (ev.status != "resolved") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { viewModel.updateSafetyEvent(family, ev.id, "reviewed") }) {
                                        Text("Mark Reviewed")
                                    }
                                    TextButton(onClick = { viewModel.updateSafetyEvent(family, ev.id, "resolved") }) {
                                        Text("Resolve")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text("Immutable Audit Log (${data.auditLogs.size})", style = MaterialTheme.typography.titleLarge)
            data.auditLogs.take(20).forEach { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${log.action} • ${log.outcome.uppercase()}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "Resource: ${log.resourceType}/${log.resourceId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
