/** Mirrors the backend UserResponse DTO. */
export interface User {
  id: number;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  locale: string;
  createdAt: string;
}

/** Body returned by /api/auth/login, /register and /refresh. The refresh token travels in an HttpOnly cookie. */
export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresAt: string;
  user: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
  locale: string;
}
