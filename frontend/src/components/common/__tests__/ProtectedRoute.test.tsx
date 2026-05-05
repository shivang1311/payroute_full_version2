/**
 * ProtectedRoute is the only true security boundary on the frontend — every
 * authenticated route renders through it. The 4 branches (auth, password
 * rotation, PIN setup, role check) are pinned here so any regression that
 * lets a user past one of those gates fails the build immediately.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import type { Role, User } from '../../../types';

// Mock the auth store; each test sets the active user shape.
const mockState = {
  current: { isAuthenticated: false, user: null as User | null },
};
vi.mock('../../../stores/authStore', () => ({
  useAuthStore: () => mockState.current,
}));

import ProtectedRoute from '../ProtectedRoute';

const makeUser = (overrides: Partial<User> = {}): User => ({
  id: 1,
  username: 'tester',
  email: 'tester@payroute.com',
  role: 'OPERATIONS',
  active: true,
  mustChangePassword: false,
  pinSet: true,
  createdAt: '2026-01-01T00:00:00',
  ...overrides,
});

/**
 * Helper: render <ProtectedRoute> at `path` with sentinel routes for each
 * possible redirect target. The visible heading tells us where we ended up.
 */
const renderAt = (
  path: string,
  options: { allowedRoles?: Role[] } = {},
) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path={path}
          element={
            <ProtectedRoute allowedRoles={options.allowedRoles}>
              <h1>protected-content</h1>
            </ProtectedRoute>
          }
        />
        <Route path="/login" element={<h1>login-page</h1>} />
        <Route path="/change-password" element={<h1>change-password-page</h1>} />
        <Route path="/setup-pin" element={<h1>setup-pin-page</h1>} />
        <Route path="/dashboard" element={<h1>dashboard-page</h1>} />
      </Routes>
    </MemoryRouter>,
  );

