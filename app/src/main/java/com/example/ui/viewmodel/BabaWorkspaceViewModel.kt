package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.BabaOrchestrator
import com.example.ai.FirebaseBabaAiProvider
import com.example.ai.OrchestratorTaskType
import com.example.core.AppLanguage
import com.example.core.AppLocaleHelper
import com.example.core.MediaValidationResult
import com.example.core.MediaValidator
import com.example.core.unwrapFirestoreException
import com.example.data.repository.FamilyRepository
import com.example.data.repository.GovernanceRepository
import com.example.data.repository.IdentityMemoryRepository
import com.example.data.repository.MessagingHubRepository
import com.example.domain.model.AgeBand
import com.example.domain.model.AiIdentity
import com.example.domain.model.AiTurn
import com.example.domain.model.AppSurface
import com.example.domain.model.AuditLog
import com.example.domain.model.ConsentRecord
import com.example.domain.model.DataRequest
import com.example.domain.model.FamilyEvent
import com.example.domain.model.FamilyGuidance
import com.example.domain.model.FamilyMedia
import com.example.domain.model.FamilyMember
import com.example.domain.model.FamilyMemory
import com.example.domain.model.FamilyMessage
import com.example.domain.model.FamilyNotification
import com.example.domain.model.FamilyRole
import com.example.domain.model.FamilyWorkspace
import com.example.domain.model.IdentityMode
import com.example.domain.model.Invitation
import com.example.domain.model.PlanQuotaTier
import com.example.domain.model.PrivateMessage
import com.example.domain.model.SafetyEvent
import com.example.domain.model.UsageRecord
import com.example.domain.model.UserAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class BabaWorkspaceData(
    val userAccount: UserAccount? = null,
    val families: List<FamilyWorkspace> = emptyList(),
    val activeFamily: FamilyWorkspace? = null,
    val members: List<FamilyMember> = emptyList(),
    val currentMember: FamilyMember? = null,
    val invitations: List<Invitation> = emptyList(),
    val identities: List<AiIdentity> = emptyList(),
    val memories: List<FamilyMemory> = emptyList(),
    val guidanceList: List<FamilyGuidance> = emptyList(),
    val familyMessages: List<FamilyMessage> = emptyList(),
    val familyEvents: List<FamilyEvent> = emptyList(),
    val privateMessages: List<PrivateMessage> = emptyList(),
    val digitalBabaTurns: List<AiTurn> = emptyList(),
    val kidsStoryTurns: List<AiTurn> = emptyList(),
    val kidsLearningTurns: List<AiTurn> = emptyList(),
    val consents: List<ConsentRecord> = emptyList(),
    val safetyEvents: List<SafetyEvent> = emptyList(),
    val dataRequests: List<DataRequest> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val notifications: List<FamilyNotification> = emptyList(),
    val usageRecords: List<UsageRecord> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class BabaWorkspaceViewModel(
    private val currentUserId: String,
    private val currentUserName: String,
    private val currentUserEmail: String,
    private val familyRepository: FamilyRepository,
    private val identityMemoryRepository: IdentityMemoryRepository,
    private val messagingHubRepository: MessagingHubRepository,
    private val governanceRepository: GovernanceRepository,
    private val orchestrator: BabaOrchestrator = BabaOrchestrator(FirebaseBabaAiProvider()),
    private val initialLocale: String = "en",
    private val preLoginPendingLocale: Boolean = false,
    private val onPreLoginLocaleCommitted: () -> Unit = {},
    private val onLocaleChanged: (String) -> Unit = {}
) : ViewModel() {

    private val _selectedSurface = MutableStateFlow<AppSurface?>(null)
    val selectedSurface: StateFlow<AppSurface?> = _selectedSurface.asStateFlow()

    private val _supervisedChildId = MutableStateFlow<String?>(null)
    val supervisedChildId: StateFlow<String?> = _supervisedChildId.asStateFlow()

    private val _activeLocale = MutableStateFlow(AppLanguage.fromCode(initialLocale).code)
    val activeLocale: StateFlow<String> = _activeLocale.asStateFlow()

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _previewResponse = MutableStateFlow<String?>(null)
    val previewResponse: StateFlow<String?> = _previewResponse.asStateFlow()

    private val _mediaCache = MutableStateFlow<Map<String, FamilyMedia>>(emptyMap())
    val mediaCache: StateFlow<Map<String, FamilyMedia>> = _mediaCache.asStateFlow()

    init {
        viewModelScope.launch {
            familyRepository.ensureUserAccount(currentUserName, locale = _activeLocale.value)
            if (preLoginPendingLocale) {
                familyRepository.updateUserPreferences(locale = _activeLocale.value)
                onPreLoginLocaleCommitted()
            }
        }
    }

    private fun <T> safeFlow(flow: kotlinx.coroutines.flow.Flow<T>, fallback: T, label: String): kotlinx.coroutines.flow.Flow<T> =
        flow.catch { error ->
            val fsErr = error.unwrapFirestoreException()
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Stream error in $label", fsErr ?: error)
            emit(fallback)
        }

    private val baseFlow = combine(
        safeFlow(familyRepository.observeUserAccount(currentUserId), null, "userAccount"),
        safeFlow(familyRepository.observeFamiliesForUser(), emptyList(), "families"),
        safeFlow(familyRepository.observeInvitationsForUser(null), emptyList(), "invitations"),
        safeFlow(messagingHubRepository.observePrivateMessages(WIFE_PRIVATE_CONV_ID), emptyList(), "privateMessages")
    ) { account, families, invitations, privateMsgs ->
        Quad(account, families, invitations, privateMsgs)
    }

    val workspaceState: StateFlow<UiState<BabaWorkspaceData>> = baseFlow
        .flatMapLatest { (account, families, invitations, privateMsgs) ->
            val activeFamily = families.firstOrNull { it.id == account?.activeFamilyId } ?: families.firstOrNull()
            if (activeFamily == null) {
                flowOf<UiState<BabaWorkspaceData>>(
                    UiState.Success(
                        BabaWorkspaceData(
                            userAccount = account,
                            families = families,
                            activeFamily = null,
                            invitations = invitations,
                            privateMessages = privateMsgs
                        )
                    )
                )
            } else {
                val fid = activeFamily.id
                val isAdult = currentUserId in activeFamily.adultUids

                val coreFamilyFlow = combine(
                    safeFlow(familyRepository.observeFamilyMembers(fid), emptyList(), "members"),
                    safeFlow(identityMemoryRepository.observeDigitalBabaIdentities(fid), emptyList(), "identities"),
                    safeFlow(identityMemoryRepository.observePermittedMemories(fid), emptyList(), "memories"),
                    safeFlow(identityMemoryRepository.observeGuidance(fid, isAdult), emptyList(), "guidance")
                ) { members, identities, memories, guidance ->
                    Quad(members, identities, memories, guidance)
                }

                val turnsFlow = combine(
                    safeFlow(messagingHubRepository.observeAiSessionTurns(fid, "digital_baba_$currentUserId"), emptyList(), "digitalBabaTurns"),
                    safeFlow(messagingHubRepository.observeAiSessionTurns(fid, "kids_story_$currentUserId"), emptyList(), "kidsStoryTurns"),
                    safeFlow(messagingHubRepository.observeAiSessionTurns(fid, "kids_learning_$currentUserId"), emptyList(), "kidsLearningTurns")
                ) { dbTurns, storyTurns, learnTurns ->
                    Triple(dbTurns, storyTurns, learnTurns)
                }

                val hubFlow = combine(
                    safeFlow(messagingHubRepository.observeFamilyMessages(fid), emptyList(), "messages"),
                    safeFlow(messagingHubRepository.observeFamilyEvents(fid), emptyList(), "events"),
                    safeFlow(messagingHubRepository.observeNotifications(fid), emptyList(), "notifications"),
                    turnsFlow
                ) { msgs, events, notifications, turns ->
                    Quad(msgs, events, notifications, turns)
                }

                val govFlow = combine(
                    safeFlow(governanceRepository.observeConsents(fid), emptyList(), "consents"),
                    if (isAdult) safeFlow(governanceRepository.observeSafetyEvents(fid), emptyList(), "safetyEvents") else flowOf(emptyList()),
                    safeFlow(governanceRepository.observeDataRequests(fid), emptyList(), "dataRequests"),
                    if (isAdult) safeFlow(governanceRepository.observeAuditLogs(fid), emptyList(), "auditLogs") else flowOf(emptyList()),
                    safeFlow(governanceRepository.observeUsageRecords(fid, isAdult), emptyList(), "usageRecords")
                ) { consents, safety, dataReqs, audits, usage ->
                    Quint(consents, safety, dataReqs, audits, usage)
                }

                combine(coreFamilyFlow, hubFlow, govFlow) { core, hub, gov ->
                    val currentMember = core.first.firstOrNull { it.uid == currentUserId }
                    UiState.Success(
                        BabaWorkspaceData(
                            userAccount = account,
                            families = families,
                            activeFamily = activeFamily,
                            members = core.first,
                            currentMember = currentMember,
                            invitations = invitations,
                            identities = core.second,
                            memories = core.third,
                            guidanceList = core.fourth,
                            familyMessages = hub.first,
                            familyEvents = hub.second,
                            notifications = hub.third,
                            privateMessages = privateMsgs,
                            digitalBabaTurns = hub.fourth.first,
                            kidsStoryTurns = hub.fourth.second,
                            kidsLearningTurns = hub.fourth.third,
                            consents = gov.first,
                            safetyEvents = gov.second,
                            dataRequests = gov.third,
                            auditLogs = gov.fourth,
                            usageRecords = gov.fifth
                        )
                    ) as UiState<BabaWorkspaceData>
                }
            }
        }
        .catch { error ->
            val fsErr = error.unwrapFirestoreException()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).localizedMessage ?: "Failed to load family workspace."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun selectSurface(surface: AppSurface) {
        _selectedSurface.value = surface
    }

    fun selectSupervisedChild(childId: String?) {
        _supervisedChildId.value = childId
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun clearAiError() {
        _aiError.value = null
    }

    fun createFamily(familyName: String, role: FamilyRole) {
        if (familyName.isBlank()) {
            _statusBanner.value = "Please enter a family workspace name."
            return
        }
        viewModelScope.launch {
            val result = familyRepository.createFamilyWorkspace(familyName, currentUserName, role)
            result.onSuccess { familyId ->
                governanceRepository.recordConsent(
                    familyId = familyId,
                    subjectUid = currentUserId,
                    purpose = "Adult participation, AI transparency, and family workspace processing",
                    status = "granted",
                    adultUids = listOf(currentUserId)
                )
                governanceRepository.appendAuditLog(
                    familyId = familyId,
                    action = "CREATE_FAMILY_WORKSPACE",
                    resourceType = "families",
                    resourceId = familyId,
                    outcome = "allowed",
                    adultUids = listOf(currentUserId)
                )
                _selectedSurface.value = if (role == FamilyRole.WIFE) AppSurface.WIFE else AppSurface.FATHER
                _statusBanner.value = "Family workspace created."
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Unable to create family workspace."
            }
        }
    }

    fun acceptInvitation(invitation: Invitation) {
        viewModelScope.launch {
            familyRepository.acceptInvitation(invitation, currentUserName)
                .onSuccess {
                    _statusBanner.value = "Joined ${invitation.familyName}."
                }
                .onFailure { e ->
                    _statusBanner.value = e.localizedMessage ?: "Unable to accept invitation."
                }
        }
    }

    fun inviteMember(family: FamilyWorkspace, email: String, role: FamilyRole, ageBand: AgeBand) {
        if (email.isBlank() || !email.contains("@")) {
            _statusBanner.value = "Enter a valid email address."
            return
        }
        viewModelScope.launch {
            familyRepository.createInvitation(
                familyId = family.id,
                familyName = family.name,
                invitedEmail = email,
                role = role,
                ageBand = ageBand
            ).onSuccess { inviteId ->
                governanceRepository.appendAuditLog(
                    familyId = family.id,
                    action = "CREATE_INVITATION",
                    resourceType = "invitations",
                    resourceId = inviteId,
                    outcome = "allowed",
                    adultUids = family.adultUids
                )
                _statusBanner.value = "Expiring single-use invite created for ${email.trim()}."
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to create invitation."
            }
        }
    }

    fun revokeInvitation(inviteId: String) {
        viewModelScope.launch {
            familyRepository.revokeInvitation(inviteId)
                .onSuccess { _statusBanner.value = "Invitation revoked." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to revoke invitation." }
        }
    }

    fun addChildProfile(family: FamilyWorkspace, childName: String, ageBand: AgeBand) {
        if (childName.isBlank()) {
            _statusBanner.value = "Enter the child's name."
            return
        }
        viewModelScope.launch {
            familyRepository.addManagedChildProfile(family.id, childName, ageBand, family.memberUids)
                .onSuccess { childId ->
                    governanceRepository.recordConsent(
                        familyId = family.id,
                        subjectUid = childId,
                        purpose = "Guardian-authorized child onboarding (${ageBand.label}) pending regional legal review",
                        status = "granted",
                        adultUids = family.adultUids
                    )
                    governanceRepository.appendAuditLog(
                        familyId = family.id,
                        action = "CHILD_ONBOARDING_CONSENT",
                        resourceType = "members",
                        resourceId = childId,
                        outcome = "allowed",
                        adultUids = family.adultUids
                    )
                    _supervisedChildId.value = childId
                    _statusBanner.value = "Child profile added with guardian consent."
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to add child profile." }
        }
    }

    fun saveAndMaybeApproveIdentity(
        family: FamilyWorkspace,
        existing: AiIdentity?,
        communicationStyle: String,
        values: String,
        interests: String,
        bioSummary: String,
        approveNow: Boolean
    ) {
        if (communicationStyle.isBlank() || values.isBlank() || bioSummary.isBlank()) {
            _statusBanner.value = "Please fill in style, values, and bio summary."
            return
        }
        viewModelScope.launch {
            val nextVer = (existing?.activeVersion ?: 0L) + 1L
            val newStatus = if (approveNow) "approved" else "draft"
            identityMemoryRepository.saveDigitalBabaIdentity(
                familyId = family.id,
                identityId = existing?.id,
                communicationStyle = communicationStyle,
                values = values,
                interests = interests.ifBlank { "Family connection, learning, storytelling" },
                bioSummary = bioSummary,
                memberUids = family.memberUids,
                status = newStatus,
                nextVersion = nextVer
            ).onSuccess { id ->
                governanceRepository.appendAuditLog(
                    familyId = family.id,
                    action = if (approveNow) "APPROVE_DIGITAL_BABA_V$nextVer" else "SAVE_DIGITAL_BABA_DRAFT",
                    resourceType = "aiIdentities",
                    resourceId = id,
                    outcome = "allowed",
                    adultUids = family.adultUids
                )
                _statusBanner.value = if (approveNow) "Digital Baba v$nextVer approved for family." else "Digital Baba draft v$nextVer saved."
            }.onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to save Digital Baba identity." }
        }
    }

    fun suspendIdentity(family: FamilyWorkspace, identity: AiIdentity) {
        viewModelScope.launch {
            val nextVer = identity.activeVersion + 1L
            identityMemoryRepository.updateIdentityStatus(family.id, identity.id, "suspended", nextVer)
                .onSuccess {
                    governanceRepository.appendAuditLog(
                        familyId = family.id,
                        action = "SUSPEND_DIGITAL_BABA",
                        resourceType = "aiIdentities",
                        resourceId = identity.id,
                        outcome = "allowed",
                        adultUids = family.adultUids
                    )
                    _statusBanner.value = "Digital Baba suspended. Existing Real Baba messages remain available."
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to suspend identity." }
        }
    }

    fun previewDigitalBabaSample(
        data: BabaWorkspaceData,
        sampleQuestion: String,
        draftIdentity: AiIdentity
    ) {
        val family = data.activeFamily ?: return
        val member = data.currentMember ?: return
        viewModelScope.launch {
            _aiBusy.value = true
            _aiError.value = null
            try {
                val res = orchestrator.handleRequest(
                    familyId = family.id,
                    requester = member.copy(consentState = "granted"),
                    taskType = OrchestratorTaskType.DIGITAL_BABA_CHAT,
                    userPrompt = sampleQuestion,
                    digitalBabaIdentity = draftIdentity.copy(status = "approved"),
                    allMemories = data.memories,
                    allGuidance = data.guidanceList,
                    preferredLocale = _activeLocale.value
                )
                _previewResponse.value = "[${res.response.identityMode.label} | ${res.response.sourceType.label}]\n${res.response.responseText}"
            } catch (e: TimeoutCancellationException) {
                _aiError.value = "Preview timed out after 60 seconds. Please try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Digital Baba preview failed", e)
                _aiError.value = e.localizedMessage ?: "Preview failed."
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun addMemory(
        family: FamilyWorkspace,
        title: String,
        content: String,
        memoryClass: String,
        sharingScope: String,
        allowedMemberIds: List<String>,
        provenanceNote: String,
        approveImmediately: Boolean
    ) {
        if (title.isBlank() || content.isBlank()) {
            _statusBanner.value = "Please enter both a memory title and content."
            return
        }
        viewModelScope.launch {
            identityMemoryRepository.createMemory(
                familyId = family.id,
                memoryClass = memoryClass,
                title = title,
                content = content,
                sourceType = "text",
                provenanceNote = provenanceNote.ifBlank { "Supplied by $currentUserName (ownership confirmed)" },
                sharingScope = sharingScope,
                allowedMemberIds = allowedMemberIds,
                memberUids = family.memberUids,
                reviewStatus = if (approveImmediately) "approved" else "draft"
            ).onSuccess { id ->
                governanceRepository.appendAuditLog(
                    familyId = family.id,
                    action = "CREATE_MEMORY",
                    resourceType = "memories",
                    resourceId = id,
                    outcome = "allowed",
                    adultUids = family.adultUids
                )
                _statusBanner.value = "Memory saved (${if (approveImmediately) "approved" else "draft"})."
            }.onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to save memory." }
        }
    }

    fun updateMemoryStatus(family: FamilyWorkspace, memoryId: String, status: String) {
        viewModelScope.launch {
            identityMemoryRepository.updateMemoryReviewStatus(family.id, memoryId, status)
                .onSuccess { _statusBanner.value = "Memory marked $status." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to update memory." }
        }
    }

    fun approveLiveGuidance(
        family: FamilyWorkspace,
        guidanceId: String?,
        topic: String,
        question: String,
        answerText: String,
        audience: String,
        existingVersion: Long = 0L
    ) {
        if (topic.isBlank() || answerText.isBlank()) {
            _statusBanner.value = "Provide both a topic and approved guidance answer."
            return
        }
        viewModelScope.launch {
            val readers = when (audience) {
                "wife" -> family.adultUids
                "child" -> family.memberUids
                else -> family.memberUids
            }
            val nextVer = if (guidanceId == null) 1L else existingVersion + 1L
            identityMemoryRepository.approveGuidance(
                familyId = family.id,
                guidanceId = guidanceId,
                topic = topic,
                question = question.ifBlank { topic },
                answerText = answerText,
                audience = audience,
                readableByUids = readers,
                memberUids = family.memberUids,
                adultUids = family.adultUids,
                nextVersion = nextVer
            ).onSuccess { id ->
                governanceRepository.appendAuditLog(
                    familyId = family.id,
                    action = "APPROVE_GUIDANCE_V$nextVer",
                    resourceType = "guidance",
                    resourceId = id,
                    outcome = "allowed",
                    adultUids = family.adultUids
                )
                _statusBanner.value = "Live Guidance v$nextVer approved and published for $audience."
            }.onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to approve guidance." }
        }
    }

    fun requestLiveGuidance(family: FamilyWorkspace, topic: String, question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            identityMemoryRepository.requestLiveGuidance(
                familyId = family.id,
                topic = topic.ifBlank { "Family question" },
                question = question,
                memberUids = family.memberUids,
                adultUids = family.adultUids
            ).onSuccess {
                messagingHubRepository.createNotification(
                    familyId = family.id,
                    title = "Live Guidance Request",
                    body = "$currentUserName asked: ${question.take(120)}",
                    type = "live_guidance_request",
                    recipientUids = family.adultUids
                )
                _statusBanner.value = "Sent Live Guidance request to authorized family adults."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to request guidance."
            }
        }
    }

    fun uploadPhotoAndCreateMemory(
        context: Context,
        family: FamilyWorkspace,
        title: String,
        content: String,
        imageUri: Uri,
        memoryClass: String,
        sharingScope: String,
        allowedMemberIds: List<String>,
        provenanceNote: String,
        approveImmediately: Boolean
    ) {
        if (title.isBlank() || content.isBlank()) {
            _statusBanner.value = "Please enter both a memory title and caption before attaching a photo."
            return
        }
        viewModelScope.launch {
            val validation = withContext(Dispatchers.IO) {
                MediaValidator.compressImageUriValidated(context, imageUri)
            }
            when (validation) {
                is MediaValidationResult.Invalid -> {
                    _statusBanner.value = validation.reason
                }
                is MediaValidationResult.Valid -> {
                    val visibleTo = when (sharingScope) {
                        "private" -> listOf(currentUserId)
                        "selected_members" -> (allowedMemberIds + currentUserId).distinct()
                        else -> (family.memberUids + currentUserId).distinct()
                    }
                    val mediaIdResult = messagingHubRepository.uploadMedia(
                        familyId = family.id,
                        validated = validation,
                        sharingScope = sharingScope,
                        visibleToUids = visibleTo
                    )
                    mediaIdResult.onSuccess { mediaId ->
                        governanceRepository.recordUsage(family.id, "media_upload", 1L, 1L, family.adultUids)
                        identityMemoryRepository.createMemory(
                            familyId = family.id,
                            memoryClass = memoryClass,
                            title = title,
                            content = content,
                            sourceType = "image",
                            provenanceNote = provenanceNote.ifBlank { "Supplied by $currentUserName (photo verified & compressed)" },
                            sharingScope = sharingScope,
                            allowedMemberIds = allowedMemberIds,
                            memberUids = family.memberUids,
                            reviewStatus = if (approveImmediately) "approved" else "draft",
                            mediaId = mediaId
                        ).onSuccess { memId ->
                            governanceRepository.appendAuditLog(
                                familyId = family.id,
                                action = "CREATE_PHOTO_MEMORY",
                                resourceType = "memories",
                                resourceId = memId,
                                outcome = "allowed",
                                adultUids = family.adultUids
                            )
                            _statusBanner.value = "Photo memory uploaded & synced across family devices."
                        }.onFailure {
                            _statusBanner.value = it.localizedMessage ?: "Failed to save photo memory."
                        }
                    }.onFailure {
                        _statusBanner.value = it.localizedMessage ?: "Failed to upload photo media."
                    }
                }
            }
        }
    }

    fun ensureMediaLoaded(familyId: String, mediaId: String) {
        if (mediaId.isBlank() || _mediaCache.value.containsKey(mediaId)) return
        viewModelScope.launch {
            messagingHubRepository.fetchMedia(familyId, mediaId).onSuccess { media ->
                if (media != null) {
                    _mediaCache.value = _mediaCache.value + (mediaId to media)
                }
            }
        }
    }

    suspend fun resolveAudioPlaybackPath(
        context: Context,
        familyId: String,
        localAudioPath: String,
        mediaId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (localAudioPath.isNotBlank() && File(localAudioPath).exists()) {
            return@withContext Result.success(localAudioPath)
        }
        if (mediaId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Recorded audio file is only stored on the sender's device."))
        }
        val cached = _mediaCache.value[mediaId]
        val media = cached ?: messagingHubRepository.fetchMedia(familyId, mediaId).getOrNull()?.also {
            _mediaCache.value = _mediaCache.value + (mediaId to it)
        } ?: return@withContext Result.failure(IllegalStateException("Audio attachment unavailable or permission restricted."))
        val decodedFile = MediaValidator.decodeBase64ToCacheFile(context, mediaId, media.mimeType, media.payloadBase64)
        Result.success(decodedFile.absolutePath)
    }

    fun sendFamilyMessage(
        family: FamilyWorkspace,
        member: FamilyMember,
        text: String,
        recipientScope: String,
        selectedRecipientUids: List<String>,
        isVoiceNote: Boolean = false,
        audioDurationSec: Long = 0L,
        localAudioPath: String = ""
    ) {
        if (text.isBlank()) {
            _statusBanner.value = "Please enter a message or transcript."
            return
        }
        val tier = PlanQuotaTier.fromPlanId(family.planId)
        if (isVoiceNote && !tier.voiceMessagesEnabled) {
            _statusBanner.value = "Cross-device voice messages require Family Plus or Premium plan."
            return
        }
        viewModelScope.launch {
            val visibleTo = if (recipientScope == "family") {
                family.memberUids
            } else {
                (selectedRecipientUids + currentUserId).distinct()
            }
            var syncedMediaId = ""
            if (isVoiceNote && localAudioPath.isNotBlank()) {
                val validation = withContext(Dispatchers.IO) {
                    MediaValidator.readAudioFileValidated(File(localAudioPath))
                }
                when (validation) {
                    is MediaValidationResult.Invalid -> {
                        _statusBanner.value = validation.reason
                        return@launch
                    }
                    is MediaValidationResult.Valid -> {
                        val uploadRes = messagingHubRepository.uploadMedia(
                            familyId = family.id,
                            validated = validation,
                            sharingScope = recipientScope,
                            visibleToUids = visibleTo
                        )
                        if (uploadRes.isFailure) {
                            _statusBanner.value = uploadRes.exceptionOrNull()?.localizedMessage ?: "Voice upload failed."
                            return@launch
                        }
                        syncedMediaId = uploadRes.getOrThrow()
                        governanceRepository.recordUsage(family.id, "voice_note", 1L, 1L, family.adultUids)
                    }
                }
            }

            messagingHubRepository.sendGenuineFamilyMessage(
                familyId = family.id,
                senderName = member.displayName,
                senderRole = member.role,
                recipientScope = recipientScope,
                visibleToUids = visibleTo,
                messageKind = if (isVoiceNote) "voice_note" else "text",
                textContent = text,
                audioDurationSec = audioDurationSec,
                localAudioPath = localAudioPath,
                mediaId = syncedMediaId,
                memberUids = family.memberUids
            ).onSuccess {
                messagingHubRepository.createNotification(
                    familyId = family.id,
                    title = if (isVoiceNote) "New Real Baba Voice Message" else "New Family Message from ${member.displayName}",
                    body = text.take(160),
                    type = "real_baba_message",
                    recipientUids = visibleTo
                )
                _statusBanner.value = if (isVoiceNote) "Genuine human voice message synced for family." else "Genuine message sent."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to send message."
            }
        }
    }

    fun addCalendarEvent(
        family: FamilyWorkspace,
        title: String,
        description: String,
        dateLabel: String,
        category: String,
        childVisible: Boolean
    ) {
        if (title.isBlank() || dateLabel.isBlank()) {
            _statusBanner.value = "Enter event title and date."
            return
        }
        viewModelScope.launch {
            messagingHubRepository.createFamilyEvent(
                familyId = family.id,
                title = title,
                description = description,
                eventDateLabel = dateLabel,
                category = category,
                childVisible = childVisible,
                memberUids = family.memberUids,
                adultUids = family.adultUids
            ).onSuccess {
                messagingHubRepository.createNotification(
                    familyId = family.id,
                    title = "Calendar Event: $title",
                    body = dateLabel,
                    type = "family_event",
                    recipientUids = if (childVisible) family.memberUids else family.adultUids
                )
                _statusBanner.value = "Family event added to shared calendar."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to add event."
            }
        }
    }

    private fun checkMonthlyAiQuota(data: BabaWorkspaceData, taskType: OrchestratorTaskType): String? {
        val family = data.activeFamily ?: return "No active family workspace."
        val tier = PlanQuotaTier.fromPlanId(family.planId)
        if (taskType == OrchestratorTaskType.DIGITAL_BABA_CHAT && !tier.digitalBabaEnabled) {
            return "Digital Baba requires Family Starter or higher plan. Upgrade under Safety & Admin."
        }
        val period = governanceRepository.currentPeriodKey()
        val usedUnits = data.usageRecords
            .filter { it.periodKey == period && it.service == "ai_turn" }
            .sumOf { it.units }
        if (usedUnits >= tier.monthlyAiUnitsLimit) {
            return "Monthly AI limit reached ($usedUnits / ${tier.monthlyAiUnitsLimit} turns on ${tier.displayName}). Upgrade plan under Safety & Admin."
        }
        return null
    }

    fun sendPrivateWifeCompanionMessage(data: BabaWorkspaceData, prompt: String) {
        if (prompt.isBlank()) return
        val family = data.activeFamily ?: return
        val member = data.currentMember ?: return
        checkMonthlyAiQuota(data, OrchestratorTaskType.COMPANION_CHAT)?.let {
            _aiError.value = it
            return
        }
        viewModelScope.launch {
            _aiBusy.value = true
            _aiError.value = null
            try {
                messagingHubRepository.ensurePrivateConversation(WIFE_PRIVATE_CONV_ID).getOrThrow()
                messagingHubRepository.appendPrivateMessage(WIFE_PRIVATE_CONV_ID, "user", prompt).getOrThrow()

                val res = orchestrator.handleRequest(
                    familyId = family.id,
                    requester = member.copy(role = FamilyRole.WIFE.apiValue, ageBand = AgeBand.ADULT.apiValue),
                    taskType = OrchestratorTaskType.COMPANION_CHAT,
                    userPrompt = prompt,
                    digitalBabaIdentity = null,
                    allMemories = emptyList(),
                    allGuidance = emptyList(),
                    preferredLocale = _activeLocale.value
                )
                messagingHubRepository.appendPrivateMessage(
                    conversationId = WIFE_PRIVATE_CONV_ID,
                    senderType = "ai",
                    text = res.response.responseText,
                    safetyStatus = res.response.safetyStatus.apiValue
                ).getOrThrow()

                governanceRepository.recordUsage(family.id, "ai_turn", 1L, 1L, family.adultUids)

                if (res.safetyCategory != null && res.safetyMetadata != null) {
                    governanceRepository.logSafetyEvent(
                        familyId = family.id,
                        category = res.safetyCategory,
                        severity = res.safetySeverity,
                        summaryMetadata = res.safetyMetadata,
                        adultUids = family.adultUids
                    )
                    messagingHubRepository.createNotification(
                        familyId = family.id,
                        title = "Safety Alert (${res.safetySeverity.uppercase()})",
                        body = res.safetyMetadata.take(200),
                        type = "safety_escalation",
                        recipientUids = family.adultUids
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Wife companion timed out", e)
                _aiError.value = "BABA Companion timed out after 60 seconds. Please retry."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Wife companion error", e)
                _aiError.value = e.localizedMessage ?: "Failed to reach BABA Companion."
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun sendFamilyAiChat(
        data: BabaWorkspaceData,
        taskType: OrchestratorTaskType,
        prompt: String,
        asChildMember: FamilyMember? = null
    ) {
        if (prompt.isBlank()) return
        val family = data.activeFamily ?: return
        val effectiveMember = asChildMember ?: data.currentMember ?: return
        checkMonthlyAiQuota(data, taskType)?.let {
            _aiError.value = it
            return
        }
        val sessionId = when (taskType) {
            OrchestratorTaskType.DIGITAL_BABA_CHAT -> "digital_baba_$currentUserId"
            OrchestratorTaskType.KIDS_STORYLAND -> "kids_story_$currentUserId"
            OrchestratorTaskType.KIDS_LEARNING -> "kids_learning_$currentUserId"
            OrchestratorTaskType.COMPANION_CHAT -> "companion_$currentUserId"
        }
        val surfaceName = when (taskType) {
            OrchestratorTaskType.DIGITAL_BABA_CHAT -> "digital_baba"
            OrchestratorTaskType.KIDS_STORYLAND -> "storyland"
            OrchestratorTaskType.KIDS_LEARNING -> "learning_academy"
            OrchestratorTaskType.COMPANION_CHAT -> "companion"
        }
        val mode = if (taskType == OrchestratorTaskType.DIGITAL_BABA_CHAT) {
            IdentityMode.DIGITAL_BABA
        } else {
            IdentityMode.BABA_COMPANION
        }
        val guardianOversight = effectiveMember.role == FamilyRole.CHILD.apiValue
        val approvedIdentity = data.identities.firstOrNull { it.status == "approved" }

        viewModelScope.launch {
            _aiBusy.value = true
            _aiError.value = null
            try {
                messagingHubRepository.ensureAiSession(
                    familyId = family.id,
                    sessionId = sessionId,
                    surface = surfaceName,
                    agentMode = mode,
                    guardianOversight = guardianOversight,
                    adultUids = family.adultUids
                ).getOrThrow()

                messagingHubRepository.appendAiTurn(
                    familyId = family.id,
                    sessionId = sessionId,
                    senderType = "user",
                    text = prompt,
                    aiResponse = null,
                    guardianOversight = guardianOversight,
                    adultUids = family.adultUids
                ).getOrThrow()

                val result = orchestrator.handleRequest(
                    familyId = family.id,
                    requester = effectiveMember,
                    taskType = taskType,
                    userPrompt = prompt,
                    digitalBabaIdentity = approvedIdentity,
                    allMemories = data.memories,
                    allGuidance = data.guidanceList,
                    preferredLocale = _activeLocale.value
                )

                messagingHubRepository.appendAiTurn(
                    familyId = family.id,
                    sessionId = sessionId,
                    senderType = "ai",
                    text = result.response.responseText,
                    aiResponse = result.response,
                    guardianOversight = guardianOversight,
                    adultUids = family.adultUids
                ).getOrThrow()

                governanceRepository.recordUsage(family.id, "ai_turn", 1L, 1L, family.adultUids)

                if (result.response.requiresHumanInput && result.response.escalationType == "live_guidance") {
                    identityMemoryRepository.requestLiveGuidance(
                        familyId = family.id,
                        topic = "Question from ${effectiveMember.displayName}",
                        question = prompt,
                        memberUids = family.memberUids,
                        adultUids = family.adultUids
                    )
                    messagingHubRepository.createNotification(
                        familyId = family.id,
                        title = "Live Guidance Needed",
                        body = "${effectiveMember.displayName} asked Digital Baba: ${prompt.take(120)}",
                        type = "live_guidance_request",
                        recipientUids = family.adultUids
                    )
                }

                if (result.safetyCategory != null && result.safetyMetadata != null) {
                    governanceRepository.logSafetyEvent(
                        familyId = family.id,
                        category = result.safetyCategory,
                        severity = result.safetySeverity,
                        summaryMetadata = result.safetyMetadata,
                        adultUids = family.adultUids
                    )
                    messagingHubRepository.createNotification(
                        familyId = family.id,
                        title = "Safety Alert (${result.safetySeverity.uppercase()})",
                        body = result.safetyMetadata.take(200),
                        type = "safety_escalation",
                        recipientUids = family.adultUids
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI generation timed out", e)
                _aiError.value = "Response timed out after 60 seconds. Please retry."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI generation failed", e)
                _aiError.value = e.localizedMessage ?: "AI generation failed."
            } finally {
                _aiBusy.value = false
            }
        }
    }

    fun reportChildConcern(family: FamilyWorkspace, note: String) {
        viewModelScope.launch {
            governanceRepository.logSafetyEvent(
                familyId = family.id,
                category = "user_report",
                severity = "high",
                summaryMetadata = "Child/user tapped Help & Report: ${note.take(160)}",
                adultUids = family.adultUids
            ).onSuccess {
                messagingHubRepository.createNotification(
                    familyId = family.id,
                    title = "Urgent Child Help Alert",
                    body = note.take(180),
                    type = "safety_escalation",
                    recipientUids = family.adultUids
                )
                _statusBanner.value = "Help request sent to your trusted family adults."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Unable to send report."
            }
        }
    }

    fun toggleConsent(family: FamilyWorkspace, consent: ConsentRecord) {
        val next = if (consent.status == "granted") "revoked" else "granted"
        viewModelScope.launch {
            governanceRepository.updateConsentStatus(family.id, consent.id, next)
                .onSuccess {
                    governanceRepository.appendAuditLog(
                        familyId = family.id,
                        action = "CONSENT_${next.uppercase()}",
                        resourceType = "consents",
                        resourceId = consent.id,
                        outcome = "allowed",
                        adultUids = family.adultUids
                    )
                    _statusBanner.value = "Consent updated to $next."
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to update consent." }
        }
    }

    fun submitPrivacyRequest(family: FamilyWorkspace, type: String, notes: String) {
        viewModelScope.launch {
            governanceRepository.submitDataRequest(family.id, type, notes, family.adultUids)
                .onSuccess { id ->
                    governanceRepository.appendAuditLog(
                        familyId = family.id,
                        action = "DATA_REQUEST_${type.uppercase()}",
                        resourceType = "dataRequests",
                        resourceId = id,
                        outcome = "allowed",
                        adultUids = family.adultUids
                    )
                    _statusBanner.value = "Privacy $type request submitted and logged."
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to submit privacy request." }
        }
    }

    fun exportUserData(
        context: Context,
        family: FamilyWorkspace,
        notes: String,
        onExportFileReady: (File) -> Unit
    ) {
        viewModelScope.launch {
            governanceRepository.executeDataExport(
                context = context,
                familyId = family.id,
                notes = notes.ifBlank { "Full JSON archive export" },
                adultUids = family.adultUids
            ).onSuccess { (_, file) ->
                governanceRepository.recordUsage(family.id, "data_export", 1L, 0L, family.adultUids)
                _statusBanner.value = "Export bundle created (${file.name}). Opening ShareSheet..."
                onExportFileReady(file)
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Data export failed."
            }
        }
    }

    fun deletePersonalData(family: FamilyWorkspace, notes: String) {
        viewModelScope.launch {
            governanceRepository.executeSelfDataDeletion(
                familyId = family.id,
                notes = notes.ifBlank { "Cascading self-deletion of private conversations & owned records" },
                adultUids = family.adultUids
            ).onSuccess {
                _statusBanner.value = "Private companion messages, owned memories/media, and active consents deleted/revoked."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Self-deletion failed."
            }
        }
    }

    fun updateSafetyEvent(family: FamilyWorkspace, eventId: String, status: String) {
        viewModelScope.launch {
            governanceRepository.updateSafetyEventStatus(family.id, eventId, status)
                .onSuccess { _statusBanner.value = "Safety event marked $status." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to update safety event." }
        }
    }

    fun toggleQuietHours(enabled: Boolean) {
        viewModelScope.launch {
            familyRepository.updateUserPreferences(quietHoursEnabled = enabled)
                .onSuccess { _statusBanner.value = if (enabled) "Quiet hours enabled." else "Quiet hours disabled." }
        }
    }

    fun updateLanguage(context: Context, localeCode: String) {
        val normalized = AppLocaleHelper.applyLocale(context, localeCode, markPreLoginPending = false)
        _activeLocale.value = normalized
        onLocaleChanged(normalized)
        val lang = AppLanguage.fromCode(normalized)
        _statusBanner.value = "${lang.nativeName} (${lang.englishName})"
        viewModelScope.launch {
            familyRepository.updateUserPreferences(locale = normalized)
        }
    }

    fun switchActiveFamily(family: FamilyWorkspace) {
        viewModelScope.launch {
            familyRepository.updateUserPreferences(activeFamilyId = family.id)
                .onSuccess {
                    _statusBanner.value = "${family.name} active."
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Failed to switch family workspace."
                }
        }
    }

    fun toggleMemberStatus(family: FamilyWorkspace, targetMember: FamilyMember) {
        if (targetMember.uid == currentUserId) {
            _statusBanner.value = "You cannot suspend your own active admin session."
            return
        }
        val nextStatus = if (targetMember.membershipStatus == "active") "suspended" else "active"
        val nextConsent = if (nextStatus == "active") "granted" else "revoked"
        viewModelScope.launch {
            familyRepository.updateFamilyMemberControl(
                familyId = family.id,
                memberUid = targetMember.uid,
                consentState = nextConsent,
                membershipStatus = nextStatus
            ).onSuccess {
                governanceRepository.appendAuditLog(
                    familyId = family.id,
                    action = "MEMBER_${nextStatus.uppercase()}",
                    resourceType = "members",
                    resourceId = targetMember.uid,
                    outcome = "allowed",
                    adultUids = family.adultUids
                )
                _statusBanner.value = "${targetMember.displayName}: $nextStatus."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to update member status."
            }
        }
    }

    fun sendSafeCheckInPulse(family: FamilyWorkspace, member: FamilyMember, note: String) {
        sendFamilyMessage(
            family = family,
            member = member,
            text = note,
            recipientScope = "family",
            selectedRecipientUids = family.memberUids,
            isVoiceNote = false
        )
    }

    fun switchPlan(family: FamilyWorkspace, planId: String) {
        viewModelScope.launch {
            familyRepository.updateFamilyPlan(family.id, planId)
                .onSuccess {
                    governanceRepository.appendAuditLog(
                        familyId = family.id,
                        action = "SWITCH_PLAN_${planId.uppercase()}",
                        resourceType = "families",
                        resourceId = family.id,
                        outcome = "allowed",
                        adultUids = family.adultUids
                    )
                    val tier = PlanQuotaTier.fromPlanId(planId)
                    _statusBanner.value = "Workspace plan updated to ${tier.displayName} (${tier.monthlyAiUnitsLimit} AI turns/mo)."
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Unable to switch plan." }
        }
    }

    fun markNotificationRead(familyId: String, notificationId: String) {
        viewModelScope.launch {
            messagingHubRepository.markNotificationRead(familyId, notificationId)
        }
    }

    fun currentPeriodKey(): String = governanceRepository.currentPeriodKey()

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    companion object {
        private const val TAG = "BabaWorkspaceVM"
        const val WIFE_PRIVATE_CONV_ID = "wife_companion_main"
    }
}
