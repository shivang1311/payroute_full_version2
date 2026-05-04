import { useState } from 'react';
import { Outlet } from 'react-router';
import { Layout } from 'antd';
import Sidebar from './Sidebar';
import AppHeader from './Header';

const { Sider, Content } = Layout;

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <AppHeader />
      <Layout>
        <Sider
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
          width={248}
          theme="light"
          style={{
            borderRight: '1px solid var(--pr-border)',
            position: 'sticky',
            top: 60,
            height: 'calc(100vh - 60px)',
            overflow: 'auto',
          }}
        >
          <Sidebar collapsed={collapsed} />
        </Sider>
        <Content
          style={{
            padding: '24px 28px',
            minHeight: 280,
            background: 'var(--pr-bg)',
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
