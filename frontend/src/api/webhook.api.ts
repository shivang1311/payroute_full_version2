import apiClient from './client';
import type { ApiResponse, PagedResponse } from '../types';
import type {
  WebhookEndpoint,
  WebhookDelivery,
  WebhookEndpointRequest,
} from '../types/webhook';

export const webhookApi = {
  listEndpoints: () =>
    apiClient.get<ApiResponse<WebhookEndpoint[]>>('/webhooks/endpoints'),

  createEndpoint: (req: WebhookEndpointRequest) =>
    apiClient.post<ApiResponse<WebhookEndpoint>>('/webhooks/endpoints', req),

  updateEndpoint: (id: number, req: WebhookEndpointRequest) =>
    apiClient.put<ApiResponse<WebhookEndpoint>>(`/webhooks/endpoints/${id}`, req),

  deleteEndpoint: (id: number) =>
    apiClient.delete<ApiResponse<void>>(`/webhooks/endpoints/${id}`),

  rotateSecret: (id: number) =>
    apiClient.post<ApiResponse<WebhookEndpoint>>(`/webhooks/endpoints/${id}/rotate-secret`),

  testEndpoint: (id: number) =>
    apiClient.post<ApiResponse<{ enqueued: number }>>(`/webhooks/endpoints/${id}/test`),

  listDeliveries: (params: { endpointId?: number; page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<WebhookDelivery>>>('/webhooks/deliveries', { params }),

  retryDelivery: (id: number) =>
    apiClient.post<ApiResponse<WebhookDelivery>>(`/webhooks/deliveries/${id}/retry`),
};
