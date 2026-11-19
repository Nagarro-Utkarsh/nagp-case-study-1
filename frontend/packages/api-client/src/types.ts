export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}

export interface Profile {
  id: string;
  tenantId: string;
  email: string;
  name: string;
  timezone: string;
  createdAt: string;
}

export interface Habit {
  id: string;
  name: string;
  category: string;
  frequency: 'DAILY' | 'WEEKLY';
  daysOfWeek: number[];
  preferredTime: string | null;
  createdAt?: string;
}

export interface ActivityLog {
  id: string;
  habitId: string;
  loggedAt: string;
  source: string;
}

export interface DashboardSummary {
  habitId: string;
  name: string;
  category: string;
  todayCount: number;
  doneToday: boolean;
  currentStreak: number;
  completionPercentage: number;
}

export interface Trend {
  habitId: string;
  days: Array<{ date: string; count: number }>;
}

export interface Cohort {
  signupWeek: string;
  totalUsers: number;
  activeUsers: number;
  activePercentage: number;
}

export interface Community {
  id: string;
  name: string;
  description: string | null;
  visibility: 'PUBLIC' | 'PRIVATE';
  createdBy: string;
}

export interface Challenge {
  id: string;
  communityId: string;
  title: string;
  description: string | null;
  habitCategory: string;
  targetCount: number;
  startDate: string;
  endDate: string;
}

export interface Invite {
  id: string;
  communityId: string;
  status: string;
}

export interface ChallengeDetail {
  challenge: Challenge;
  leaderboard: Array<{
    userId: string;
    name: string;
    count: number | null;
    targetCount: number;
  }>;
}

export interface CommunityDetail {
  community: Community;
  members: Array<{ userId: string; role: string; joinedAt: string }>;
  challenges: Challenge[];
}

export interface Partner {
  partner: string;
  available: boolean;
}

export interface Connection {
  id: string;
  partner: string;
  habitId: string;
  status: string;
  connectedAt: string;
}

export interface CreatedConnection extends Pick<Connection, 'id' | 'partner' | 'habitId' | 'connectedAt'> {
  apiKey: string;
}

export interface EventReceipt {
  id: string;
  eventId: string;
  duplicate: boolean;
}

export interface PartnerEvent {
  eventId: string;
  payload: Record<string, unknown>;
  status: 'RECEIVED' | 'PROCESSED' | 'REJECTED' | string;
  rejectReason: string | null;
  publishAttempts: number;
  receivedAt: string;
}

export interface Recommendation {
  habitId: string;
  type: 'TIMING' | 'FREQUENCY' | string;
  current: string;
  suggested: string;
  reason: string;
}
