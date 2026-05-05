/**
 * Tests the auth store's state machine: login → updates user/token + persists,
 * logout → clears, hasRole → checks the active user. The api module is mocked
 * so tests don't hit the network.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { User } from '../../types';

const sampleUser: User = {
  id: 7,
  username: 'newadmin',
  email: 'newadmin@payroute.com',
  role: 'ADMIN',
  active: true,
  createdAt: '2026-01-01T00:00:00',
};

// vi.mock factories are hoisted; declare shared mocks via vi.hoisted() so they exist
// when the factory runs.
const {
  loginMock, registerMock, logoutMock, changePasswordMock, updateMeMock, setTransactionPinMock,
} = vi.hoisted(() => ({
  loginMock: vi.fn(),
  registerMock: vi.fn(),
  logoutMock: vi.fn(),
  changePasswordMock: vi.fn(),
  updateMeMock: vi.fn(),
  setTransactionPinMock: vi.fn(),
}));

vi.mock('../../api/auth.api', () => ({
  authApi: {
    login: loginMock,
    register: registerMock,
    logout: logoutMock,
    changePassword: changePasswordMock,
  },
  userApi: {
    updateMe: updateMeMock,
    setTransactionPin: setTransactionPinMock,
  },
}));

// Suppress antd toast notifications.
vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd');
  return {
    ...actual,
    message: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
    },
  };
});

// Import AFTER the mocks above are registered.
import { useAuthStore } from '../authStore';

const resetStore = () => {
  useAuthStore.setState({
    user: null,
    token: null,
    isAuthenticated: false,
    isLoading: false,
  });
};

describe('useAuthStore', () => {
  beforeEach(() => {
    resetStore();
    localStorage.clear();
    loginMock.mockReset();
    registerMock.mockReset();
    logoutMock.mockReset();
    changePasswordMock.mockReset();
    updateMeMock.mockReset();
    setTransactionPinMock.mockReset();
  });

  describe('initial state', () => {
    it('starts unauthenticated when no token in storage', () => {
      const s = useAuthStore.getState();
      expect(s.user).toBeNull();
      expect(s.token).toBeNull();
      expect(s.isAuthenticated).toBe(false);
    });
  });

  describe('login', () => {
    it('on success: stores user/token + flips isAuthenticated', async () => {
      loginMock.mockResolvedValueOnce({
        data: {
          data: {
            accessToken: 'tok-123',
            refreshToken: 'rtok-123',
            user: sampleUser,
          },
        },
      });

      await useAuthStore.getState().login('newadmin', 'pwd');

      const s = useAuthStore.getState();
      expect(s.user).toEqual(sampleUser);
      expect(s.token).toBe('tok-123');
      expect(s.isAuthenticated).toBe(true);
      expect(s.isLoading).toBe(false);
      // Persisted to localStorage
      expect(localStorage.getItem('accessToken')).toBe('tok-123');
      expect(localStorage.getItem('refreshToken')).toBe('rtok-123');
      expect(JSON.parse(localStorage.getItem('user')!)).toEqual(sampleUser);
    });

    it('on failure: re-throws + leaves auth state empty + clears loading', async () => {
      loginMock.mockRejectedValueOnce({ response: { data: { message: 'bad creds' } } });

      await expect(
        useAuthStore.getState().login('newadmin', 'wrong'),
      ).rejects.toBeDefined();

      const s = useAuthStore.getState();
      expect(s.user).toBeNull();
      expect(s.token).toBeNull();
      expect(s.isAuthenticated).toBe(false);
      expect(s.isLoading).toBe(false);
      expect(localStorage.getItem('accessToken')).toBeNull();
    });
  });

  describe('logout', () => {
    it('clears state + storage even if api call fails', async () => {
      // Pre-load auth
      useAuthStore.setState({
        user: sampleUser,
        token: 'tok',
        isAuthenticated: true,
        isLoading: false,
      });
      localStorage.setItem('accessToken', 'tok');
      localStorage.setItem('refreshToken', 'rtok');
      localStorage.setItem('user', JSON.stringify(sampleUser));

      logoutMock.mockRejectedValueOnce(new Error('network'));

      await useAuthStore.getState().logout();

      const s = useAuthStore.getState();
      expect(s.user).toBeNull();
      expect(s.token).toBeNull();
      expect(s.isAuthenticated).toBe(false);
      expect(localStorage.getItem('accessToken')).toBeNull();
      expect(localStorage.getItem('refreshToken')).toBeNull();
      expect(localStorage.getItem('user')).toBeNull();
    });
  });

  describe('register', () => {
    it('on success: same shape as login', async () => {
      registerMock.mockResolvedValueOnce({
        data: {
          data: {
            accessToken: 'tok-r',
            refreshToken: 'rtok-r',
            user: { ...sampleUser, role: 'CUSTOMER', id: 99 },
          },
        },
      });

      await useAuthStore
        .getState()
        .register('Test', 'tester', 'tester@gmail.com', 'pwd', '+919876543210');

      const s = useAuthStore.getState();
      expect(s.user?.username).toBe(sampleUser.username);
      expect(s.user?.role).toBe('CUSTOMER');
      expect(s.token).toBe('tok-r');
      expect(s.isAuthenticated).toBe(true);
    });
  });

  describe('updateProfile', () => {
    it('replaces the user with the API response and persists it', async () => {
      useAuthStore.setState({
        user: sampleUser, token: 'tok', isAuthenticated: true, isLoading: false,
      });

      const updated = { ...sampleUser, email: 'new@gmail.com' };
      updateMeMock.mockResolvedValueOnce({ data: { data: updated } });

      await useAuthStore.getState().updateProfile({ email: 'new@gmail.com' });

      expect(useAuthStore.getState().user?.email).toBe('new@gmail.com');
      expect(JSON.parse(localStorage.getItem('user')!).email).toBe('new@gmail.com');
    });
  });

  describe('setTransactionPin', () => {
    it('marks pinSet=true on the existing user', async () => {
      useAuthStore.setState({
        user: { ...sampleUser, pinSet: false },
        token: 'tok',
        isAuthenticated: true,
        isLoading: false,
      });
      setTransactionPinMock.mockResolvedValueOnce({});

      await useAuthStore.getState().setTransactionPin('1234');

      expect(useAuthStore.getState().user?.pinSet).toBe(true);
      expect(JSON.parse(localStorage.getItem('user')!).pinSet).toBe(true);
    });
  });

  describe('hasRole', () => {
    it('returns false when no user', () => {
      expect(useAuthStore.getState().hasRole('ADMIN')).toBe(false);
    });

    it('returns true when user role matches one of the args', () => {
      useAuthStore.setState({
        user: sampleUser, token: 'tok', isAuthenticated: true, isLoading: false,
      });
      expect(useAuthStore.getState().hasRole('ADMIN')).toBe(true);
      expect(useAuthStore.getState().hasRole('OPERATIONS', 'ADMIN')).toBe(true);
    });

    it('returns false when user role is not in args', () => {
      useAuthStore.setState({
        user: { ...sampleUser, role: 'CUSTOMER' },
        token: 'tok',
        isAuthenticated: true,
        isLoading: false,
      });
      expect(useAuthStore.getState().hasRole('ADMIN', 'OPERATIONS')).toBe(false);
    });
  });

  describe('loadFromStorage', () => {
    it('hydrates auth state from valid stored user/token', () => {
      localStorage.setItem('accessToken', 'tok-stored');
      localStorage.setItem('user', JSON.stringify(sampleUser));

      useAuthStore.getState().loadFromStorage();

      const s = useAuthStore.getState();
      expect(s.user).toEqual(sampleUser);
      expect(s.token).toBe('tok-stored');
      expect(s.isAuthenticated).toBe(true);
    });

    it('clears storage when stored user is corrupt JSON', () => {
      localStorage.setItem('accessToken', 'tok');
      localStorage.setItem('user', '{not-json');

      useAuthStore.getState().loadFromStorage();

      expect(localStorage.getItem('accessToken')).toBeNull();
      expect(localStorage.getItem('user')).toBeNull();
      expect(useAuthStore.getState().isAuthenticated).toBe(false);
    });

    it('does nothing when token is missing', () => {
      // Only user, no token
      localStorage.setItem('user', JSON.stringify(sampleUser));

      useAuthStore.getState().loadFromStorage();

      expect(useAuthStore.getState().isAuthenticated).toBe(false);
    });
  });
});
