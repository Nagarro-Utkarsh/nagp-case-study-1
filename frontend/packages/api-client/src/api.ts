import type { ProblemDetail } from './types';

type RequestOptions = RequestInit & { authenticated?: boolean };

export class ApiError extends Error {
  readonly status: number;
  readonly errors: Record<string, string>;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail || problem.title || `Request failed (${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.errors = problem.errors ?? {};
  }
}

let tokenReader: () => string | null = () => null;
let unauthorizedHandler: (() => void) | null = null;

export function configureApi(options: {
  getToken: () => string | null;
  onUnauthorized: () => void;
}): void {
  tokenReader = options.getToken;
  unauthorizedHandler = options.onUnauthorized;
}

export function apiUrl(path: string): string {
  const base = (import.meta.env.VITE_API_BASE ?? '').replace(/\/+$/, '');
  return `${base}${path.startsWith('/') ? path : `/${path}`}`;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { authenticated = true, headers: suppliedHeaders, ...init } = options;
  const headers = new Headers(suppliedHeaders);
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (authenticated) {
    const token = tokenReader();
    if (token) headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(apiUrl(path), { ...init, headers });
  if (!response.ok) {
    let problem: ProblemDetail = {};
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch (error) {
      if (!(error instanceof SyntaxError)) throw error;
    }
    if (response.status === 401 && authenticated) unauthorizedHandler?.();
    throw new ApiError(response.status, problem);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function jsonBody(value: unknown): string {
  return JSON.stringify(value);
}
