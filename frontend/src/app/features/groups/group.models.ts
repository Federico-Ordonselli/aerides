/** Mirrors the backend MembershipRole, from the weakest to the strongest. */
export type MembershipRole = 'MEMBER' | 'OFFICER' | 'OWNER';

const ROLE_ORDER: readonly MembershipRole[] = ['MEMBER', 'OFFICER', 'OWNER'];

/** True if `role` has at least the permissions of `required` (OWNER > OFFICER > MEMBER). */
export function hasRole(role: MembershipRole | undefined, required: MembershipRole): boolean {
  return role !== undefined && ROLE_ORDER.indexOf(role) >= ROLE_ORDER.indexOf(required);
}

export interface GroupSummary {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  myRole: MembershipRole;
  memberCount: number;
}

export interface GroupDetail {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  myRole: MembershipRole;
  memberCount: number;
  /** Only for OFFICER and OWNER. */
  inviteCode: string | null;
  hasDiscordWebhook: boolean;
  /** Only for the OWNER. */
  discordWebhookUrl: string | null;
  createdAt: string;
}

export interface Member {
  userId: number;
  displayName: string;
  avatarUrl: string | null;
  role: MembershipRole;
  joinedAt: string;
}

export interface GroupRequest {
  name: string;
  description: string | null;
}
