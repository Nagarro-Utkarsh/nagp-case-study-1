import { apiUrl } from './api';

export function openDashboardStream(token: string): EventSource {
  const url = new URL(apiUrl('/api/analytics/stream'), window.location.href);
  url.searchParams.set('token', token);
  return new EventSource(url);
}
