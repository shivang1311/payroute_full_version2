/**
 * Payment endpoints — initiate, fetch, search, cancel, status updates.
 * Backed by payment-service.
 */
import apiClient from './client';
import type { ApiResponse, PagedResponse, PaymentOrder, PaymentInitiationRequest } from '../types';

export const paymentApi = {
  create: (data: PaymentInitiationRequest) =>
    apiClient.post<ApiResponse<PaymentOrder>>('/payments', data),

  getAll: (params: { page?: number; size?: number; status?: string; channel?: string } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<PaymentOrder>>>('/payments', { params }),

  getById: (id: number) =>
    apiClient.get<ApiResponse<PaymentOrder>>(`/payments/${id}`),

  retry: (id: number) =>
    apiClient.post<ApiResponse<PaymentOrder>>(`/payments/${id}/retry`),

  cancel: (id: number, reason: string) =>
    apiClient.post<ApiResponse<PaymentOrder>>(`/payments/${id}/cancel`, { reason }),

  hold: (id: number, reason: string) =>
    apiClient.post<ApiResponse<PaymentOrder>>(`/payments/${id}/hold`, { reason }),
};
