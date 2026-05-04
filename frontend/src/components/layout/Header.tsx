import { useNavigate, useLocation } from 'react-router';
import { Layout, Badge, Dropdown, Avatar, Space, Typography, Tag, Button, Tooltip } from 'antd';
import {
  BellOutlined,
  UserOutlined,
  LogoutOutlined,
  SunOutlined,
  MoonOutlined,
} from '@ant-design/icons';
import { useAuthStore } from '../../stores/authStore';
import { useNotificationStore } from '../../stores/notificationStore';
import { useThemeStore } from '../../stores/themeStore';
import { useEffect } from 'react';
import type { MenuProps } from 'antd';
import Logo from '../common/Logo';

const { Text } = Typography;

const roleColors: Record<string, string> = {
  ADMIN: 'red',
  OPERATIONS: 'blue',
  COMPLIANCE: 'orange',
  RECONCILIATION: 'green',
  CUSTOMER: 'default',
};

export default function AppHeader() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuthStore();
  const { unreadCount, fetchUnreadCount } = useNotificationStore();
  const themeMode = useThemeStore((s) => s.mode);
  const toggleTheme = useThemeStore((s) => s.toggle);

  // Poll every 10s, plus refetch on tab focus / network reconnect
  useEffect(() => {
    fetchUnreadCount();
    const interval = setInterval(fetchUnreadCount, 10000);
    const onFocus = () => fetchUnreadCount();
    const onVisible = () => {
      if (document.visibilityState === 'visible') fetchUnreadCount();
    };
    window.addEventListener('focus', onFocus);
    window.addEventListener('online', onFocus);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      clearInterval(interval);
      window.removeEventListener('focus', onFocus);
      window.removeEventListener('online', onFocus);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [fetchUnreadCount]);

  // Also refetch on every route change (catches actions like "payment created")
  useEffect(() => {
    fetchUnreadCount();
  }, [location.pathname, fetchUnreadCount]);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'identity',
      icon: <UserOutlined />,
      label: (
        <span>
          <div style={{ fontWeight: 600, lineHeight: 1.2 }}>{user?.username}</div>
          <div style={{ fontSize: 12, color: 'var(--pr-text-muted)' }}>{user?.role}</div>
        </span>
      ),
      disabled: true,
    },
    { type: 'divider' },
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: 'My Profile',
      onClick: () => navigate('/profile'),
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Logout',
      onClick: handleLogout,
    },
  ];

  const isDark = themeMode === 'dark';

  return (
    <Layout.Header
      style={{
        padding: '0 24px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        borderBottom: '1px solid var(--pr-border)',
        position: 'sticky',
        top: 0,
        zIndex: 100,
        boxShadow: 'var(--pr-shadow-sm)',
        height: 60,
        lineHeight: '60px',
      }}
    >
      <div
        style={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}
        onClick={() => navigate('/dashboard')}
      >
        <Logo size={34} />
      </div>

      <Space size={16}>
        <Tooltip title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}>
          <Button
            type="text"
            shape="circle"
            icon={isDark ? <SunOutlined /> : <MoonOutlined />}
            onClick={toggleTheme}
            aria-label="Toggle theme"
          />
        </Tooltip>

        <Tooltip title="Notifications">
          <Badge count={unreadCount} size="small" offset={[-2, 2]}>
            <Button
              type="text"
              shape="circle"
              icon={<BellOutlined style={{ fontSize: 18 }} />}
              onClick={() => navigate('/notifications')}
              aria-label="Notifications"
            />
          </Badge>
        </Tooltip>

        <Dropdown menu={{ items: userMenuItems }} trigger={['click']} placement="bottomRight">
          <Space style={{ cursor: 'pointer', padding: '4px 10px', borderRadius: 8 }}>
            <Avatar
              size={32}
              style={{ background: 'var(--pr-brand-grad)', color: '#fff', fontWeight: 600 }}
            >
              {user?.username?.[0]?.toUpperCase() || <UserOutlined />}
            </Avatar>
            <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.2 }}>
              <Text style={{ fontWeight: 600, fontSize: 13 }}>{user?.username}</Text>
              <Tag
                color={roleColors[user?.role || ''] || 'default'}
                style={{ fontSize: 10, margin: 0, padding: '0 6px', lineHeight: '16px' }}
              >
                {user?.role}
              </Tag>
            </div>
          </Space>
        </Dropdown>
      </Space>
    </Layout.Header>
  );
}
