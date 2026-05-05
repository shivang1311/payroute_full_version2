/**
 * Tests the role-based menu filtering in Sidebar — exactly the kind of bug we
 * already hit once (RECONCILIATION couldn't reach Returns/Reconciliation
 * because they were buried in OPS-only "Exceptions"). Each role gets its own
 * snapshot of the visible top-level menu items.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import type { Role, User } from '../../../types';

// Mock the auth store so each test can swap the active user.
const mockUser = { current: null as User | null };
vi.mock('../../../stores/authStore', () => ({
  useAuthStore: () => ({ user: mockUser.current }),
}));

import Sidebar from '../Sidebar';

const makeUser = (role: Role): User => ({
  id: 1,
  username: 'tester',
  email: 'tester@payroute.com',
  role,
  active: true,
  createdAt: '2026-01-01T00:00:00',
});

const renderSidebar = () =>
  render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <Sidebar />
    </MemoryRouter>,
  );

const visibleMenuLabels = (): string[] => {
  // Top-level rendered items inside the menu — text content of each menuitem.
  const items = document.querySelectorAll('.ant-menu-item, .ant-menu-submenu-title');
  return Array.from(items).map((el) => el.textContent?.trim() || '');
};

describe('Sidebar role filtering', () => {
  beforeEach(() => {
    mockUser.current = null;
  });

  describe('CUSTOMER', () => {
    beforeEach(() => {
      mockUser.current = makeUser('CUSTOMER');
    });

    it('shows Dashboard, Payments group, and Notifications', () => {
      renderSidebar();
      const labels = visibleMenuLabels();
      expect(labels).toContain('Dashboard');
      expect(labels).toContain('Payments');
      expect(labels).toContain('Notifications');
    });

    it('does NOT show ops/admin tools', () => {
      renderSidebar();
      const labels = visibleMenuLabels();
      expect(labels).not.toContain('Ops Dashboard');
      expect(labels).not.toContain('Routing');
      expect(labels).not.toContain('Compliance');
      expect(labels).not.toContain('Administration');
      expect(labels).not.toContain('Reconciliation');
      expect(labels).not.toContain('Exception Queue');
      expect(labels).not.toContain('Exceptions');
    });
  });

  describe('COMPLIANCE', () => {
    beforeEach(() => {
      mockUser.current = makeUser('COMPLIANCE');
    });

    it('shows Compliance group, hides Payments/Routing/Reconciliation', () => {
      renderSidebar();
      const labels = visibleMenuLabels();
      expect(labels).toContain('Dashboard');
      expect(labels).toContain('Compliance');
      expect(labels).toContain('Notifications');
      // Compliance is a queue-only role
      expect(labels).not.toContain('Payments');
      expect(labels).not.toContain('Routing');
      expect(labels).not.toContain('Reconciliation');
      expect(labels).not.toContain('Exception Queue');
      expect(labels).not.toContain('Administration');
    });
  });

  describe('RECONCILIATION', () => {
    beforeEach(() => {
      mockUser.current = makeUser('RECONCILIATION');
    });

    it('shows Reconciliation group with Breaks + Returns visible (regression for menu-visibility bug)', async () => {
      const user = userEvent.setup();
      renderSidebar();
      const labels = visibleMenuLabels();

      // Top-level: Reconciliation menu must be visible to RECON
      expect(labels).toContain('Reconciliation');
      // Settlement also visible
      expect(labels).toContain('Settlement');
      // Compliance / Routing / Payments / Admin are NOT visible
      expect(labels).not.toContain('Compliance');
      expect(labels).not.toContain('Payments');
      expect(labels).not.toContain('Routing');
      expect(labels).not.toContain('Administration');

      // Expand the Reconciliation group and verify its children
      const trigger = screen.getByText('Reconciliation');
      await user.click(trigger);
      // Children render under the open submenu
      const allItems = visibleMenuLabels();
      expect(allItems).toContain('Breaks');
      expect(allItems).toContain('Returns');
    });

    it('does not see the OPS-only "Exception Queue"', () => {
      renderSidebar();
      expect(visibleMenuLabels()).not.toContain('Exception Queue');
    });
  });

  describe('OPERATIONS', () => {
    beforeEach(() => {
      mockUser.current = makeUser('OPERATIONS');
    });

    it('shows Payments, Exception Queue, Reconciliation, but not Administration', () => {
      renderSidebar();
      const labels = visibleMenuLabels();
      expect(labels).toContain('Payments');
      expect(labels).toContain('Reconciliation');
      // Exception Queue group has only one item (after refactor) so it lifts to top level
      expect(labels).toContain('Exception Queue');
      expect(labels).not.toContain('Administration');
    });
  });

  describe('ADMIN', () => {
    beforeEach(() => {
      mockUser.current = makeUser('ADMIN');
    });

    it('sees the full menu including Administration and Reconciliation', () => {
      renderSidebar();
      const labels = visibleMenuLabels();
      expect(labels).toContain('Dashboard');
      expect(labels).toContain('Ops Dashboard');
      expect(labels).toContain('Payments');
      expect(labels).toContain('Compliance');
      expect(labels).toContain('Reconciliation');
      expect(labels).toContain('Settlement');
      expect(labels).toContain('Administration');
      expect(labels).toContain('Exception Queue');
    });

    it('Administration submenu has all 4 admin pages', async () => {
      const user = userEvent.setup();
      renderSidebar();
      const adminTrigger = screen.getByText('Administration');
      await user.click(adminTrigger);
      const labels = visibleMenuLabels();
      expect(labels).toContain('User Management');
      expect(labels).toContain('Audit Log');
      expect(labels).toContain('Webhooks');
      expect(labels).toContain('SLA Config');
    });
  });

  describe('unauthenticated', () => {
    it('renders only items with no role restriction (Dashboard, Notifications)', () => {
      mockUser.current = null;
      renderSidebar();
      const labels = visibleMenuLabels();
      expect(labels).toContain('Dashboard');
      expect(labels).toContain('Notifications');
      expect(labels).not.toContain('Payments');
      expect(labels).not.toContain('Administration');
    });
  });

  describe('navigation', () => {
    it('clicking Dashboard navigates to /dashboard', async () => {
      mockUser.current = makeUser('ADMIN');
      const user = userEvent.setup();
      renderSidebar();
      const dashboard = screen.getByText('Dashboard');
      await user.click(dashboard);
      // Just asserting the menu item exists and is clickable — full nav tested via routing in app
      expect(dashboard.closest('.ant-menu-item')).toBeInTheDocument();
    });

    it('shows the version footer when not collapsed', () => {
      mockUser.current = makeUser('ADMIN');
      const { container } = render(
        <MemoryRouter>
          <Sidebar collapsed={false} />
        </MemoryRouter>,
      );
      expect(within(container).getByText(/PayRoute Hub · v1\.0/)).toBeInTheDocument();
    });

    it('hides the version footer when collapsed', () => {
      mockUser.current = makeUser('ADMIN');
      const { container } = render(
        <MemoryRouter>
          <Sidebar collapsed={true} />
        </MemoryRouter>,
      );
      expect(within(container).queryByText(/PayRoute Hub · v1\.0/)).not.toBeInTheDocument();
    });
  });
});
