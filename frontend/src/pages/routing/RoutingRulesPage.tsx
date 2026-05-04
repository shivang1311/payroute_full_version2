import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Card, Tag, Button, Space, message, Modal, Form,
  Input, Select, InputNumber, Switch, Popconfirm, Typography,
} from 'antd';
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { routingRuleApi } from '../../api/routing.api';
import type { RoutingRule, RailType } from '../../types';

const { TextArea } = Input;
const { Text } = Typography;

const railColorMap: Record<RailType, string> = {
  NEFT: 'blue',
  RTGS: 'purple',
  IMPS: 'green',
  ACH: 'cyan',
  WIRE: 'orange',
  BOOK: 'default',
};

const railOptions: RailType[] = ['BOOK', 'NEFT', 'RTGS', 'IMPS', 'ACH', 'WIRE'];

const RoutingRulesPage: React.FC = () => {
  const [data, setData] = useState<RoutingRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<RoutingRule | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchRules = useCallback(async () => {
    setLoading(true);
    try {
      const response = await routingRuleApi.getAll({ page, size: pageSize });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load routing rules');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchRules();
  }, [fetchRules]);

  const openCreateModal = () => {
    setEditingRule(null);
    form.resetFields();
    form.setFieldsValue({ active: true, priority: 1 });
    setModalOpen(true);
  };

  const openEditModal = (record: RoutingRule) => {
    setEditingRule(record);
    form.setFieldsValue({
      name: record.name,
      description: record.description,
      conditionJson: record.conditionJson,
      preferredRail: record.preferredRail,
      priority: record.priority,
      active: record.active,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (editingRule) {
        await routingRuleApi.update(editingRule.id, values);
        message.success('Routing rule updated');
      } else {
        await routingRuleApi.create(values);
        message.success('Routing rule created');
      }
      setModalOpen(false);
      fetchRules();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'errorFields' in err) return;
      message.error('Failed to save routing rule');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await routingRuleApi.delete(id);
      message.success('Routing rule deleted');
      fetchRules();
    } catch {
      message.error('Failed to delete routing rule');
    }
  };

  const columns: ColumnsType<RoutingRule> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: 'Name', dataIndex: 'name', key: 'name' },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (val: string | undefined) => val || <Text type="secondary">-</Text>,
    },
    {
      title: 'Preferred Rail',
      dataIndex: 'preferredRail',
      key: 'preferredRail',
      render: (rail: RailType) => <Tag color={railColorMap[rail]}>{rail}</Tag>,
    },
    { title: 'Priority', dataIndex: 'priority', key: 'priority', width: 100, align: 'center' },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 100,
      render: (active: boolean) => (
        <Tag color={active ? 'green' : 'default'}>{active ? 'Yes' : 'No'}</Tag>
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
      width: 120,
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => openEditModal(record)} />
          <Popconfirm
            title="Delete this rule?"
            onConfirm={() => handleDelete(record.id)}
            okText="Yes"
            cancelText="No"
          >
            <Button type="link" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="Routing Rules"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchRules}>Refresh</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
            Add Rule
          </Button>
        </Space>
      }
    >
      <Table<RoutingRule>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} rules`,
          onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
        }}
      />

      <Modal
        title={editingRule ? 'Edit Routing Rule' : 'Add Routing Rule'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Name is required' }]}>
            <Input placeholder="Enter rule name" />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input placeholder="Optional description" />
          </Form.Item>
          <Form.Item
            name="conditionJson"
            label="Condition JSON"
            rules={[{ required: true, message: 'Condition JSON is required' }]}
          >
            <TextArea rows={4} placeholder='{"amount_gt": 100000, "currency": "INR"}' />
          </Form.Item>
          <Form.Item
            name="preferredRail"
            label="Preferred Rail"
            rules={[{ required: true, message: 'Preferred rail is required' }]}
          >
            <Select
              placeholder="Select rail"
              options={railOptions.map((r) => ({ label: r, value: r }))}
            />
          </Form.Item>
          <Form.Item
            name="priority"
            label="Priority"
            rules={[{ required: true, message: 'Priority is required' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default RoutingRulesPage;
