/**
 * Scheduled (future-dated and recurring) payments. The scheduler in
 * payment-service materializes a real PaymentOrder when each next-run hits.
 */
import apiClient from './client';
import type { ApiResponse, PagedResponse } from '../types';
import type { ScheduledPayment, ScheduledPaymentRequest } from '../types/scheduled';

export const scheduledApi = {
  list: (params: { page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<ScheduledPayment>>>(
      '/payments/scheduled',
      { params },
    ),

  getOne: (id: number) =>
    apiClient.get<ApiResponse<ScheduledPayment>>(`/payments/scheduled/${id}`),

  create: (req: ScheduledPaymentRequest) =>
    apiClient.post<ApiResponse<ScheduledPayment>>('/payments/scheduled', req),

  update: (id: number, req: ScheduledPaymentRequest) =>
    apiClient.put<ApiResponse<ScheduledPayment>>(`/payments/scheduled/${id}`, req),

  pause: (id: number) =>
    apiClient.post<ApiResponse<ScheduledPayment>>(`/payments/scheduled/${id}/pause`),

  resume: (id: number) =>
    apiClient.post<ApiResponse<ScheduledPayment>>(`/payments/scheduled/${id}/resume`),

  cancel: (id: number) =>
    apiClient.delete<ApiResponse<ScheduledPayment>>(`/payments/scheduled/${id}`),
};
