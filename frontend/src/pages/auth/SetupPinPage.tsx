import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Form, Input, Button, Typography, Alert } from 'antd';
import { LockOutlined, NumberOutlined } from '@ant-design/icons';
import { useAuthStore } from '../../stores/authStore';
import Logo from '../../components/common/Logo';

const { Title, Text } = Typography;

/**
 * Mandatory transaction-PIN setup screen — shown to a CUSTOMER on first login
 * (or whenever `user.pinSet === false`). The PIN is required for every payment
 * and scheduled-payment authorisation. After successful setup the user is
 * redirected to the dashboard.
 */
export default function SetupPinPage() {
  const navigate = useNavigate();
  const { user, setTransactionPin, logout, isLoading } = useAuthStore();
  const [form] = Form.useForm();
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (values: { pin: string; confirmPin: string }) => {
    setError(null);
    if (values.pin !== values.confirmPin) {
      setError('PIN and confirmation do not match');
      return;
    }
    try {
      await setTransactionPin(values.pin);
      navigate('/dashboard', { replace: true });
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      setError(e.response?.data?.message || 'Failed to save transaction PIN');
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
          <NumberOutlined />
        </div>

        <Title level={3} style={{ textAlign: 'center', marginBottom: 8 }}>
          Set your transaction PIN
        </Title>
        <Text
          type="secondary"
          style={{ display: 'block', textAlign: 'center', marginBottom: 24, fontSize: 14 }}
        >
          Hi {user?.username}, please choose a 4–6 digit PIN. You'll enter it to
          authorise every payment and scheduled transfer — it's an extra security
          layer on top of your account password.
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
            label="Transaction PIN"
            name="pin"
            rules={[
              { required: true, message: 'Choose a PIN' },
              { pattern: /^\d{4,6}$/, message: 'PIN must be 4 to 6 digits' },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: 'var(--pr-text-muted)' }} />}
              placeholder="4-6 digit PIN"
              maxLength={6}
              inputMode="numeric"
              autoComplete="off"
            />
          </Form.Item>

          <Form.Item
            label="Confirm PIN"
            name="confirmPin"
            dependencies={['pin']}
            rules={[
              { required: true, message: 'Re-enter the PIN' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || value === getFieldValue('pin')) return Promise.resolve();
                  return Promise.reject(new Error('PINs do not match'));
                },
              }),
            ]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: 'var(--pr-text-muted)' }} />}
              placeholder="Repeat the PIN"
              maxLength={6}
              inputMode="numeric"
              autoComplete="off"
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 12 }}>
            <Button type="primary" htmlType="submit" loading={isLoading} block size="large">
              Save PIN
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
