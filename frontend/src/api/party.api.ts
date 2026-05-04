import apiClient from './client';
import type { ApiResponse, PagedResponse, Party, Account, AliasType, AccountValidationResponse } from '../types';

export const partyApi = {
  getAll: (params: { page?: number; size?: number; status?: string; type?: string } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<Party>>>('/parties', { params }),

  getById: (id: number) =>
    apiClient.get<ApiResponse<Party>>(`/parties/${id}`),

  create: (data: Partial<Party>) =>
    apiClient.post<ApiResponse<Party>>('/parties', data),

  update: (id: number, data: Partial<Party>) =>
    apiClient.put<ApiResponse<Party>>(`/parties/${id}`, data),

  delete: (id: number) =>
    apiClient.delete<void>(`/parties/${id}`),
};

export const accountApi = {
  getAll: (params: { partyId?: number; page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<Account>>>('/accounts', { params }),

  getById: (id: number) =>
    apiClient.get<ApiResponse<Account>>(`/accounts/${id}`),

  create: (data: Partial<Account>) =>
    apiClient.post<ApiResponse<Account>>('/accounts', data),

  update: (id: number, data: Partial<Account>) =>
    apiClient.put<ApiResponse<Account>>(`/accounts/${id}`, data),

  delete: (id: number) =>
    apiClient.delete<void>(`/accounts/${id}`),

  lookup: (alias: string) =>
    apiClient.get<ApiResponse<Account>>('/accounts/lookup', { params: { alias } }),

  resolve: (aliasType: AliasType, value: string) =>
    apiClient.get<ApiResponse<Account>>('/accounts/resolve', { params: { aliasType, value } }),

  validate: (accountNumber: string, ifscIban: string) =>
    apiClient.get<ApiResponse<AccountValidationResponse>>('/accounts/validate', {
      params: { accountNumber, ifscIban },
    }),
};
