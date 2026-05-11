import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Card, Tag, Button, Space, message, Modal, Form, Input, InputNumber, Select, Popconfirm, Alert,
} from 'antd';
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { accountApi } from '../../api/party.api';
import { useAuthStore } from '../../stores/authStore';
import type { Account, AliasType } from '../../types';
import { SearchOutlined } from '@ant-design/icons';
import { EMAIL_PATTERN, EMAIL_VALIDATION_MESSAGE } from '../../utils/emailValidation';

const accountTypeOptions = [
  'SAVINGS', 'CURRENT', 'SALARY', 'NRE', 'NRO', 'OVERDRAFT', 'LOAN', 'FIXED_DEPOSIT',
];
const currencyOptions = ['INR', 'USD', 'EUR', 'GBP', 'AED', 'SGD', 'JPY', 'CAD', 'AUD', 'CHF'];

const IFSC_REGEX = /^[A-Z]{4}0[A-Z0-9]{6}$/;
const UPI_REGEX = /^[a-zA-Z0-9.\-]{2,256}@[a-zA-Z]{2,64}$/;

const AccountDirectoryPage: React.FC = () => {
  const { user } = useAuthStore();
  const isCustomer = user?.role === 'CUSTOMER';

  const [data, setData] = useState<Account[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [partyIdFilter, setPartyIdFilter] = useState<number | undefined>();

  const [modalOpen, setModalOpen] = useState(false);
  const [editingAccount, setEditingAccount] = useState<Account | null>(null);
  const [modalLoading, setModalLoading] = useState(false);
  const [form] = Form.useForm();

  // Alias resolver state
  const [aliasType, setAliasType] = useState<AliasType>('VPA');
  const [aliasValue, setAliasValue] = useState('');
  const [resolving, setResolving] = useState(false);
  const [resolvedAccount, setResolvedAccount] = useState<Account | null>(null);

  // Customer's party ID from login (stored on user record)
  const myPartyId = isCustomer ? (user?.partyId ?? null) : null;

  // Watch currency for conditional rendering
  const currencyValue = Form.useWatch('currency', form);

  // Set party filter for customer on mount
  useEffect(() => {
    if (isCustomer && myPartyId) {
      setPartyIdFilter(myPartyId);
    }
  }, [isCustomer, myPartyId]);

  const fetchAccounts = useCallback(async () => {
    setLoading(true);
    try {
      const response = await accountApi.getAll({
        page,
        size: pageSize,
        partyId: isCustomer ? (myPartyId ?? undefined) : partyIdFilter,
      });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load accounts');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, partyIdFilter, isCustomer, myPartyId]);

  useEffect(() => {
    if (isCustomer && !myPartyId) return; // Wait for party to load
    fetchAccounts();
  }, [fetchAccounts, isCustomer, myPartyId]);

  const openAddModal = () => {
    setEditingAccount(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEditModal = (account: Account) => {
    setEditingAccount(account);
    form.resetFields();
    setModalOpen(true);
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      // Ensure partyId is always set for customer (hidden field may lose value due to destroyOnClose)
      if (isCustomer && myPartyId) {
        values.partyId = myPartyId;
      }
      setModalLoading(true);
      if (editingAccount) {
        await accountApi.update(editingAccount.id, values);
        message.success('Account updated successfully');
      } else {
        await accountApi.create(values);
        message.success('Account created successfully');
      }
      setModalOpen(false);
      fetchAccounts();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'errorFields' in err) return;
      const e = err as { response?: { data?: { message?: string } } };
      message.error(e?.response?.data?.message || 'Failed to save account');
    } finally {
      setModalLoading(false);
    }
  };

  const handleResolve = async () => {
    if (!aliasValue.trim()) {
      message.warning('Enter an alias value to resolve');
      return;
    }
    setResolving(true);
    setResolvedAccount(null);
    try {
      const { data } = await accountApi.resolve(aliasType, aliasValue.trim());
      setResolvedAccount(data.data);
      message.success(
        isCustomer
          ? `Resolved ${aliasType} to ${data.data.partyName || data.data.accountNumber}`
          : `Resolved ${aliasType} to account #${data.data.id}`,
      );
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } } };
      message.error(e?.response?.data?.message || `No account found for that ${aliasType}`);
    } finally {
      setResolving(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await accountApi.delete(id);
      message.success('Account deleted');
      fetchAccounts();
    } catch {
      message.error('Failed to delete account');
    }
  };

  const handleCurrencyChange = () => {
    const ifscIban = form.getFieldValue('ifscIban');
    const vpaUpiId = form.getFieldValue('vpaUpiId');
    if (ifscIban) form.validateFields(['ifscIban']);
    if (vpaUpiId) form.validateFields(['vpaUpiId']);
  };

  const validateIfscIban = (_: unknown, value: string) => {
    if (!value) {
      return Promise.reject(new Error('IFSC/IBAN is required'));
    }
    const currency = form.getFieldValue('currency');
    if (currency === 'INR') {
      if (!IFSC_REGEX.test(value.toUpperCase())) {
        return Promise.reject(
          new Error('Invalid IFSC: must be 4 letters + 0 + 6 alphanumeric (e.g. SBIN0001234)')
        );
      }
    }
    return Promise.resolve();
  };

  const validateVpaUpiId = (_: unknown, value: string) => {
    // Customers must register both an IFSC and a UPI/VPA so beneficiary lookups
    // by VPA work end-to-end. Staff users may leave VPA empty.
    if (!value || value.trim() === '') {
      if (isCustomer) {
        const currency = form.getFieldValue('currency');
        return Promise.reject(
          new Error(currency === 'INR' ? 'UPI ID is required' : 'VPA ID is required')
        );
      }
      return Promise.resolve();
    }
    const currency = form.getFieldValue('currency');
    if (currency === 'INR') {
      if (!UPI_REGEX.test(value)) {
        return Promise.reject(
          new Error('Invalid UPI ID: format must be name@handle (e.g. user@okbank)')
        );
      }
    } else {
      if (!value.includes('@') || value.length < 5) {
        return Promise.reject(
          new Error('Invalid VPA ID: format must be name@handle')
        );
      }
    }
    return Promise.resolve();
  };

  const columns: ColumnsType<Account> = [
    // Internal id is staff-only — customers don't see it
    ...(!isCustomer ? [{ title: 'Account ID', dataIndex: 'id', key: 'id', width: 90 }] as ColumnsType<Account> : []),
    ...(!isCustomer ? [{ title: 'Party Name', dataIndex: 'partyName', key: 'partyName', width: 140, ellipsis: true }] : []),
    { title: 'Account Number', dataIndex: 'accountNumber', key: 'accountNumber', width: 170 },
    { title: 'IFSC/IBAN', dataIndex: 'ifscIban', key: 'ifscIban', width: 130 },
    {
      title: 'UPI/VPA ID',
      dataIndex: 'vpaUpiId',
      key: 'vpaUpiId',
      width: 170,
      ellipsis: true,
      render: (v: string) => v || '-',
    },
    {
      title: 'Phone',
      dataIndex: 'phone',
      key: 'phone',
      width: 130,
      render: (v: string) => v || '-',
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
      width: 180,
      ellipsis: true,
      render: (v: string) => v || '-',
    },
    { title: 'Currency', dataIndex: 'currency', key: 'currency', width: 90 },
    { title: 'Account Type', dataIndex: 'accountType', key: 'accountType', width: 130 },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 80,
      render: (val: boolean) => (
        <Tag color={val ? 'green' : 'red'}>{val ? 'Yes' : 'No'}</Tag>
      ),
    },
    {
      title: 'Created At',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (val: string) => (
        <span style={{ whiteSpace: 'nowrap' }}>
          {dayjs(val).format('YYYY-MM-DD HH:mm')}
        </span>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 100,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            size="small"
            onClick={() => openEditModal(record)}
          />
          <Popconfirm
            title="Delete this account?"
            description="This action cannot be undone."
            onConfirm={() => handleDelete(record.id)}
            okText="Yes"
            cancelText="No"
          >
            <Button type="link" danger icon={<DeleteOutlined />} size="small" />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      {isCustomer && !myPartyId && (
        <Alert
          message="No party linked to your account. Please contact admin."
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      <Card
        title={isCustomer ? 'My Accounts' : 'Account Directory'}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchAccounts}>
              Refresh
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={openAddModal}
              disabled={isCustomer && !myPartyId}
            >
              Add Account
            </Button>
          </Space>
        }
      >
        {!isCustomer && (
          <Space style={{ marginBottom: 16 }} wrap>
            <InputNumber
              placeholder="Filter by Party ID"
              style={{ width: 200 }}
              min={1}
              value={partyIdFilter}
              onChange={(val) => {
                setPartyIdFilter(val ?? undefined);
                setPage(0);
              }}
              allowClear
            />
          </Space>
        )}

        {/* Alias resolver — resolve an account by VPA, phone, email, or name */}
        <Space.Compact style={{ marginBottom: 8, width: '100%', maxWidth: 720 }}>
          <Select
            style={{ width: 120 }}
            value={aliasType}
            onChange={(v) => setAliasType(v)}
            options={[
              { label: 'VPA / UPI', value: 'VPA' },
              { label: 'Phone', value: 'PHONE' },
              { label: 'Email', value: 'EMAIL' },
              { label: 'Name', value: 'NAME' },
            ]}
          />
          <Input
            placeholder={
              aliasType === 'VPA' ? 'e.g. alice@okhdfc'
                : aliasType === 'PHONE' ? 'e.g. +919876543210'
                : aliasType === 'EMAIL' ? 'e.g. alice@example.com'
                : 'e.g. main-ops-account'
            }
            value={aliasValue}
            onChange={(e) => setAliasValue(e.target.value)}
            onPressEnter={handleResolve}
            allowClear
          />
          <Button
            type="primary"
            icon={<SearchOutlined />}
            loading={resolving}
            onClick={handleResolve}
          >
            Resolve
          </Button>
        </Space.Compact>
        {resolvedAccount && (
          <Alert
            style={{ marginBottom: 16 }}
            type="success"
            showIcon
            closable
            onClose={() => setResolvedAccount(null)}
            message={
              isCustomer
                ? `Resolved to ${resolvedAccount.partyName || resolvedAccount.accountNumber}`
                : `Resolved to account #${resolvedAccount.id}`
            }
            description={
              <>
                <div>
                  <strong>Party:</strong> {resolvedAccount.partyName}
                  {!isCustomer && ` (id ${resolvedAccount.partyId})`}
                </div>
                <div><strong>Account:</strong> {resolvedAccount.accountNumber} · {resolvedAccount.ifscIban} · {resolvedAccount.currency}</div>
                <div><strong>VPA:</strong> {resolvedAccount.vpaUpiId || '—'} · <strong>Phone:</strong> {resolvedAccount.phone || '—'} · <strong>Email:</strong> {resolvedAccount.email || '—'}</div>
              </>
            }
          />
        )}

        <Table<Account>
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          scroll={{ x: 'max-content' }}
          size="middle"
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `Total ${t} accounts`,
            onChange: (p, ps) => {
              setPage(p - 1);
              setPageSize(ps);
            },
          }}
        />
      </Card>

      <Modal
        title={editingAccount ? 'Edit Account' : 'Add Account'}
        open={modalOpen}
        onOk={handleModalOk}
        onCancel={() => setModalOpen(false)}
        confirmLoading={modalLoading}
        afterOpenChange={(open) => {
          if (open && editingAccount) {
            form.setFieldsValue({
              partyId: editingAccount.partyId,
              accountNumber: editingAccount.accountNumber,
              ifscIban: editingAccount.ifscIban,
              alias: editingAccount.alias,
              currency: editingAccount.currency,
              accountType: editingAccount.accountType,
              vpaUpiId: editingAccount.vpaUpiId,
              phone: editingAccount.phone,
              email: editingAccount.email,
              active: editingAccount.active,
            });
          } else if (open && isCustomer && myPartyId) {
            form.setFieldsValue({ partyId: myPartyId });
          }
        }}
      >
        <Form form={form} layout="vertical">
          {/* Show Party ID field only for ADMIN/OPERATIONS */}
          {!isCustomer ? (
            <Form.Item
              label="Party ID"
              name="partyId"
              rules={[{ required: true, message: 'Please enter party ID' }]}
            >
              <InputNumber
                style={{ width: '100%' }}
                min={1}
                placeholder="Enter party ID"
                disabled={!!editingAccount}
              />
            </Form.Item>
          ) : (
            <Form.Item name="partyId" hidden>
              <InputNumber />
            </Form.Item>
          )}

          <Form.Item
            label="Account Number"
            name="accountNumber"
            rules={[
              { required: true, message: 'Account number is required' },
              { len: 16, message: 'Account number must be exactly 16 digits' },
              { pattern: /^\d{16}$/, message: 'Account number must contain only digits' },
            ]}
          >
            <Input placeholder="Enter 16-digit account number" maxLength={16} />
          </Form.Item>

          <Form.Item
            label="Currency"
            name="currency"
            rules={[{ required: true, message: 'Currency is required' }]}
          >
            <Select
              placeholder="Select currency"
              options={currencyOptions.map((c) => ({ label: c, value: c }))}
              onChange={handleCurrencyChange}
            />
          </Form.Item>

          <Form.Item
            label="IFSC/IBAN"
            name="ifscIban"
            dependencies={['currency']}
            required
            rules={[{ validator: validateIfscIban }]}
            extra={
              currencyValue === 'INR'
                ? 'IFSC format: 4 letters + 0 + 6 alphanumeric (e.g. SBIN0001234)'
                : undefined
            }
          >
            <Input
              placeholder={
                currencyValue === 'INR'
                  ? 'Enter IFSC code (e.g. SBIN0001234)'
                  : 'Enter IFSC/IBAN/SWIFT code'
              }
              style={{ textTransform: 'uppercase' }}
            />
          </Form.Item>

          <Form.Item
            label={currencyValue === 'INR' ? 'UPI ID' : 'VPA ID'}
            name="vpaUpiId"
            dependencies={['currency']}
            required={isCustomer}
            rules={[{ validator: validateVpaUpiId }]}
            extra={
              currencyValue === 'INR'
                ? 'UPI format: name@handle (e.g. user@okbank, 9876543210@upi)'
                : 'VPA format: name@handle'
            }
          >
            <Input
              placeholder={
                currencyValue === 'INR'
                  ? 'Enter UPI ID (e.g. user@okbank)'
                  : 'Enter VPA ID (e.g. user@handle)'
              }
            />
          </Form.Item>

          <Form.Item
            label="Account Type"
            name="accountType"
            rules={[{ required: true, message: 'Account type is required' }]}
          >
            <Select
              placeholder="Select account type"
              options={accountTypeOptions.map((t) => ({
                label: t.replace(/_/g, ' '),
                value: t,
              }))}
            />
          </Form.Item>

          <Form.Item
            label="Phone alias"
            name="phone"
            rules={[
              {
                pattern: /^\+?[0-9\- ]{6,20}$/,
                message: 'Enter a valid phone (digits, optional leading +, 6–20 chars)',
              },
            ]}
          >
            <Input placeholder="Optional phone-number alias (e.g. +919876543210)" />
          </Form.Item>

          <Form.Item
            label="Email alias"
            name="email"
            rules={[
              { type: 'email', message: 'Enter a valid email address' },
              { pattern: EMAIL_PATTERN, message: EMAIL_VALIDATION_MESSAGE },
            ]}
          >
            <Input placeholder="Optional email alias (e.g. ops@example.com)" />
          </Form.Item>

          <Form.Item label="Alias" name="alias">
            <Input placeholder="Optional friendly name (e.g. My Salary Account)" />
          </Form.Item>

          {editingAccount && (
            <Form.Item label="Active" name="active">
              <Select
                options={[
                  { label: 'Yes', value: true },
                  { label: 'No', value: false },
                ]}
              />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </>
  );
};

export default AccountDirectoryPage;
