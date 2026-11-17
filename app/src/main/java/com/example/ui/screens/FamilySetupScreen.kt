package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.FamilyRole
import com.example.domain.model.Invitation
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LanguageSelectorFlowRow
import com.example.ui.components.StatusBannerBar

@Composable
fun FamilySetupScreen(
    displayName: String,
    invitations: List<Invitation>,
    statusBanner: String?,
    onDismissBanner: () -> Unit,
    onCreateFamily: (String, FamilyRole) -> Unit,
    onAcceptInvitation: (Invitation) -> Unit,
    selectedLocale: String = "en",
    onSelectLocale: (String) -> Unit = {}
) {
    var familyName by remember { mutableStateOf("$displayName Family") }
    var selectedRole by remember { mutableStateOf(FamilyRole.FATHER) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 620.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            StatusBannerBar(message = statusBanner, onDismiss = onDismissBanner)

            Text(
                text = "${stringResource(R.string.setup_welcome_prefix)}, ${displayName.ifBlank { "Family Member" }}",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = stringResource(R.string.select_language_title)
                        )
                        Text(
                            text = stringResource(R.string.select_language_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    LanguageSelectorFlowRow(
                        selectedCode = selectedLocale,
                        onSelectLocale = onSelectLocale
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FamilyRestroom,
                            contentDescription = stringResource(R.string.create_workspace_title),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(R.string.create_workspace_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    OutlinedTextField(
                        value = familyName,
                        onValueChange = { familyName = it },
                        label = { Text(stringResource(R.string.workspace_name_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("family_name_input")
                    )

                    Text(stringResource(R.string.your_role_label), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(FamilyRole.FATHER, FamilyRole.WIFE).forEach { role ->
                            val localizedRole = if (role == FamilyRole.FATHER) {
                                stringResource(R.string.nav_father)
                            } else {
                                stringResource(R.string.nav_wife)
                            }
                            FilterChip(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role },
                                label = { Text(localizedRole) },
                                modifier = Modifier.testTag("role_chip_${role.apiValue}")
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Guardian policy",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = stringResource(R.string.child_policy_note),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Button(
                        onClick = { onCreateFamily(familyName, selectedRole) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("create_family_button")
                    ) {
                        Text(stringResource(R.string.create_workspace_button))
                    }
                }
            }

            Text(stringResource(R.string.pending_invites_title), style = MaterialTheme.typography.titleLarge)

            val pendingInvites = invitations.filter { it.status == "pending" }
            if (pendingInvites.isEmpty()) {
                EmptyStateCard(
                    title = stringResource(R.string.no_pending_invites),
                    description = stringResource(R.string.no_pending_invites_desc),
                    icon = Icons.Default.MarkEmailRead
                )
            } else {
                pendingInvites.forEach { invite ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(invite.familyName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Role: ${invite.role.uppercase()} • Age Band: ${invite.ageBand}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { onAcceptInvitation(invite) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("accept_invite_button")
                            ) {
                                Text(stringResource(R.string.accept_join_button))
                            }
                        }
                    }
                }
            }
        }
    }
}
