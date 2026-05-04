import { useEffect, lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router';
import { ConfigProvider, Spin, App as AntApp, theme as antdTheme } from 'antd';
import { useAuthStore } from './stores/authStore';
import { useThemeStore } from './stores/themeStore';
import AppLayout from './components/layout/AppLayout';
import ProtectedRoute from './components/common/ProtectedRoute';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';
import ChangePasswordPage from './pages/auth/ChangePasswordPage';
import SetupPinPage from './pages/auth/SetupPinPage';

// Lazy-load pages for code splitting
const DashboardPage = lazy(() => import('./pages/dashboard/DashboardPage'));
const OpsDashboardPage = lazy(() => import('./pages/dashboard/OpsDashboardPage'));
const PaymentListPage = lazy(() => import('./pages/payments/PaymentListPage'));
const CreatePaymentPage = lazy(() => import('./pages/payments/CreatePaymentPage'));
const ScheduledPaymentsPage = lazy(() => import('./pages/payments/ScheduledPaymentsPage'));
const PaymentDetailPage = lazy(() => import('./pages/payments/PaymentDetailPage'));
const PartyListPage = lazy(() => import('./pages/parties/PartyListPage'));
const AccountDirectoryPage = lazy(() => import('./pages/parties/AccountDirectoryPage'));
const RoutingRulesPage = lazy(() => import('./pages/routing/RoutingRulesPage'));
const RailInstructionsPage = lazy(() => import('./pages/routing/RailInstructionsPage'));
const LedgerEntriesPage = lazy(() => import('./pages/ledger/LedgerEntriesPage'));
const FeeSchedulePage = lazy(() => import('./pages/ledger/FeeSchedulePage'));
const StatementPage = lazy(() => import('./pages/ledger/StatementPage'));
const ComplianceChecksPage = lazy(() => import('./pages/compliance/ComplianceChecksPage'));
const HoldsPage = lazy(() => import('./pages/compliance/HoldsPage'));
const ExceptionQueuePage = lazy(() => import('./pages/exceptions/ExceptionQueuePage'));
const ReturnsPage = lazy(() => import('./pages/exceptions/ReturnsPage'));
const ReconciliationPage = lazy(() => import('./pages/exceptions/ReconciliationPage'));
const SettlementBatchesPage = lazy(() => import('./pages/settlement/SettlementBatchesPage'));
const ReportsPage = lazy(() => import('./pages/settlement/ReportsPage'));
const NotificationsPage = lazy(() => import('./pages/notifications/NotificationsPage'));
const UserManagementPage = lazy(() => import('./pages/admin/UserManagementPage'));
const AuditLogPage = lazy(() => import('./pages/admin/AuditLogPage'));
const WebhookSettingsPage = lazy(() => import('./pages/admin/WebhookSettingsPage'));
const SlaConfigPage = lazy(() => import('./pages/admin/SlaConfigPage'));
const ProfilePage = lazy(() => import('./pages/profile/ProfilePage'));

const PageLoader = () => (
  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
    <Spin size="large" />
  </div>
);

export default function App() {
  const { loadFromStorage } = useAuthStore();
  const themeMode = useThemeStore((s) => s.mode);
  const isDark = themeMode === 'dark';

  useEffect(() => {
    loadFromStorage();
  }, [loadFromStorage]);

  return (
    <ConfigProvider
      theme={{
        algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
        token: {
          // Brand
          colorPrimary: '#5b5bf0',
          colorInfo: '#5b5bf0',
          colorSuccess: '#16a34a',
          colorWarning: '#f59e0b',
          colorError: '#ef4444',

          // Shape
          borderRadius: 10,
          borderRadiusLG: 14,
          borderRadiusSM: 6,

          // Typography
          fontFamily:
            "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
          fontSize: 14,

          // Surface
          colorBgLayout: isDark ? '#0f1117' : '#f4f5fb',
          colorBgContainer: isDark ? '#161a23' : '#ffffff',
          colorBgElevated: isDark ? '#1a1f2b' : '#ffffff',
          colorBorder: isDark ? '#2a3040' : '#e5e7eb',
          colorBorderSecondary: isDark ? '#242a37' : '#f0f2f5',

          // Text
          colorText: isDark ? '#e6e8ee' : '#1f2937',
          colorTextSecondary: isDark ? '#9aa3b2' : '#6b7280',

          // Controls
          controlHeight: 38,

          // Motion
          motionDurationMid: '0.18s',
        },
        components: {
          Layout: {
            headerBg: isDark ? '#161a23' : '#ffffff',
            headerHeight: 60,
            siderBg: isDark ? '#161a23' : '#ffffff',
            bodyBg: isDark ? '#0f1117' : '#f4f5fb',
          },
          Menu: {
            itemBorderRadius: 8,
            itemHeight: 40,
            itemMarginInline: 8,
            iconSize: 16,
          },
          Card: {
            borderRadiusLG: 14,
            paddingLG: 20,
          },
          Button: {
            controlHeight: 38,
            fontWeight: 500,
          },
          Table: {
            headerSplitColor: 'transparent',
            rowHoverBg: isDark ? 'rgba(91,91,240,0.08)' : 'rgba(91,91,240,0.04)',
          },
          Statistic: {
            titleFontSize: 13,
          },
          Tag: {
            borderRadiusSM: 6,
          },
        },
      }}
    >
      <AntApp>
      <BrowserRouter>
        <Suspense fallback={<PageLoader />}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route
              path="/change-password"
              element={
                <ProtectedRoute>
                  <ChangePasswordPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/setup-pin"
              element={
                <ProtectedRoute>
                  <SetupPinPage />
                </ProtectedRoute>
              }
            />

            <Route
              element={
                <ProtectedRoute>
                  <AppLayout />
                </ProtectedRoute>
              }
            >
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route
                path="/ops-dashboard"
                element={
                  <ProtectedRoute allowedRoles={['ADMIN', 'OPERATIONS', 'RECONCILIATION']}>
                    <OpsDashboardPage />
                  </ProtectedRoute>
                }
              />

              {/* Payments */}
              <Route path="/payments" element={<PaymentListPage />} />
              <Route path="/payments/new" element={<CreatePaymentPage />} />
              <Route path="/payments/scheduled" element={<ScheduledPaymentsPage />} />
              <Route path="/payments/:id" element={<PaymentDetailPage />} />

              {/* Parties & Accounts */}
              <Route path="/parties" element={<PartyListPage />} />
              <Route path="/accounts" element={<AccountDirectoryPage />} />

              {/* Routing */}
              <Route path="/routing" element={<RoutingRulesPage />} />
              <Route path="/routing/instructions" element={<RailInstructionsPage />} />

              {/* Ledger */}
              <Route path="/ledger" element={<LedgerEntriesPage />} />
              <Route path="/ledger/fees" element={<FeeSchedulePage />} />
              <Route path="/ledger/statement" element={<StatementPage />} />

              {/* Compliance */}
              <Route path="/compliance" element={<ComplianceChecksPage />} />
              <Route path="/compliance/holds" element={<HoldsPage />} />

              {/* Exceptions */}
              <Route path="/exceptions" element={<ExceptionQueuePage />} />
              <Route path="/exceptions/returns" element={<ReturnsPage />} />
              <Route path="/exceptions/reconciliation" element={<ReconciliationPage />} />

              {/* Settlement */}
              <Route path="/settlement" element={<SettlementBatchesPage />} />
              <Route path="/settlement/reports" element={<ReportsPage />} />

              {/* Notifications */}
              <Route path="/notifications" element={<NotificationsPage />} />

              {/* Admin */}
              <Route path="/admin/users" element={<UserManagementPage />} />
              <Route path="/admin/audit" element={<AuditLogPage />} />
              <Route path="/admin/webhooks" element={<WebhookSettingsPage />} />
              <Route
                path="/admin/sla"
                element={
                  <ProtectedRoute allowedRoles={['ADMIN']}>
                    <SlaConfigPage />
                  </ProtectedRoute>
                }
              />
            </Route>

            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </Suspense>
      </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  );
}
