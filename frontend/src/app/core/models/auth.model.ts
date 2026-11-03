export interface AuthUser {
  id: number;
  name: string;
  email: string;
  role: string;
  provider: string;
}

export interface AuthResponse {
  token: string;
  id: number;
  name: string;
  email: string;
  role: string;
  provider: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface GoogleLoginRequest {
  credential: string;
}
