import { browser } from '$app/environment';
import { goto } from '$app/navigation';
import { configureApi, getProfile, login as loginRequest, signup as signupRequest } from '@habitloop/api-client';
import type { Profile } from '@habitloop/api-client';

const TOKEN_KEY = 'habitloop.token';

interface AuthState {
  token: string | null;
  user: Profile | null;
  ready: boolean;
  error: string | null;
}

const state = $state<AuthState>({ token: null, user: null, ready: false, error: null });
let expiryTimer: ReturnType<typeof setTimeout> | undefined;

function clearSession(redirect: boolean): void {
  if (expiryTimer) clearTimeout(expiryTimer);
  expiryTimer = undefined;
  state.token = null;
  state.user = null;
  state.error = null;
  if (browser) localStorage.removeItem(TOKEN_KEY);
  if (redirect && browser && state.ready) void goto('/login');
}

function scheduleExpiry(token: string): void {
  if (expiryTimer) clearTimeout(expiryTimer);
  try {
    const segment = token.split('.')[1];
    if (!segment) throw new Error('Malformed token');
    const payload = JSON.parse(atob(segment.replace(/-/g, '+').replace(/_/g, '/'))) as { exp?: number };
    const expiresAt = Number(payload.exp) * 1000;
    if (!Number.isFinite(expiresAt)) throw new Error('Token has no expiry');
    const remaining = expiresAt - Date.now();
    if (remaining <= 0) {
      clearSession(true);
      return;
    }
    expiryTimer = setTimeout(() => clearSession(true), Math.min(remaining, 2_147_000_000));
  } catch {
    clearSession(false);
    state.error = 'Your sign-in could not be restored. Please sign in again.';
  }
}

function persistToken(token: string): void {
  state.token = token;
  state.error = null;
  if (browser) localStorage.setItem(TOKEN_KEY, token);
  scheduleExpiry(token);
}

configureApi({
  getToken: () => state.token,
  onUnauthorized: () => clearSession(true)
});

export const auth = {
  get token() { return state.token; },
  get user() { return state.user; },
  get ready() { return state.ready; },
  get error() { return state.error; },
  get isAuthed() { return Boolean(state.token && state.user); },
  async hydrate(): Promise<void> {
    if (!browser || state.ready) return;
    const stored = localStorage.getItem(TOKEN_KEY);
    if (!stored) {
      state.ready = true;
      return;
    }
    state.token = stored;
    scheduleExpiry(stored);
    if (!state.token) {
      state.ready = true;
      return;
    }
    try {
      state.user = await getProfile();
    } catch (error) {
      clearSession(false);
      state.error = error instanceof Error ? error.message : 'Unable to restore your session.';
    } finally {
      state.ready = true;
    }
  },
  async login(email: string, password: string, tenantSlug: string): Promise<void> {
    state.error = null;
    persistToken(await loginRequest({ email, password, tenantSlug }));
    try {
      state.user = await getProfile();
    } catch (error) {
      clearSession(false);
      throw error;
    }
  },
  async signup(name: string, email: string, password: string, tenantSlug: string): Promise<void> {
    state.error = null;
    persistToken(await signupRequest({ name, email, password, tenantSlug }));
    try {
      state.user = await getProfile();
    } catch (error) {
      clearSession(false);
      throw error;
    }
  },
  async refreshUser(): Promise<void> {
    state.user = await getProfile();
  },
  logout(redirect = true): void {
    clearSession(redirect);
  },
  storageChanged(token: string | null): void {
    if (token === state.token) return;
    if (!token) {
      clearSession(false);
      state.ready = true;
      return;
    }
    state.token = token;
    state.user = null;
    scheduleExpiry(token);
    void getProfile().then((profile) => {
      if (state.token === token) state.user = profile;
    }).catch(() => clearSession(false));
  },
  setError(message: string | null): void {
    state.error = message;
  }
};
