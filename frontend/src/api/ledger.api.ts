import apiClient from './client';
import type { ApiResponse, PagedResponse, LedgerEntry, FeeSchedule, AccountSummary } from '../types';

export const ledgerApi = {
  getEntries: (params: { paymentId?: number; accountId?: number; page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<LedgerEntry>>>('/ledger/entries', { params }),

  getEntryById: (id: number) =>
    apiClient.get<ApiResponse<LedgerEntry>>(`/ledger/entries/${id}`),

  getAccountSummary: (accountId: number) =>
    apiClient.get<ApiResponse<AccountSummary>>('/ledger/summary', { params: { accountId } }),

  exportCsv: (from: string, to: string) =>
    apiClient.get<Blob>('/ledger/export.csv', {
      params: { from, to },
      responseType: 'blob',
    }),
};

export const feeScheduleApi = {
  getAll: (params: { page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<FeeSchedule>>>('/ledger/fees', { params }),

  getById: (id: number) =>
    apiClient.get<ApiResponse<FeeSchedule>>(`/ledger/fees/${id}`),

  create: (data: Partial<FeeSchedule>) =>
    apiClient.post<ApiResponse<FeeSchedule>>('/ledger/fees', data),

  update: (id: number, data: Partial<FeeSchedule>) =>
    apiClient.put<ApiResponse<FeeSchedule>>(`/ledger/fees/${id}`, data),
};
