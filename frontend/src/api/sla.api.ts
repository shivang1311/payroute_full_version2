import apiClient from './client';
import type { ApiResponse } from '../types';
import type { SlaConfig, SlaConfigRequest } from '../types/sla';

export const slaApi = {
  getAll: () => apiClient.get<ApiResponse<SlaConfig[]>>('/routing/sla-config'),

  getById: (id: number) =>
    apiClient.get<ApiResponse<SlaConfig>>(`/routing/sla-config/${id}`),

  create: (data: SlaConfigRequest) =>
    apiClient.post<ApiResponse<SlaConfig>>('/routing/sla-config', data),

  update: (id: number, data: SlaConfigRequest) =>
    apiClient.put<ApiResponse<SlaConfig>>(`/routing/sla-config/${id}`, data),

  delete: (id: number) =>
    apiClient.delete<void>(`/routing/sla-config/${id}`),

  getBreachCount: (from: string, to: string) =>
    apiClient.get<ApiResponse<{ count: number }>>('/routing/stats/sla-breaches', {
      params: { from, to },
    }),
};
