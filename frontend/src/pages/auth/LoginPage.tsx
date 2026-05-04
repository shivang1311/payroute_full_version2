import { useState } from 'react';
import { useNavigate, Link } from 'react-router';
import { Form, Input, Button, Typography, Alert } from 'antd';
import { UserOutlined, LockOutlined, ThunderboltOutlined, SafetyOutlined, NodeIndexOutlined } from '@ant-design/icons';
import { useAuthStore } from '../../stores/authStore';
import Logo from '../../components/common/Logo';

const { Title, Text } = Typography;

export default function LoginPage() {
  const navigate = useNavigate();
  const { login, isLoading } = useAuthStore();
  const [form] = Form.useForm();
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (values: { username: string; password: string }) => {
    setError(null);
    try {
      await login(values.username, values.password);
      navigate('/dashboard');
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      setError(e.response?.data?.message || 'Invalid username or password');
    }
  };

  return (
    <div
      style={{
        display: 'flex',
        minHeight: '100vh',
        background: 'var(--pr-bg)',
      }}
    >
      {/* ---------- Left brand panel ---------- */}
      <div
        className="pr-login-brand"
        style={{
          flex: '1 1 52%',
          position: 'relative',
          background: 'linear-gradient(135deg, #4a4ae8 0%, #7c4ef5 60%, #8855ff 100%)',
          color: '#fff',
          padding: '64px 56px',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          overflow: 'hidden',
        }}
      >
        {/* Decorative blobs */}
        <div
          style={{
            position: 'absolute',
            width: 420,
            height: 420,
            borderRadius: '50%',
            background: 'radial-gradient(circle, rgba(255,255,255,0.18) 0%, rgba(255,255,255,0) 70%)',
            top: -120,
            right: -80,
            pointerEvents: 'none',
          }}
        />
        <div
          style={{
            position: 'absolute',
            width: 320,
            height: 320,
            borderRadius: '50%',
            background: 'radial-gradient(circle, rgba(255,255,255,0.10) 0%, rgba(255,255,255,0) 70%)',
            bottom: -80,
            left: -60,
            pointerEvents: 'none',
          }}
        />

        <Logo size={44} textColor="#fff" />

        <div style={{ position: 'relative', zIndex: 1, maxWidth: 520 }}>
          <Title level={1} style={{ color: '#fff', fontSize: 40, lineHeight: 1.15, marginBottom: 16 }}>
            Move money with confidence.
          </Title>
          <Text style={{ color: 'rgba(255,255,255,0.85)', fontSize: 16, lineHeight: 1.6 }}>
            Enterprise payments orchestration — compliance, routing, settlement and reconciliation in one place.
          </Text>

          <div style={{ marginTop: 40, display: 'grid', gap: 18 }}>
            <FeatureRow
              icon={<ThunderboltOutlined />}
              title="Real-time routing"
              subtitle="Smart rails pick the best path per payment."
            />
            <FeatureRow
              icon={<SafetyOutlined />}
              title="Built-in compliance"
              subtitle="Screening, holds and audit trail on every transaction."
            />
            <FeatureRow
              icon={<NodeIndexOutlined />}
              title="End-to-end visibility"
              subtitle="Ledger, exceptions and settlement in one place."
            />
          </div>
        </div>

        <Text style={{ color: 'rgba(255,255,255,0.65)', fontSize: 12, position: 'relative', zIndex: 1 }}>
          © {new Date().getFullYear()} PayRoute Hub · Secure enterprise payments
        </Text>
      </div>

      {/* ---------- Right form panel ---------- */}
      <div
        style={{
          flex: '1 1 48%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 32,
        }}
      >
        <div style={{ width: '100%', maxWidth: 400 }}>
          <div style={{ marginBottom: 32 }}>
            <Title level={2} style={{ marginBottom: 8, fontWeight: 700 }}>
              Welcome back
            </Title>
            <Text type="secondary" style={{ fontSize: 15 }}>
              Sign in to your PayRoute Hub account
            </Text>
          </div>

          {error && (
            <Alert
              message={error}
              type="error"
              showIcon
              closable
              onClose={() => setError(null)}
              style={{ marginBottom: 20 }}
            />
          )}

          <Form form={form} onFinish={handleSubmit} layout="vertical" size="large" requiredMark={false}>
            <Form.Item
              label="Username"
              name="username"
              rules={[{ required: true, message: 'Please enter your username' }]}
            >
              <Input
                prefix={<UserOutlined style={{ color: 'var(--pr-text-muted)' }} />}
                placeholder="Enter your username"
                name="username"
                id="login-username"
                autoComplete="username"
              />
            </Form.Item>

            <Form.Item
              label="Password"
              name="password"
              rules={[{ required: true, message: 'Please enter your password' }]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: 'var(--pr-text-muted)' }} />}
                placeholder="Enter your password"
                name="password"
                id="login-password"
                autoComplete="current-password"
              />
            </Form.Item>

            <Form.Item style={{ marginBottom: 16 }}>
              <Button type="primary" htmlType="submit" loading={isLoading} block size="large">
                Sign In
              </Button>
            </Form.Item>
          </Form>

          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <Text type="secondary">
              Don't have an account?{' '}
              <Link to="/register" style={{ fontWeight: 600, color: 'var(--pr-brand-1)' }}>
                Register here
              </Link>
            </Text>
          </div>
        </div>
      </div>
    </div>
  );
}

function FeatureRow({
  icon,
  title,
  subtitle,
}: {
  icon: React.ReactNode;
  title: string;
  subtitle: string;
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14 }}>
      <div
        style={{
          width: 40,
          height: 40,
          flexShrink: 0,
          borderRadius: 10,
          background: 'rgba(255,255,255,0.18)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          fontSize: 18,
          backdropFilter: 'blur(4px)',
        }}
      >
        {icon}
      </div>
      <div>
        <div style={{ color: '#fff', fontWeight: 600, fontSize: 15, marginBottom: 2 }}>{title}</div>
        <div style={{ color: 'rgba(255,255,255,0.75)', fontSize: 13 }}>{subtitle}</div>
      </div>
    </div>
  );
}
