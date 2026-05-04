import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Card, Tag, Select, Button, Space, message, Modal, Form, Input, Popconfirm,
} from 'antd';
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { partyApi } from '../../api/party.api';
import type { Party, PartyType, PartyStatus } from '../../types';

const statusColorMap: Record<PartyStatus, string> = {
  ACTIVE: 'green',
  INACTIVE: 'default',
  SUSPENDED: 'red',
};

const typeColorMap: Record<PartyType, string> = {
  INDIVIDUAL: 'blue',
  CORPORATE: 'purple',
};

const PartyListPage: React.FC = () => {
  const [data, setData] = useState<Party[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [typeFilter, setTypeFilter] = useState<string | undefined>();
  const [statusFilter, setStatusFilter] = useState<string | undefined>();

  const [modalOpen, setModalOpen] = useState(false);
  const [editingParty, setEditingParty] = useState<Party | null>(null);
  const [modalLoading, setModalLoading] = useState(false);
  const [form] = Form.useForm();

  const fetchParties = useCallback(async () => {
    setLoading(true);
    try {
      const response = await partyApi.getAll({
        page,
        size: pageSize,
        type: typeFilter,
        status: statusFilter,
      });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load parties');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, typeFilter, statusFilter]);

  useEffect(() => {
    fetchParties();
  }, [fetchParties]);

  const openAddModal = () => {
    setEditingParty(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEditModal = (party: Party) => {
    setEditingParty(party);
    form.resetFields();
    setModalOpen(true);
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      setModalLoading(true);
      if (editingParty) {
        await partyApi.update(editingParty.id, values);
        message.success('Party updated successfully');
      } else {
        await partyApi.create(values);
        message.success('Party created successfully');
      }
      setModalOpen(false);
      fetchParties();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'errorFields' in err) return; // form validation
      message.error('Failed to save party');
    } finally {
      setModalLoading(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await partyApi.delete(id);
      message.success('Party deleted');
      fetchParties();
    } catch {
      message.error('Failed to delete party');
    }
  };

  const columns: ColumnsType<Party> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      render: (val: PartyType) => <Tag color={typeColorMap[val]}>{val}</Tag>,
    },
    { title: 'Phone', dataIndex: 'phone', key: 'phone', render: (v: string) => v || '-' },
    { title: 'Country', dataIndex: 'country', key: 'country' },
    { title: 'Risk Rating', dataIndex: 'riskRating', key: 'riskRating' },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (val: PartyStatus) => <Tag color={statusColorMap[val]}>{val}</Tag>,
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
      width: 120,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            size="small"
            onClick={() => openEditModal(record)}
          />
          <Popconfirm
            title="Delete this party?"
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

  const typeOptions: PartyType[] = ['INDIVIDUAL', 'CORPORATE'];
  const statusOptions: PartyStatus[] = ['ACTIVE', 'INACTIVE', 'SUSPENDED'];

  return (
    <>
      <Card
        title="Parties"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchParties}>
              Refresh
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openAddModal}>
              Add Party
            </Button>
          </Space>
        }
      >
        <Space style={{ marginBottom: 16 }} wrap>
          <Select
            placeholder="Filter by Type"
            allowClear
            style={{ width: 180 }}
            value={typeFilter}
            onChange={(val) => {
              setTypeFilter(val);
              setPage(0);
            }}
            options={typeOptions.map((t) => ({ label: t, value: t }))}
          />
          <Select
            placeholder="Filter by Status"
            allowClear
            style={{ width: 180 }}
            value={statusFilter}
            onChange={(val) => {
              setStatusFilter(val);
              setPage(0);
            }}
            options={statusOptions.map((s) => ({ label: s, value: s }))}
          />
        </Space>

        <Table<Party>
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `Total ${t} parties`,
            onChange: (p, ps) => {
              setPage(p - 1);
              setPageSize(ps);
            },
          }}
        />
      </Card>

      <Modal
        title={editingParty ? 'Edit Party' : 'Add Party'}
        open={modalOpen}
        onOk={handleModalOk}
        onCancel={() => setModalOpen(false)}
        confirmLoading={modalLoading}
        afterOpenChange={(open) => {
          if (open && editingParty) {
            form.setFieldsValue({
              name: editingParty.name,
              type: editingParty.type,
              phone: editingParty.phone,
              country: editingParty.country,
              riskRating: editingParty.riskRating,
              status: editingParty.status,
            });
          }
        }}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="Name"
            name="name"
            rules={[{ required: true, message: 'Please enter party name' }]}
          >
            <Input placeholder="Party name" />
          </Form.Item>
          <Form.Item
            label="Type"
            name="type"
            rules={[{ required: true, message: 'Please select type' }]}
          >
            <Select
              placeholder="Select type"
              options={typeOptions.map((t) => ({ label: t, value: t }))}
            />
          </Form.Item>
          <Form.Item
            label="Phone"
            name="phone"
            rules={[
              { len: 10, message: 'Phone must be exactly 10 digits' },
              { pattern: /^\d{10}$/, message: 'Phone must contain only digits' },
            ]}
          >
            <Input placeholder="10-digit phone number" maxLength={10} />
          </Form.Item>
          <Form.Item
            label="Country"
            name="country"
            rules={[{ required: true, message: 'Please enter country' }]}
          >
            <Input placeholder="e.g. IN, US, GB" />
          </Form.Item>
          <Form.Item
            label="Risk Rating"
            name="riskRating"
            rules={[{ required: true, message: 'Please enter risk rating' }]}
          >
            <Input placeholder="e.g. LOW, MEDIUM, HIGH" />
          </Form.Item>
          {editingParty && (
            <Form.Item label="Status" name="status">
              <Select
                options={statusOptions.map((s) => ({ label: s, value: s }))}
              />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </>
  );
};

export default PartyListPage;
