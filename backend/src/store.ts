import crypto from "node:crypto";
import type {
  AiIdentityRecord,
  FamilyMemberRecord,
  FamilyRecord,
  GuidanceRecord,
  InvitationRecord,
  MemoryRecord,
  PlanId,
  PrivateMessageRecord,
  SafetyEventRecord,
  UsageRecord,
} from "./models.ts";

export interface ValidatedMediaMetadata {
  id: string;
  familyId: string;
  ownerUid: string;
  mimeType: string;
  byteSize: number;
  sharingScope: "private" | "selected_members" | "family";
  scanStatus: "validated";
  createdAtIso: string;
}

export class InMemoryBackendStore {
  families = new Map<string, FamilyRecord>();
  members = new Map<string, Map<string, FamilyMemberRecord>>();
  invitations = new Map<string, InvitationRecord>();
  aiIdentities = new Map<string, AiIdentityRecord[]>();
  memories = new Map<string, MemoryRecord[]>();
  guidance = new Map<string, GuidanceRecord[]>();
  privateMessages = new Map<string, PrivateMessageRecord[]>();
  usageRecords = new Map<string, UsageRecord[]>();
  safetyEvents = new Map<string, SafetyEventRecord[]>();
  mediaRegistry = new Map<string, ValidatedMediaMetadata[]>();

  createId(prefix: string): string {
    return `${prefix}_${crypto.randomUUID().replace(/-/g, "").slice(0, 12)}`;
  }

  getFamily(familyId: string): FamilyRecord | undefined {
    return this.families.get(familyId);
  }

  getMember(familyId: string, uid: string): FamilyMemberRecord | undefined {
    return this.members.get(familyId)?.get(uid);
  }

  createFamily(params: {
    ownerUid: string;
    ownerName: string;
    familyName: string;
    role: "father" | "wife";
    planId?: PlanId;
  }): FamilyRecord {
    const id = this.createId("fam");
    const now = new Date().toISOString();
    const record: FamilyRecord = {
      id,
      name: params.familyName,
      createdBy: params.ownerUid,
      status: "active",
      planId: params.planId ?? "family_plus",
      memberUids: [params.ownerUid],
      adultUids: [params.ownerUid],
      createdAtIso: now,
      updatedAtIso: now,
    };
    this.families.set(id, record);
    const memberMap = new Map<string, FamilyMemberRecord>();
    memberMap.set(params.ownerUid, {
      familyId: id,
      uid: params.ownerUid,
      displayName: params.ownerName,
      role: params.role,
      ageBand: "adult",
      consentState: "granted",
      membershipStatus: "active",
    });
    this.members.set(id, memberMap);
    return record;
  }

  getApprovedIdentity(familyId: string): AiIdentityRecord | null {
    const list = this.aiIdentities.get(familyId) ?? [];
    return list.find((i) => i.status === "approved") ?? null;
  }

  getMonthlyUsageUnits(familyId: string, periodKey: string): number {
    const list = this.usageRecords.get(familyId) ?? [];
    return list.filter((u) => u.periodKey === periodKey).reduce((acc, item) => acc + item.units, 0);
  }

  addUsage(record: UsageRecord): void {
    const list = this.usageRecords.get(record.familyId) ?? [];
    list.push(record);
    this.usageRecords.set(record.familyId, list);
  }

  addSafetyEvent(event: SafetyEventRecord): void {
    const list = this.safetyEvents.get(event.familyId) ?? [];
    list.push(event);
    this.safetyEvents.set(event.familyId, list);
  }

  addMediaMetadata(item: ValidatedMediaMetadata): void {
    const list = this.mediaRegistry.get(item.familyId) ?? [];
    list.push(item);
    this.mediaRegistry.set(item.familyId, list);
  }
}
