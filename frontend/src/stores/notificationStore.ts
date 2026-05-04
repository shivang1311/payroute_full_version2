import { create } from 'zustand';
import { notificationApi } from '../api/notification.api';

interface NotificationState {
  unreadCount: number;
  fetchUnreadCount: () => Promise<void>;
}

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
