import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, configureApi, request } from './api';

afterEach(() => {
  vi.unstubAllGlobals();
  configureApi({ getToken: () => null, onUnauthorized: () => undefined });
});

describe('request', () => {
  it('sends bearer tokens and parses backend problem field errors', async () => {
    configureApi({ getToken: () => 'secret-token', onUnauthorized: () => undefined });
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ detail: 'Request validation failed', errors: { name: 'must not be blank' } }),
      { status: 400, headers: { 'Content-Type': 'application/problem+json' } }
    ));
    vi.stubGlobal('fetch', fetchMock);

    await expect(request('/api/habits', { method: 'POST', body: '{}' })).rejects.toMatchObject({
      status: 400,
      errors: { name: 'must not be blank' }
    });
    expect(fetchMock.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer secret-token');
  });

  it('only expires authenticated sessions on a 401', async () => {
    const onUnauthorized = vi.fn();
    configureApi({ getToken: () => null, onUnauthorized });
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ detail: 'Invalid email or password' }), { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ detail: 'Unauthorized' }), { status: 401 })));

    await expect(request('/api/auth/login', { authenticated: false })).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).not.toHaveBeenCalled();
    await expect(request('/api/protected')).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });
});
