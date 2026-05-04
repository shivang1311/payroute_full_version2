import apiClient from './client';
import type { ApiResponse, PagedResponse, RoutingRule, RailInstruction } from '../types';

export const routingRuleApi = {
  getAll: (params: { page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<RoutingRule>>>('/routing/rules', { params }),

  getById: (id: number) =>
    apiClient.get<ApiResponse<RoutingRule>>(`/routing/rules/${id}`),

  create: (data: Partial<RoutingRule>) =>
    apiClient.post<ApiResponse<RoutingRule>>('/routing/rules', data),

  update: (id: number, data: Partial<RoutingRule>) =>
    apiClient.put<ApiResponse<RoutingRule>>(`/routing/rules/${id}`, data),

  delete: (id: number) =>
    apiClient.delete<void>(`/routing/rules/${id}`),
};

export const railInstructionApi = {
  getAll: (params: { paymentId?: number; rail?: string; status?: string; page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<RailInstruction>>>('/routing/instructions', { params }),

  getById: (id: number) =>
    apiClient.get<ApiResponse<RailInstruction>>(`/routing/instructions/${id}`),
};
