import { jsonBody, request } from './api';
import type { Profile } from './types';

export interface Credentials {
  email: string;
  password: string;
  tenantSlug: string;
}

export interface SignupDetails extends Credentials {
  name: string;
}

export async function login(credentials: Credentials): Promise<string> {
  const result = await request<{ token: string }>('/api/auth/login', {
    method: 'POST',
    authenticated: false,
    body: jsonBody(credentials)
  });
  return result.token;
}

export async function signup(details: SignupDetails): Promise<string> {
  const result = await request<{ token: string }>('/api/auth/signup', {
    method: 'POST',
    authenticated: false,
    body: jsonBody(details)
  });
  return result.token;
}

export function getProfile(): Promise<Profile> {
  return request('/api/users/me');
}

export function updateProfile(name: string, timezone: string): Promise<Profile> {
  return request('/api/users/me', {
    method: 'PUT',
    body: jsonBody({ name, timezone })
  });
}

export function lookupUser(email: string): Promise<Profile> {
  return request(`/api/users?email=${encodeURIComponent(email)}`);
}
