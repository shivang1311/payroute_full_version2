import { useState } from 'react';
import {
  Card,
  Form,
  Input,
  Button,
  Tag,
  Row,
  Col,
  Descriptions,
  Divider,
  Alert,
  message,
} from 'antd';
import {
  UserOutlined,
  MailOutlined,
  PhoneOutlined,
  LockOutlined,
  SaveOutlined,
  KeyOutlined,
  NumberOutlined,
} from '@ant-design/icons';
import { useAuthStore } from '../../stores/authStore';
import PageHeader from '../../components/common/PageHeader';
import { EMAIL_PATTERN, EMAIL_VALIDATION_MESSAGE } from '../../utils/emailValidation';
import dayjs from 'dayjs';

const roleColorMap: Record<string, string> = {
  ADMIN: 'red',
  OPERATIONS: 'blue',
  COMPLIANCE: 'orange',
  RECONCILIATION: 'green',
  CUSTOMER: 'default',
};

export default function ProfilePage() {
  const { user, updateProfile, changePassword, setTransactionPin, isLoading } = useAuthStore();
  const [profileForm] = Form.useForm();
  const [passwordForm] = Form.useForm();
  const [pinForm] = Form.useForm();
  const [profileSaving, setProfileSaving] = useState(false);
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [pinSaving, setPinSaving] = useState(false);

  if (!user) return null;
  const isCustomer = user.role === 'CUSTOMER';
  const isAdmin = user.role === 'ADMIN';

  const handleProfileSave = async (values: { email: string; phone: string }) => {
    setProfileSaving(true);
    try {
      const payload: { email?: string; phone?: string } = {};
      if (values.email && values.email !== user.email) payload.email = values.email;
      if (values.phone && values.phone !== (user.phone || '')) payload.phone = values.phone;
      if (Object.keys(payload).length === 0) {
        message.info('Nothing to update');
        return;
      }
      await updateProfile(payload);
    } catch {
      /* error toast already shown by store */
    } finally {
      setProfileSaving(false);
    }
  };

  const handlePinSave = async (values: {
    currentPassword: string;
    newPin: string;
    confirmPin: string;
  }) => {
    if (values.newPin !== values.confirmPin) {
      message.error('PIN and confirmation do not match');
      return;
    }
    setPinSaving(true);
    try {
      await setTransactionPin(values.newPin, values.currentPassword);
      pinForm.resetFields();
    } catch {
      /* error toast already shown by store */
    } finally {
      setPinSaving(false);
    }
  };

  const handlePasswordSave = async (values: {
    currentPassword: string;
    newPassword: string;
    confirmPassword: string;
  }) => {
    if (values.newPassword !== values.confirmPassword) {
      message.error('New password and confirmation do not match');
      return;
    }
    setPasswordSaving(true);
    try {
      await changePassword(values.currentPassword, values.newPassword);
      passwordForm.resetFields();
    } catch {
      /* error toast already shown */
    } finally {
      setPasswordSaving(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="My Profile"
        subtitle="Update your contact details and password"
        icon={<UserOutlined />}
        iconVariant="brand"
      />

      <Row gutter={[20, 20]}>
        {/* ----- Identity card ----- */}
        <Col xs={24} lg={10}>
          <Card title="Account Identity" bodyStyle={{ paddingTop: 12 }}>
            <div style={{ textAlign: 'center', marginBottom: 16 }}>
              <div
                style={{
                  width: 84,
                  height: 84,
                  borderRadius: '50%',
                  background: 'var(--pr-brand-grad)',
                  color: '#fff',
                  fontSize: 34,
                  fontWeight: 700,
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: 'var(--pr-shadow-md)',
                }}
              >
                {user.username?.[0]?.toUpperCase() || <UserOutlined />}
              </div>
              <div style={{ marginTop: 12, fontSize: 18, fontWeight: 700 }}>{user.username}</div>
              {!isCustomer && (
                <Tag color={roleColorMap[user.role] || 'default'} style={{ marginTop: 6 }}>
                  {user.role}
                </Tag>
              )}
            </div>

            <Descriptions column={1} size="small" colon={false}>
              <Descriptions.Item label="Status">
                <Tag color={user.active ? 'green' : 'red'}>{user.active ? 'Active' : 'Inactive'}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Member since">
                {dayjs(user.createdAt).format('MMMM D, YYYY')}
              </Descriptions.Item>
            </Descriptions>

            {!isAdmin && (
              <Alert
                type="info"
                showIcon
                style={{ marginTop: 16 }}
                message={
                  isCustomer
                    ? 'Your username cannot be changed here.'
                    : 'Username and role are managed by your administrator and cannot be changed here.'
                }
              />
            )}
          </Card>
        </Col>

        {/* ----- Editable section ----- */}
        <Col xs={24} lg={14}>
          <Card
            title={
              <span>
                <MailOutlined style={{ marginRight: 8, color: 'var(--pr-brand-1)' }} />
                Contact Details
              </span>
            }
          >
            <Form
              form={profileForm}
              layout="vertical"
              initialValues={{ email: user.email, phone: user.phone || '' }}
              onFinish={handleProfileSave}
              requiredMark={false}
            >
              <Form.Item
                label="Email"
                name="email"
                rules={[
                  { required: true, message: 'Email is required' },
                  { type: 'email', message: 'Invalid email address' },
                  { pattern: EMAIL_PATTERN, message: EMAIL_VALIDATION_MESSAGE },
                ]}
              >
                <Input prefix={<MailOutlined />} placeholder="you@company.com" autoComplete="email" />
              </Form.Item>
              <Form.Item
                label="Phone"
                name="phone"
                rules={[
                  { required: true, message: 'Phone is required' },
                  { len: 10, message: 'Phone must be exactly 10 digits' },
                  { pattern: /^\d{10}$/, message: 'Digits only' },
                ]}
              >
                <Input prefix={<PhoneOutlined />} maxLength={10} placeholder="10-digit phone" />
              </Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                icon={<SaveOutlined />}
                loading={profileSaving || isLoading}
              >
                Save Changes
              </Button>
            </Form>

            <Divider style={{ margin: '24px 0' }} />

            <div style={{ marginBottom: 16, fontWeight: 600, fontSize: 15 }}>
              <KeyOutlined style={{ marginRight: 8, color: 'var(--pr-brand-1)' }} />
              Change Password
            </div>
            <Form
              form={passwordForm}
              layout="vertical"
              onFinish={handlePasswordSave}
              requiredMark={false}
            >
              <Form.Item
                label="Current password"
                name="currentPassword"
                rules={[{ required: true, message: 'Enter your current password' }]}
              >
                <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
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
                  prefix={<LockOutlined />}
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
                <Input.Password prefix={<LockOutlined />} autoComplete="new-password" />
              </Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                icon={<KeyOutlined />}
                loading={passwordSaving || isLoading}
              >
                Update Password
              </Button>
            </Form>

            {isCustomer && (
              <>
                <Divider style={{ margin: '24px 0' }} />
                <div style={{ marginBottom: 16, fontWeight: 600, fontSize: 15 }}>
                  <NumberOutlined style={{ marginRight: 8, color: 'var(--pr-brand-1)' }} />
                  Change Transaction PIN
                </div>
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 16 }}
                  message="For your safety, changing the PIN requires your current account password."
                />
                <Form
                  form={pinForm}
                  layout="vertical"
                  onFinish={handlePinSave}
                  requiredMark={false}
                >
                  <Form.Item
                    label="Current account password"
                    name="currentPassword"
                    rules={[{ required: true, message: 'Enter your account password' }]}
                  >
                    <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
                  </Form.Item>
                  <Form.Item
                    label="New PIN"
                    name="newPin"
                    rules={[
                      { required: true, message: 'Choose a new PIN' },
                      { pattern: /^\d{4,6}$/, message: 'PIN must be 4 to 6 digits' },
                    ]}
                  >
                    <Input.Password
                      prefix={<NumberOutlined />}
                      placeholder="4-6 digits"
                      maxLength={6}
                      inputMode="numeric"
                      autoComplete="off"
                    />
                  </Form.Item>
                  <Form.Item
                    label="Confirm new PIN"
                    name="confirmPin"
                    dependencies={['newPin']}
                    rules={[
                      { required: true, message: 'Re-enter the new PIN' },
                      ({ getFieldValue }) => ({
                        validator(_, value) {
                          if (!value || value === getFieldValue('newPin')) return Promise.resolve();
                          return Promise.reject(new Error('PINs do not match'));
                        },
                      }),
                    ]}
                  >
                    <Input.Password
                      prefix={<NumberOutlined />}
                      maxLength={6}
                      inputMode="numeric"
                      autoComplete="off"
                    />
                  </Form.Item>
                  <Button
                    type="primary"
                    htmlType="submit"
                    icon={<NumberOutlined />}
                    loading={pinSaving || isLoading}
                  >
                    Update PIN
                  </Button>
                </Form>
              </>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}
