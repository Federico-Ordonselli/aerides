import { AuthResponse } from './auth.models';

/** Test helper: a backend auth response with the given access token. */
export function authResponse(accessToken = 'access-1'): AuthResponse {
  return {
    accessToken,
    tokenType: 'Bearer',
    expiresAt: '2030-01-01T00:00:00Z',
    user: {
      id: 1,
      email: 'mario@example.com',
      displayName: 'Mario',
      avatarUrl: null,
      locale: 'it',
      createdAt: '2026-01-01T00:00:00Z',
    },
  };
}