describe('ProtectedRoute', () => {
  beforeEach(() => {
    mockState.current = { isAuthenticated: false, user: null };
  });

  describe('unauthenticated', () => {
    it('redirects to /login when not authenticated', () => {
      mockState.current = { isAuthenticated: false, user: null };
      renderAt('/payments');
      expect(screen.getByRole('heading', { name: 'login-page' })).toBeInTheDocument();
      expect(screen.queryByText('protected-content')).not.toBeInTheDocument();
    });

    it('redirects to /login even when a user object somehow exists but isAuthenticated=false', () => {
      mockState.current = { isAuthenticated: false, user: makeUser() };
      renderAt('/payments');
      expect(screen.getByRole('heading', { name: 'login-page' })).toBeInTheDocument();
    });
  });

  describe('first-login password rotation', () => {
    it('forces /change-password when mustChangePassword=true', () => {
      mockState.current = {
        isAuthenticated: true,
        user: makeUser({ mustChangePassword: true }),
      };
      renderAt('/payments');
      expect(screen.getByRole('heading', { name: 'change-password-page' })).toBeInTheDocument();
    });

    it('lets the user reach /change-password itself (no redirect loop)', () => {
      mockState.current = {
        isAuthenticated: true,
        user: makeUser({ mustChangePassword: true }),
      };
      renderAt('/change-password');
      // On the target page the children should render instead of redirecting.
      expect(screen.getByText('protected-content')).toBeInTheDocument();
    });
  });

  describe('customer transaction PIN setup', () => {
    it('forces CUSTOMER without pinSet onto /setup-pin', () => {
      mockState.current = {
        isAuthenticated: true,
        user: makeUser({ role: 'CUSTOMER', pinSet: false }),
      };
      renderAt('/payments');
      expect(screen.getByRole('heading', { name: 'setup-pin-page' })).toBeInTheDocument();
    });

    it('lets the customer reach /setup-pin itself (no redirect loop)', () => {
      mockState.current = {
        isAuthenticated: true,
        user: makeUser({ role: 'CUSTOMER', pinSet: false }),
      };
      renderAt('/setup-pin');
      expect(screen.getByText('protected-content')).toBeInTheDocument();
    });

    it('does NOT force PIN setup when the customer already has one', () => {
      mockState.current = {
        isAuthenticated: true,
        user: makeUser({ role: 'CUSTOMER', pinSet: true }),
      };
      renderAt('/payments');
      expect(screen.getByText('protected-content')).toBeInTheDocument();
    });

    it('does NOT force PIN setup on staff roles even if pinSet=false (PIN is customer-only)', () => {
      mockState.current = {
        isAuthenticated: true,
        user: makeUser({ role: 'OPERATIONS', pinSet: false }),
      };
      renderAt('/payments');
      expect(screen.getByText('protected-content')).toBeInTheDocument();
    });
  });

  describe('role gate', () => {
    it('redirects to /dashboard when user role is not in allowedRoles', () => {
      mockState.current = {
        isAuthenticated: true,
        user: makeUser({ role: 'CUSTOMER' }),
      };
      renderAt('/admin/users', { allowedRoles: ['ADMIN'] });
      expect(screen.getByRole('heading', { name: 'dashboard-page' })).toBeInTheDocument();
      expect(screen.queryByText('protected-content')).not.toBeInTheDocument();
    });

    it('renders children when user role IS in allowedRoles', () => {
      mockState.current = {
        isAuthenticated: true,
        user: makeUser({ role: 'ADMIN' }),
      };
      renderAt('/admin/users', { allowedRoles: ['ADMIN'] });
      expect(screen.getByText('protected-content')).toBeInTheDocument();
    });

    it('renders children when user role is one of multiple allowedRoles', () => {
      mockState.current = {
        isAuthenticated: true,
        user: makeUser({ role: 'OPERATIONS' }),
      };
      renderAt('/exceptions', { allowedRoles: ['OPERATIONS', 'ADMIN'] });
      expect(screen.getByText('protected-content')).toBeInTheDocument();
    });

    it('renders children when no allowedRoles given (any authenticated user OK)', () => {
      mockState.current = {
        isAuthenticated: true,
        user: makeUser({ role: 'CUSTOMER' }),
      };
      renderAt('/payments');
      expect(screen.getByText('protected-content')).toBeInTheDocument();
    });
  });

  describe('happy path', () => {
    it('renders children when authenticated, password OK, pin OK, role OK', () => {
      mockState.current = {
        isAuthenticated: true,
        user: makeUser({ role: 'ADMIN', mustChangePassword: false, pinSet: true }),
      };
      renderAt('/dashboard');
      expect(screen.getByText('protected-content')).toBeInTheDocument();
    });
  });

  describe('gate ordering', () => {
    it('mustChangePassword takes priority over role gate', () => {
      // CUSTOMER trying to load an ADMIN-only page WHILE also mid-rotation.
      // Password rotation must win — otherwise we'd expose role-gate behavior
      // before the user has even rotated.
      mockState.current = {
        isAuthenticated: true,
        user: makeUser({ role: 'CUSTOMER', mustChangePassword: true }),
      };
      renderAt('/admin/users', { allowedRoles: ['ADMIN'] });
      expect(screen.getByRole('heading', { name: 'change-password-page' })).toBeInTheDocument();
    });

    it('PIN setup takes priority over role gate', () => {
      mockState.current = {
        isAuthenticated: true,
        user: makeUser({ role: 'CUSTOMER', pinSet: false }),
      };
      renderAt('/admin/users', { allowedRoles: ['ADMIN'] });
      expect(screen.getByRole('heading', { name: 'setup-pin-page' })).toBeInTheDocument();
    });

    it('auth gate takes priority over everything', () => {
      mockState.current = { isAuthenticated: false, user: null };
      renderAt('/admin/users', { allowedRoles: ['ADMIN'] });
      expect(screen.getByRole('heading', { name: 'login-page' })).toBeInTheDocument();
    });
  });
});
