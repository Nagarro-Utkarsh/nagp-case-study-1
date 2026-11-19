import { jsonBody, request } from './api';
import type { Connection, CreatedConnection, EventReceipt, Partner, PartnerEvent } from './types';

export function listPartners(): Promise<Partner[]> {
  return request('/api/integrations/partners');
}

export function listConnections(): Promise<Connection[]> {
  return request('/api/integrations/connections');
}

export function connectPartner(partner: string, habitId: string): Promise<CreatedConnection> {
  return request('/api/integrations/connections', {
    method: 'POST',
    body: jsonBody({ partner, habitId })
  });
}

export function disconnectPartner(id: string): Promise<void> {
  return request(`/api/integrations/connections/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export function simulateEvent(
  connectionId: string,
  event: { metric: string; value: number; occurredAt?: string; eventId?: string }
): Promise<EventReceipt> {
  return request(`/api/integrations/connections/${encodeURIComponent(connectionId)}/simulate`, {
    method: 'POST',
    body: jsonBody(event)
  });
}

export function getEvents(connectionId: string): Promise<PartnerEvent[]> {
  return request(`/api/integrations/connections/${encodeURIComponent(connectionId)}/events`);
}
