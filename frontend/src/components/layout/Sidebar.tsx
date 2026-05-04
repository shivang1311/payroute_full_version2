import { useNavigate, useLocation } from 'react-router';
import { Menu } from 'antd';
import {
  DashboardOutlined,
  TransactionOutlined,
  TeamOutlined,
  NodeIndexOutlined,
  BookOutlined,
  SafetyCertificateOutlined,
  WarningOutlined,
  BankOutlined,
  BellOutlined,
  UserOutlined,
  AuditOutlined,
  SettingOutlined,
  IdcardOutlined,
  SendOutlined,
  DollarOutlined,
  StopOutlined,
  SwapOutlined,
  ReconciliationOutlined,
  FileTextOutlined,
  FundOutlined,
  ScheduleOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { useAuthStore } from '../../stores/authStore';
import type { Role } from '../../types';
import type { MenuProps } from 'antd';

type MenuItem = Required<MenuProps>['items'][number] & {
  roles?: Role[];
  children?: MenuItem[];
};

const menuItems: MenuItem[] = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: 'Dashboard' },
  { key: '/ops-dashboard', icon: <FundOutlined />, label: 'Ops Dashboard', roles: ['ADMIN', 'OPERATIONS', 'RECONCILIATION'] } as MenuItem,
  {
    key: '/payments-group',
    icon: <TransactionOutlined />,
    label: 'Payments',
    roles: ['CUSTOMER', 'OPERATIONS', 'ADMIN'],
    children: [
      { key: '/payments', icon: <TransactionOutlined />, label: 'All Payments' },
      { key: '/payments/new', icon: <SendOutlined />, label: 'New Payment' },
      { key: '/payments/scheduled', icon: <ScheduleOutlined />, label: 'Scheduled' },
    ],
  } as MenuItem,
  {
    key: '/parties-group',
    icon: <TeamOutlined />,
    label: 'Parties & Accounts',
    roles: ['CUSTOMER', 'OPERATIONS', 'ADMIN'],
    children: [
      { key: '/parties', icon: <TeamOutlined />, label: 'Parties', roles: ['OPERATIONS', 'ADMIN'] } as MenuItem,
      { key: '/accounts', icon: <IdcardOutlined />, label: 'Account Directory' },
    ],
  } as MenuItem,
  {
    key: '/routing-group',
    icon: <NodeIndexOutlined />,
    label: 'Routing',
    roles: ['OPERATIONS', 'ADMIN'],
    children: [
      { key: '/routing', icon: <NodeIndexOutlined />, label: 'Routing Rules' },
      { key: '/routing/instructions', icon: <SendOutlined />, label: 'Rail Instructions' },
    ],
  } as MenuItem,
  {
    key: '/ledger-group',
    icon: <BookOutlined />,
    label: 'Ledger',
    roles: ['CUSTOMER', 'RECONCILIATION', 'ADMIN'],
    children: [
      { key: '/ledger', icon: <BookOutlined />, label: 'Entries', roles: ['RECONCILIATION', 'ADMIN'] } as MenuItem,
      { key: '/ledger/fees', icon: <DollarOutlined />, label: 'Fee Schedule', roles: ['ADMIN'] } as MenuItem,
      { key: '/ledger/statement', icon: <FileTextOutlined />, label: 'Statements' },
    ],
  } as MenuItem,
  {
    key: '/compliance-group',
    icon: <SafetyCertificateOutlined />,
    label: 'Compliance',
    roles: ['COMPLIANCE', 'ADMIN', 'OPERATIONS'],
    children: [
      { key: '/compliance', icon: <SafetyCertificateOutlined />, label: 'Checks', roles: ['COMPLIANCE', 'ADMIN'] } as MenuItem,
      { key: '/compliance/holds', icon: <StopOutlined />, label: 'Holds' },
    ],
  } as MenuItem,
  {
    key: '/exceptions-group',
    icon: <WarningOutlined />,
    label: 'Exceptions',
    roles: ['OPERATIONS', 'ADMIN'],
    children: [
      { key: '/exceptions', icon: <WarningOutlined />, label: 'Exception Queue' },
      { key: '/exceptions/returns', icon: <SwapOutlined />, label: 'Returns' },
      { key: '/exceptions/reconciliation', icon: <ReconciliationOutlined />, label: 'Reconciliation' },
    ],
  } as MenuItem,
  {
    key: '/settlement-group',
    icon: <BankOutlined />,
    label: 'Settlement',
    roles: ['RECONCILIATION', 'ADMIN', 'OPERATIONS'],
    children: [
      { key: '/settlement', icon: <BankOutlined />, label: 'Batches' },
      { key: '/settlement/reports', icon: <FundOutlined />, label: 'Reports', roles: ['RECONCILIATION', 'ADMIN'] } as MenuItem,
    ],
  } as MenuItem,
  { key: '/notifications', icon: <BellOutlined />, label: 'Notifications' },
  {
    key: '/admin',
    icon: <SettingOutlined />,
    label: 'Administration',
    roles: ['ADMIN'],
    children: [
      { key: '/admin/users', icon: <UserOutlined />, label: 'User Management' },
      { key: '/admin/audit', icon: <AuditOutlined />, label: 'Audit Log' },
      { key: '/admin/webhooks', icon: <SendOutlined />, label: 'Webhooks' },
      { key: '/admin/sla', icon: <ClockCircleOutlined />, label: 'SLA Config' },
    ],
  } as MenuItem,
];

