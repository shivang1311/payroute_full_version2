import apiClient from './client';
import type { ApiResponse, PagedResponse, ExceptionCase, ReturnItem, ReconciliationRecord } from '../types';

export interface ExceptionStats {
  total: number;
  open: number;
  inProgress: number;
  escalated: number;
  resolved: number;
  closed: number;
  slaBreached: number;
}

export const exceptionApi = {
  getStats: () =>
    apiClient.get<ApiResponse<ExceptionStats>>('/exceptions/stats'),

  getAll: (params: { status?: string; category?: string; ownerId?: number; page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<ExceptionCase>>>('/exceptions', { params }),

  assign: (id: number, ownerId: number | null) =>
    apiClient.put<ApiResponse<ExceptionCase>>(`/exceptions/${id}/assign`,
      null, { params: ownerId == null ? {} : { ownerId } }),

  getById: (id: number) =>
    apiClient.get<ApiResponse<ExceptionCase>>(`/exceptions/${id}`),

  create: (data: {
    paymentId: number;
    category: string;
    priority: string;
    description?: string;
    ownerId?: number;
  }) => apiClient.post<ApiResponse<ExceptionCase>>('/exceptions', data),

  update: (id: number, data: Partial<ExceptionCase>) =>
    apiClient.put<ApiResponse<ExceptionCase>>(`/exceptions/${id}`, data),
};

export const returnApi = {
  getAll: (params: { status?: string; page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<ReturnItem>>>('/returns', { params }),

  getById: (id: number) =>
    apiClient.get<ApiResponse<ReturnItem>>(`/returns/${id}`),

  process: (id: number) =>
    apiClient.post<ApiResponse<ReturnItem>>(`/returns/${id}/process`),
};

export const reconciliationApi = {
  getAll: (params: { result?: string; reconDate?: string; page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<ReconciliationRecord>>>('/reconciliation', { params }),

  runReconciliation: (date: string) =>
    apiClient.post<ApiResponse<void>>('/reconciliation/run', null, { params: { date } }),
};
