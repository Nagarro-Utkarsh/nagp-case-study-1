import { ApiError } from '@habitloop/api-client';

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.';
}

export function fieldErrors(error: unknown): Record<string, string> {
  return error instanceof ApiError ? error.errors : {};
}