interface SidebarProps {
  collapsed?: boolean;
}

export default function Sidebar({ collapsed = false }: SidebarProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuthStore();

  const filterByRole = (items: MenuItem[]): MenuItem[] =>
    items
      .filter((item) => {
        const mi = item as MenuItem & { roles?: Role[] };
        return !mi.roles || (user && mi.roles.includes(user.role));
      })
      .flatMap((item) => {
        const mi = item as MenuItem & { children?: MenuItem[] };
        if (!mi.children) return [mi];
        const filteredChildren = filterByRole(mi.children);
        // Drop empty groups entirely
        if (filteredChildren.length === 0) return [];
        // Collapse single-child groups: lift the child up so we don't show
        // a submenu with just one item (e.g. "Parties & Accounts" → "Account Directory" for CUSTOMER)
        if (filteredChildren.length === 1) {
          const onlyChild = filteredChildren[0] as MenuItem & { icon?: React.ReactNode };
          // Keep the parent's icon if the child doesn't define one
          return [{ ...onlyChild, icon: onlyChild.icon || (mi as MenuItem & { icon?: React.ReactNode }).icon }];
        }
        return [{ ...mi, children: filteredChildren }];
      });

  const filteredItems = filterByRole(menuItems);

  // Determine which parent group to open based on current path
  const openKeys = menuItems
    .filter((item) => {
      const mi = item as MenuItem & { children?: MenuItem[] };
      return mi.children?.some((child) => {
        const ck = (child as MenuItem & { key: string }).key;
        return location.pathname === ck || location.pathname.startsWith(ck + '/');
      });
    })
    .map((item) => (item as MenuItem & { key: string }).key as string);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', paddingTop: 8 }}>
      <Menu
        mode="inline"
        selectedKeys={[location.pathname]}
        defaultOpenKeys={[...openKeys, '/admin']}
        style={{ flex: 1, borderRight: 0, background: 'transparent' }}
        onClick={({ key }) => navigate(key)}
        items={filteredItems as MenuProps['items']}
      />
      {!collapsed && (
        <div
          style={{
            padding: '12px 16px',
            fontSize: 11,
            color: 'var(--pr-text-muted)',
            borderTop: '1px solid var(--pr-border)',
            textAlign: 'center',
            letterSpacing: 0.3,
          }}
        >
          PayRoute Hub · v1.0
        </div>
      )}
    </div>
  );
}
