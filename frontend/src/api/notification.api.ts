/**
 * In-app notifications — list, mark read, unread badge count, and broadcast
 * helpers used by other services for fan-out. Backed by notification-service.
 */
import apiClient from './client';
import type { ApiResponse, PagedResponse, Notification } from '../types';

export const notificationApi = {
  getAll: (params: { page?: number; size?: number } = {}) =>
    apiClient.get<ApiResponse<PagedResponse<Notification>>>('/notifications', { params }),

  getUnreadCount: () =>
    apiClient.get<ApiResponse<{ unreadCount: number }>>('/notifications/unread-count'),

  markAsRead: (id: number) =>
    apiClient.put<ApiResponse<Notification>>(`/notifications/${id}/read`),

  markAllAsRead: () =>
    apiClient.put<ApiResponse<void>>('/notifications/read-all'),
};
