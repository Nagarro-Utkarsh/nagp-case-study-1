import { request } from './api';
import type { Cohort, DashboardSummary, Trend } from './types';

export function getDashboard(): Promise<DashboardSummary[]> {
  return request('/api/analytics/dashboard');
}

export function getTrend(habitId: string, days = 30): Promise<Trend> {
  return request(`/api/analytics/habits/${encodeURIComponent(habitId)}/trend?days=${days}`);
}

export function getCohorts(): Promise<Cohort[]> {
  return request('/api/analytics/cohorts');
}
