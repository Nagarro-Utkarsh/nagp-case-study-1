import { jsonBody, request } from './api';
import type { Habit, Recommendation } from './types';

export function getRecommendations(): Promise<Recommendation[]> {
  return request('/api/recommendations');
}

export function applyRecommendation(input: {
  habitId: string;
  preferredTime?: string;
  frequency?: string;
  daysOfWeek?: number[];
}): Promise<Habit> {
  return request('/api/recommendations/apply', {
    method: 'POST',
    body: jsonBody(input)
  });
}
