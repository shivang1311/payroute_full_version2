import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Form, Input, Button, Typography, Alert } from 'antd';
import { LockOutlined, KeyOutlined } from '@ant-design/icons';
import { useAuthStore } from '../../stores/authStore';
import Logo from '../../components/common/Logo';

const { Title, Text } = Typography;

/**
 * Forced password rotation screen — shown to staff users on first login while
 * `user.mustChangePassword` is true. Successful rotation issues fresh tokens
 * and clears the flag, after which the user is redirected to the dashboard.
 */
export default function ChangePasswordPage() {
  const navigate = useNavigate();
  const { user, changePassword, isLoading, logout } = useAuthStore();
  const [form] = Form.useForm();
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (values: {
    currentPassword: string;
    newPassword: string;
    confirmPassword: string;
  }) => {
    setError(null);
    if (values.newPassword !== values.confirmPassword) {
      setError('New password and confirmation do not match');
      return;
    }
    try {
      await changePassword(values.currentPassword, values.newPassword);
      navigate('/dashboard', { replace: true });
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      setError(e.response?.data?.message || 'Failed to change password');
    }
  };

  return (
    <div
      style={{
        display: 'flex',
        minHeight: '100vh',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--pr-bg)',
        padding: 24,
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 460,
          background: 'var(--pr-bg-elevated)',
          padding: '36px 32px',
          borderRadius: 14,
          boxShadow: 'var(--pr-shadow-md)',
          border: '1px solid var(--pr-border)',
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Logo size={40} />
        </div>

        <div
          className="pr-stat-icon brand"
          style={{ margin: '0 auto 16px', width: 48, height: 48, fontSize: 22 }}
        >
          <KeyOutlined />
        </div>

        <Title level={3} style={{ textAlign: 'center', marginBottom: 8 }}>
          Set a new password
        </Title>
        <Text
          type="secondary"
          style={{ display: 'block', textAlign: 'center', marginBottom: 24, fontSize: 14 }}
        >
          Hi {user?.username}, your account uses a temporary password issued by an administrator.
          Please choose a new password to continue.
        </Text>

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
            label="Current (temporary) password"
            name="currentPassword"
            rules={[{ required: true, message: 'Enter the temporary password' }]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: 'var(--pr-text-muted)' }} />}
              placeholder="Temporary password"
              autoComplete="current-password"
            />
          </Form.Item>

          <Form.Item
            label="New password"
            name="newPassword"
            rules={[
              { required: true, message: 'Choose a new password' },
              { min: 8, message: 'Min 8 characters' },
              {
                pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z\d]).{8,}$/,
                message: 'Must include uppercase, lowercase, number & special character',
              },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: 'var(--pr-text-muted)' }} />}
              placeholder="e.g. Stronger@123"
              autoComplete="new-password"
            />
          </Form.Item>

          <Form.Item
            label="Confirm new password"
            name="confirmPassword"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: 'Re-enter the new password' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || value === getFieldValue('newPassword')) return Promise.resolve();
                  return Promise.reject(new Error('Passwords do not match'));
                },
              }),
            ]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: 'var(--pr-text-muted)' }} />}
              placeholder="Repeat the new password"
              autoComplete="new-password"
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 12 }}>
            <Button type="primary" htmlType="submit" loading={isLoading} block size="large">
              Change Password
            </Button>
          </Form.Item>
        </Form>

        <div style={{ textAlign: 'center', marginTop: 8 }}>
          <Button
            type="link"
            onClick={async () => {
              await logout();
              navigate('/login', { replace: true });
            }}
            style={{ color: 'var(--pr-text-muted)' }}
          >
            Cancel and sign out
          </Button>
        </div>
      </div>
    </div>
  );
}
