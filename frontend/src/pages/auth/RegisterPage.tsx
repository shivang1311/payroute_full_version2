import { useState } from 'react';
import { useNavigate, Link } from 'react-router';
import { Form, Input, Button, Typography, Alert } from 'antd';
import { UserOutlined, LockOutlined, MailOutlined, PhoneOutlined } from '@ant-design/icons';
import { useAuthStore } from '../../stores/authStore';
import Logo from '../../components/common/Logo';
import { EMAIL_PATTERN, EMAIL_VALIDATION_MESSAGE } from '../../utils/emailValidation';

const { Title, Text } = Typography;

export default function RegisterPage() {
  const navigate = useNavigate();
  const { register, isLoading } = useAuthStore();
  const [form] = Form.useForm();
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (values: {
    name: string;
    username: string;
    email: string;
    password: string;
    phone: string;
  }) => {
    setError(null);
    try {
      // Self-registration is always CUSTOMER. Staff users are created by an admin
      // from the User Management page.
      await register(values.name, values.username, values.email, values.password, values.phone);
      navigate('/dashboard');
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      setError(e.response?.data?.message || 'Registration failed. Please try again.');
    }
  };

  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--pr-bg)' }}>
      {/* Left brand panel */}
      <div
        className="pr-login-brand"
        style={{
          flex: '1 1 48%',
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
        <Logo size={44} textColor="#fff" />

        <div style={{ position: 'relative', zIndex: 1, maxWidth: 480 }}>
          <Title level={1} style={{ color: '#fff', fontSize: 36, lineHeight: 1.2, marginBottom: 16 }}>
            Get started with PayRoute Hub.
          </Title>
          <Text style={{ color: 'rgba(255,255,255,0.85)', fontSize: 16, lineHeight: 1.6 }}>
            Join the enterprise platform that moves money faster, safer and with full visibility across every rail.
          </Text>
        </div>

        <Text style={{ color: 'rgba(255,255,255,0.65)', fontSize: 12, position: 'relative', zIndex: 1 }}>
          © {new Date().getFullYear()} PayRoute Hub
        </Text>
      </div>

      {/* Right form panel */}
      <div
        style={{
          flex: '1 1 52%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '32px 24px',
          overflowY: 'auto',
        }}
      >
        <div style={{ width: '100%', maxWidth: 440 }}>
          <div style={{ marginBottom: 28 }}>
            <Title level={2} style={{ marginBottom: 8, fontWeight: 700 }}>
              Create your account
            </Title>
            <Text type="secondary" style={{ fontSize: 15 }}>
              Just a few details to get you started
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
              label="Full Name"
              name="name"
              rules={[
                { required: true, message: 'Full name is required' },
                { min: 2, message: 'Min 2 characters' },
              ]}
            >
              <Input
                prefix={<UserOutlined style={{ color: 'var(--pr-text-muted)' }} />}
                placeholder="e.g. Priya Sharma"
                name="name"
                id="reg-name"
                autoComplete="name"
              />
            </Form.Item>

            <Form.Item
              label="Username"
              name="username"
              rules={[
                { required: true, message: 'Username is required' },
                { min: 3, message: 'Min 3 characters' },
                {
                  pattern: /^[a-zA-Z0-9._-]+$/,
                  message: 'Only letters, numbers, dots, underscores and hyphens allowed',
                },
              ]}
            >
              <Input
                prefix={<UserOutlined style={{ color: 'var(--pr-text-muted)' }} />}
                placeholder="Login ID"
                name="username"
                id="reg-username"
                autoComplete="username"
              />
            </Form.Item>

            <Form.Item
              label="Email"
              name="email"
              rules={[
                { required: true, message: 'Email is required' },
                { type: 'email', message: 'Invalid email format' },
                { pattern: EMAIL_PATTERN, message: EMAIL_VALIDATION_MESSAGE },
              ]}
            >
              <Input
                prefix={<MailOutlined style={{ color: 'var(--pr-text-muted)' }} />}
                placeholder="you@company.com"
                name="email"
                id="reg-email"
                type="email"
                autoComplete="email"
              />
            </Form.Item>

            <Form.Item
              label="Password"
              name="password"
              rules={[
                { required: true, message: 'Password is required' },
                { min: 8, message: 'Password must be at least 8 characters' },
                {
                  pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z\d]).{8,}$/,
                  message: 'Must include uppercase, lowercase, number & special character',
                },
              ]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: 'var(--pr-text-muted)' }} />}
                placeholder="e.g. Secret@123"
                name="password"
                id="reg-password"
                autoComplete="new-password"
              />
            </Form.Item>

            <Form.Item
              label="Phone"
              name="phone"
              rules={[
                { required: true, message: 'Phone number is required' },
                { len: 10, message: 'Phone number must be exactly 10 digits' },
                { pattern: /^\d{10}$/, message: 'Phone number must contain only digits' },
              ]}
            >
              <Input
                prefix={<PhoneOutlined style={{ color: 'var(--pr-text-muted)' }} />}
                placeholder="10-digit phone"
                maxLength={10}
                name="phone"
                id="reg-phone"
                type="tel"
                autoComplete="tel-national"
              />
            </Form.Item>

            <Form.Item style={{ marginBottom: 16 }}>
              <Button type="primary" htmlType="submit" loading={isLoading} block size="large">
                Create Account
              </Button>
            </Form.Item>
          </Form>

          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <Text type="secondary">
              Already have an account?{' '}
              <Link to="/login" style={{ fontWeight: 600, color: 'var(--pr-brand-1)' }}>
                Sign in
              </Link>
            </Text>
          </div>
        </div>
      </div>
    </div>
  );
}
