import apiClient from './client';
import type { ApiResponse, AuthResponse, LoginRequest, RegisterRequest, User, PagedResponse } from '../types';

export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/login', data),

  register: (data: RegisterRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/register', data),

  refresh: (refreshToken: string) =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/refresh', { refreshToken }),

  logout: () =>
    apiClient.post<ApiResponse<void>>('/auth/logout'),

  changePassword: (currentPassword: string, newPassword: string) =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/change-password', {
      currentPassword,
      newPassword,
    }),
};

export const userApi = {
  getAll: (page = 0, size = 10) =>
    apiClient.get<ApiResponse<PagedResponse<User>>>('/users', { params: { page, size } }),

  getMe: () => apiClient.get<ApiResponse<User>>('/users/me'),

  updateMe: (data: { email?: string; phone?: string }) =>
    apiClient.put<ApiResponse<User>>('/users/me', data),

  getById: (id: number) =>
    apiClient.get<ApiResponse<User>>(`/users/${id}`),

  update: (id: number, data: Partial<User>) =>
    apiClient.put<ApiResponse<User>>(`/users/${id}`, data),

  updateRole: (id: number, role: string) =>
    apiClient.put<ApiResponse<User>>(`/users/${id}/role`, { role }),

  deactivate: (id: number) =>
    apiClient.delete<void>(`/users/${id}`),

  /** Set or change own transaction PIN.
   *  - First-time setup: just `pin`.
   *  - Subsequent change: include `currentPassword`. */
  setTransactionPin: (data: { pin: string; currentPassword?: string }) =>
    apiClient.post<ApiResponse<void>>('/users/me/pin', data),

  /** Verify own transaction PIN. Returns {valid:true|false}. */
  verifyTransactionPin: (pin: string) =>
    apiClient.post<ApiResponse<{ valid: boolean }>>('/users/me/pin/verify', { pin }),

  /** Admin-only: provision OPERATIONS / COMPLIANCE / RECONCILIATION user. */
  createStaff: (data: {
    username: string;
    email: string;
    phone: string;
    password: string;
    role: 'OPERATIONS' | 'COMPLIANCE' | 'RECONCILIATION';
  }) => apiClient.post<ApiResponse<User>>('/users/staff', data),
};

export const auditApi = {
  getAll: (page = 0, size = 20) =>
    apiClient.get<ApiResponse<PagedResponse<import('../types').AuditLog>>>('/audit', { params: { page, size } }),

  getByUser: (userId: number, page = 0, size = 20) =>
    apiClient.get<ApiResponse<PagedResponse<import('../types').AuditLog>>>(`/audit/user/${userId}`, { params: { page, size } }),
};
