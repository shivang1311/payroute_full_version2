/**
 * Tracks the unread-notification badge count shown in the header.
 * Polled periodically by the layout shell; failures are swallowed (the
 * badge is best-effort UX, never blocks the user).
 */
import { create } from 'zustand';
import { notificationApi } from '../api/notification.api';

/** State + actions for the unread notification badge. */
export interface NotificationState {
  /** Current unread count for the signed-in user. */
  unreadCount: number;
  /** Refresh the count from the API. Silently no-ops on failure. */
  fetchUnreadCount: () => Promise<void>;
}

/** Hook for reading the unread count and triggering refreshes. */
export const useNotificationStore = create<NotificationState>((set) => ({
  unreadCount: 0,

  fetchUnreadCount: async () => {
    try {
      const response = await notificationApi.getUnreadCount();
      set({ unreadCount: response.data.data.unreadCount });
    } catch {
      // Silently fail - notifications are best-effort
    }
  },
}));
