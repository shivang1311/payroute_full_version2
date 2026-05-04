import apiClient from './client';
import type { ApiResponse, PagedResponse, ComplianceCheck, Hold } from '../types';

export const complianceApi = {
  getChecks: (params: { paymentId?: number; result?: string; page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<ComplianceCheck>>>('/compliance/checks', { params }),

  getHolds: (params: { status?: string; page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<Hold>>>('/compliance/holds', { params }),

  releaseHold: (id: number, releaseNotes: string) =>
    apiClient.put<ApiResponse<Hold>>(`/compliance/holds/${id}/release`, { releaseNotes }),

  rejectHold: (id: number, releaseNotes: string) =>
    apiClient.put<ApiResponse<Hold>>(`/compliance/holds/${id}/reject`, { releaseNotes }),
};
