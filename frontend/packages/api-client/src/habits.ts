import { jsonBody, request } from './api';
import type { ActivityLog, Habit } from './types';

export type HabitInput = Pick<Habit, 'name' | 'category' | 'frequency' | 'daysOfWeek' | 'preferredTime'>;

export function listHabits(): Promise<Habit[]> {
  return request('/api/habits');
}

export function getHabit(id: string): Promise<Habit> {
  return request(`/api/habits/${encodeURIComponent(id)}`);
}

export function saveHabit(input: HabitInput, id?: string): Promise<Habit> {
  return request(id ? `/api/habits/${encodeURIComponent(id)}` : '/api/habits', {
    method: id ? 'PUT' : 'POST',
    body: jsonBody(input)
  });
}

export function deleteHabit(id: string): Promise<void> {
  return request(`/api/habits/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export function logHabit(id: string, loggedAt?: string): Promise<ActivityLog> {
  return request(`/api/habits/${encodeURIComponent(id)}/logs`, {
    method: 'POST',
    body: jsonBody(loggedAt ? { loggedAt } : {})
  });
}

export function getHabitLogs(id: string): Promise<ActivityLog[]> {
  return request(`/api/habits/${encodeURIComponent(id)}/logs`);
}
