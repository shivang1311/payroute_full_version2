import React, { useEffect, useState, useCallback } from 'react';
import { Table, Card, Tag, Space, Button, Modal, Select, Popconfirm, message, Form, Input } from 'antd';
import { ReloadOutlined, UserAddOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { userApi } from '../../api/auth.api';
import type { User, Role } from '../../types';

const roleColorMap: Record<Role, string> = {
  ADMIN: 'red',
  OPERATIONS: 'blue',
  COMPLIANCE: 'purple',
  RECONCILIATION: 'cyan',
  CUSTOMER: 'default',
};

const allRoles: Role[] = ['CUSTOMER', 'OPERATIONS', 'COMPLIANCE', 'RECONCILIATION', 'ADMIN'];

const UserManagementPage: React.FC = () => {
  const [data, setData] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);

  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [newRole, setNewRole] = useState<Role | undefined>();
  const [updatingRole, setUpdatingRole] = useState(false);

  const [staffModalOpen, setStaffModalOpen] = useState(false);
  const [staffForm] = Form.useForm();
  const [creatingStaff, setCreatingStaff] = useState(false);

  const handleCreateStaff = async () => {
    const values = await staffForm.validateFields();
    setCreatingStaff(true);
    try {
      await userApi.createStaff(values);
      message.success(`${values.role} user "${values.username}" created`);
      setStaffModalOpen(false);
      staffForm.resetFields();
      fetchData();
    } catch (e: unknown) {
      const err = e as { response?: { status?: number; data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Failed to create staff user');
    } finally {
      setCreatingStaff(false);
    }
  };

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await userApi.getAll(page, pageSize);
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load users');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const openRoleModal = (user: User) => {
    setSelectedUser(user);
    setNewRole(user.role);
    setRoleModalOpen(true);
  };

  const handleUpdateRole = async () => {
    if (!selectedUser || !newRole) return;
    setUpdatingRole(true);
    try {
      await userApi.updateRole(selectedUser.id, newRole);
      message.success('Role updated successfully');
      setRoleModalOpen(false);
      setSelectedUser(null);
      setNewRole(undefined);
      fetchData();
    } catch {
      message.error('Failed to update role');
    } finally {
      setUpdatingRole(false);
    }
  };

  const handleDeactivate = async (id: number) => {
    try {
      await userApi.deactivate(id);
      message.success('User deactivated successfully');
      fetchData();
    } catch {
      message.error('Failed to deactivate user');
    }
  };

  const columns: ColumnsType<User> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
    { title: 'Username', dataIndex: 'username', key: 'username' },
    { title: 'Email', dataIndex: 'email', key: 'email', ellipsis: true },
    { title: 'Phone', dataIndex: 'phone', key: 'phone', render: (val?: string) => val || '-' },
    {
      title: 'Role',
      dataIndex: 'role',
      key: 'role',
      render: (r: Role) => <Tag color={roleColorMap[r]}>{r}</Tag>,
    },
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
      render: (val: string) => dayjs(val).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 200,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => openRoleModal(record)}>
            Change Role
          </Button>
          {record.active && (
            <Popconfirm
              title="Deactivate user?"
              description={`This will deactivate ${record.username}.`}
              onConfirm={() => handleDeactivate(record.id)}
              okText="Yes"
              cancelText="No"
            >
              <Button type="link" size="small" danger>
                Deactivate
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="User Management"
      extra={
        <Space>
          <Button
            type="primary"
            icon={<UserAddOutlined />}
            onClick={() => setStaffModalOpen(true)}
          >
            New Staff User
          </Button>
          <Button icon={<ReloadOutlined />} onClick={fetchData}>
            Refresh
          </Button>
        </Space>
      }
    >
      <Table<User>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} users`,
          onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
        }}
      />

      <Modal
        title={`Change Role - ${selectedUser?.username}`}
        open={roleModalOpen}
        onOk={handleUpdateRole}
        onCancel={() => { setRoleModalOpen(false); setSelectedUser(null); }}
        confirmLoading={updatingRole}
        okText="Update"
      >
        <div style={{ marginBottom: 8 }}>Select new role:</div>
        <Select
          style={{ width: '100%' }}
          value={newRole}
          onChange={setNewRole}
          options={allRoles.map((r) => ({ label: r, value: r }))}
        />
      </Modal>

      <Modal
        title="Create Staff User"
        open={staffModalOpen}
        onOk={handleCreateStaff}
        onCancel={() => { setStaffModalOpen(false); staffForm.resetFields(); }}
        confirmLoading={creatingStaff}
        okText="Create"
        destroyOnClose
      >
        <p style={{ color: 'var(--pr-text-muted)', marginBottom: 16, fontSize: 13 }}>
          Customers self-register via the public sign-up page. Use this form to provision an
          internal staff member (Operations, Compliance or Reconciliation).
        </p>
        <Form form={staffForm} layout="vertical" requiredMark={false}>
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
            <Input placeholder="e.g. ops.sharma" autoComplete="off" />
          </Form.Item>
          <Form.Item
            label="Email"
            name="email"
            rules={[
              { required: true, message: 'Email is required' },
              { type: 'email', message: 'Invalid email' },
            ]}
          >
            <Input placeholder="user@company.com" autoComplete="off" />
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
            <Input maxLength={10} placeholder="10-digit phone" autoComplete="off" />
          </Form.Item>
          <Form.Item
            label="Temporary Password"
            name="password"
            extra="Share securely with the user — they should change it on first login."
            rules={[
              { required: true, message: 'Password is required' },
              { min: 8, message: 'Min 8 characters' },
              {
                pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z\d]).{8,}$/,
                message: 'Must include uppercase, lowercase, number & special character',
              },
            ]}
          >
            <Input.Password placeholder="e.g. Welcome@1" autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="Role"
            name="role"
            rules={[{ required: true, message: 'Pick a role' }]}
          >
            <Select
              placeholder="Select staff role"
              options={[
                { value: 'OPERATIONS', label: 'Operations Officer' },
                { value: 'COMPLIANCE', label: 'Compliance Analyst' },
                { value: 'RECONCILIATION', label: 'Reconciliation Analyst' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default UserManagementPage;
