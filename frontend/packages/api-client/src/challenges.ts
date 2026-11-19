import { jsonBody, request } from './api';
import type { Challenge, ChallengeDetail, Community, CommunityDetail, Invite } from './types';

export function listChallenges(): Promise<Challenge[]> {
  return request('/api/challenges');
}

export function getChallenge(id: string): Promise<ChallengeDetail> {
  return request(`/api/challenges/${encodeURIComponent(id)}`);
}

export function joinChallenge(id: string, habitId: string): Promise<unknown> {
  return request(`/api/challenges/${encodeURIComponent(id)}/join`, {
    method: 'POST',
    body: jsonBody({ habitId })
  });
}

export function listCommunities(): Promise<Community[]> {
  return request('/api/communities');
}

export function getCommunity(id: string): Promise<CommunityDetail> {
  return request(`/api/communities/${encodeURIComponent(id)}`);
}

export function createCommunity(input: Pick<Community, 'name' | 'description' | 'visibility'>): Promise<Community> {
  return request('/api/communities', { method: 'POST', body: jsonBody(input) });
}

export function joinCommunity(id: string): Promise<Community> {
  return request(`/api/communities/${encodeURIComponent(id)}/join`, { method: 'POST' });
}

export function listInvites(): Promise<Invite[]> {
  return request('/api/invites');
}

export function respondToInvite(id: string, action: 'accept' | 'decline'): Promise<Invite> {
  return request(`/api/invites/${encodeURIComponent(id)}/${action}`, { method: 'POST' });
}

export function inviteByEmail(communityId: string, email: string): Promise<Invite> {
  return request(`/api/communities/${encodeURIComponent(communityId)}/invites`, {
    method: 'POST',
    body: jsonBody({ email })
  });
}

export function createChallenge(
  communityId: string,
  input: Omit<Challenge, 'id' | 'communityId'>
): Promise<Challenge> {
  return request(`/api/communities/${encodeURIComponent(communityId)}/challenges`, {
    method: 'POST',
    body: jsonBody(input)
  });
}
