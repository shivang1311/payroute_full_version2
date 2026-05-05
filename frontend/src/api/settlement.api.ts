/**
 * Settlement batches and downloadable payment reports.
 * Backed by settlement-service.
 */
import apiClient from './client';
import type { ApiResponse, PagedResponse, SettlementBatch, PaymentReport } from '../types';

export const settlementApi = {
  getBatches: (params: { rail?: string; status?: string; page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<SettlementBatch>>>('/settlement/batches', { params }),

  getBatchById: (id: number) =>
    apiClient.get<ApiResponse<SettlementBatch>>(`/settlement/batches/${id}`),

  createBatch: (data: { rail: string; periodStart: string; periodEnd: string }) =>
    apiClient.post<ApiResponse<SettlementBatch>>('/settlement/batches', data),
};

export const reportApi = {
  getAll: (params: { scope?: string; page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<PaymentReport>>>('/reports', { params }),

  getById: (id: number) =>
    apiClient.get<ApiResponse<PaymentReport>>(`/reports/${id}`),

  generate: (data: { reportName: string; scope: string; parameters?: Record<string, unknown> }) =>
    apiClient.post<ApiResponse<PaymentReport>>('/reports/generate', data),
};
