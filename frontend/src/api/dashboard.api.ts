/**
 * Aggregated dashboard stats — payment counts/totals, per-rail breakdowns,
 * and fee income. Backed by payment-service + ledger-service joined queries.
 */
import apiClient from './client';
import type { ApiResponse } from '../types';
import type { PaymentStats, RailStat, FeeIncomeStats } from '../types/dashboard';

export const dashboardApi = {
  getPaymentStats: (from: string, to: string) =>
    apiClient.get<ApiResponse<PaymentStats>>('/payments/stats', { params: { from, to } }),

  getRailStats: (from: string, to: string) =>
    apiClient.get<ApiResponse<RailStat[]>>('/routing/stats/by-rail', { params: { from, to } }),

  getFeeIncomeStats: (from: string, to: string) =>
    apiClient.get<ApiResponse<FeeIncomeStats>>('/ledger/stats/fee-income', { params: { from, to } }),
};
