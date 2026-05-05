/**
 * Global auth store (Zustand). Single source of truth for the current user,
 * access token, and login/logout/profile mutations. Hydrates synchronously
 * from `localStorage` on module load so the app boots already-signed-in if
 * credentials exist. Wraps every mutation in toast feedback via antd's
 * `message` so callers don't need to handle UI on success/failure.
 */
import { create } from 'zustand';
import type { User, Role } from '../types';
import { authApi, userApi } from '../api/auth.api';
import { message } from 'antd';

/**
 * State + actions exposed by {@link useAuthStore}.
 */
export interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;

  login: (username: string, password: string) => Promise<void>;
  register: (name: string, username: string, email: string, password: string, phone: string, role?: Role) => Promise<void>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  setTransactionPin: (pin: string, currentPassword?: string) => Promise<void>;
  updateProfile: (data: { email?: string; phone?: string }) => Promise<void>;
  logout: () => Promise<void>;
  loadFromStorage: () => void;
  hasRole: (...roles: Role[]) => boolean;
}

// Hydrate from localStorage synchronously on store creation
const storedToken = localStorage.getItem('accessToken');
const storedUserStr = localStorage.getItem('user');
let initialUser: User | null = null;
let initialToken: string | null = null;
let initialAuth = false;
if (storedToken && storedUserStr) {
  try {
    initialUser = JSON.parse(storedUserStr) as User;
    initialToken = storedToken;
    initialAuth = true;
  } catch {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
  }
}

/**
 * The auth store hook. Use selectors to read only what you need:
 * `const user = useAuthStore(s => s.user)`. Calling without a selector
 * subscribes to the whole store and re-renders on any change.
 */
export const useAuthStore = create<AuthState>((set, get) => ({
  user: initialUser,
  token: initialToken,
  isAuthenticated: initialAuth,
  isLoading: false,

  login: async (username: string, password: string) => {
    set({ isLoading: true });
    try {
      const response = await authApi.login({ username, password });
      const { accessToken, refreshToken, user } = response.data.data;

      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);
      localStorage.setItem('user', JSON.stringify(user));

      set({ user, token: accessToken, isAuthenticated: true, isLoading: false });
      message.success(`Welcome back, ${user.username}!`);
    } catch (error: unknown) {
      set({ isLoading: false });
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Login failed');
      throw error;
    }
  },

  register: async (name, username, email, password, phone, role?) => {
    set({ isLoading: true });
    try {
      const response = await authApi.register({ name, username, email, password, phone, role });
      const { accessToken, refreshToken, user } = response.data.data;

      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);
      localStorage.setItem('user', JSON.stringify(user));

      set({ user, token: accessToken, isAuthenticated: true, isLoading: false });
      message.success('Registration successful!');
    } catch (error: unknown) {
      set({ isLoading: false });
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Registration failed');
      throw error;
    }
  },

  setTransactionPin: async (pin: string, currentPassword?: string) => {
    set({ isLoading: true });
    try {
      await userApi.setTransactionPin({ pin, currentPassword });
      const current = get().user;
      if (current) {
        const updated = { ...current, pinSet: true } as User;
        localStorage.setItem('user', JSON.stringify(updated));
        set({ user: updated, isLoading: false });
      } else {
        set({ isLoading: false });
      }
      message.success('Transaction PIN saved');
    } catch (error: unknown) {
      set({ isLoading: false });
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to save transaction PIN');
      throw error;
    }
  },

  updateProfile: async (data: { email?: string; phone?: string }) => {
    set({ isLoading: true });
    try {
      const response = await userApi.updateMe(data);
      const user = response.data.data;
      localStorage.setItem('user', JSON.stringify(user));
      set({ user, isLoading: false });
      message.success('Profile updated');
    } catch (error: unknown) {
      set({ isLoading: false });
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to update profile');
      throw error;
    }
  },

  changePassword: async (currentPassword: string, newPassword: string) => {
    set({ isLoading: true });
    try {
      const response = await authApi.changePassword(currentPassword, newPassword);
      const { accessToken, refreshToken, user } = response.data.data;
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);
      localStorage.setItem('user', JSON.stringify(user));
      set({ user, token: accessToken, isAuthenticated: true, isLoading: false });
      message.success('Password changed successfully');
    } catch (error: unknown) {
      set({ isLoading: false });
      const err = error as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to change password');
      throw error;
    }
  },

  logout: async () => {
    try {
      await authApi.logout();
    } catch {
      // Logout even if API call fails
    }
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    set({ user: null, token: null, isAuthenticated: false });
  },

  loadFromStorage: () => {
    const token = localStorage.getItem('accessToken');
    const userStr = localStorage.getItem('user');
    if (token && userStr) {
      try {
        const user = JSON.parse(userStr) as User;
        set({ user, token, isAuthenticated: true });
      } catch {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
      }
    }
  },

  hasRole: (...roles: Role[]) => {
    const user = get().user;
    return user ? roles.includes(user.role) : false;
  },
}));
