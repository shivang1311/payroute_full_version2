import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Card, Tag, Button, Space, message, Modal, Form,
  Input, Select, InputNumber, Switch, DatePicker,
} from 'antd';
import { PlusOutlined, ReloadOutlined, EditOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { feeScheduleApi } from '../../api/ledger.api';
import type { FeeSchedule, RailType, FeeType } from '../../types';

const railColorMap: Record<RailType, string> = {
  NEFT: 'blue',
  RTGS: 'purple',
  IMPS: 'green',
  ACH: 'cyan',
  WIRE: 'orange',
  BOOK: 'default',
};

const feeTypeColorMap: Record<FeeType, string> = {
  FLAT: 'blue',
  PERCENTAGE: 'geekblue',
};

const railOptions: RailType[] = ['BOOK', 'NEFT', 'RTGS', 'IMPS', 'ACH', 'WIRE'];
const feeTypeOptions: FeeType[] = ['FLAT', 'PERCENTAGE'];

const FeeSchedulePage: React.FC = () => {
  const [data, setData] = useState<FeeSchedule[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingFee, setEditingFee] = useState<FeeSchedule | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchFees = useCallback(async () => {
    setLoading(true);
    try {
      const response = await feeScheduleApi.getAll({ page, size: pageSize });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load fee schedules');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchFees();
  }, [fetchFees]);

  const openCreateModal = () => {
    setEditingFee(null);
    form.resetFields();
    form.setFieldsValue({ active: true, currency: 'INR' });
    setModalOpen(true);
  };

  const openEditModal = (record: FeeSchedule) => {
    setEditingFee(record);
    form.setFieldsValue({
      product: record.product,
      rail: record.rail,
      feeType: record.feeType,
      value: record.value,
      minFee: record.minFee,
      maxFee: record.maxFee,
      currency: record.currency,
      effectiveFrom: dayjs(record.effectiveFrom),
      effectiveTo: record.effectiveTo ? dayjs(record.effectiveTo) : undefined,
      active: record.active,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const payload = {
        ...values,
        effectiveFrom: values.effectiveFrom.format('YYYY-MM-DD'),
        effectiveTo: values.effectiveTo ? values.effectiveTo.format('YYYY-MM-DD') : undefined,
      };
      setSubmitting(true);
      if (editingFee) {
        await feeScheduleApi.update(editingFee.id, payload);
        message.success('Fee schedule updated');
      } else {
        await feeScheduleApi.create(payload);
        message.success('Fee schedule created');
      }
      setModalOpen(false);
      fetchFees();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'errorFields' in err) return;
      message.error('Failed to save fee schedule');
    } finally {
      setSubmitting(false);
    }
  };

  const columns: ColumnsType<FeeSchedule> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
    { title: 'Product', dataIndex: 'product', key: 'product' },
    {
      title: 'Rail',
      dataIndex: 'rail',
      key: 'rail',
      render: (rail: RailType) => <Tag color={railColorMap[rail]}>{rail}</Tag>,
    },
    {
      title: 'Fee Type',
      dataIndex: 'feeType',
      key: 'feeType',
      render: (type: FeeType) => <Tag color={feeTypeColorMap[type]}>{type}</Tag>,
    },
    {
      title: 'Value',
      dataIndex: 'value',
      key: 'value',
      align: 'right',
      render: (val: number) => val.toLocaleString(undefined, { minimumFractionDigits: 2 }),
    },
    {
      title: 'Min Fee',
      dataIndex: 'minFee',
      key: 'minFee',
      align: 'right',
      render: (val: number) => val.toLocaleString(undefined, { minimumFractionDigits: 2 }),
    },
    {
      title: 'Max Fee',
      dataIndex: 'maxFee',
      key: 'maxFee',
      align: 'right',
      render: (val?: number) =>
        val != null ? val.toLocaleString(undefined, { minimumFractionDigits: 2 }) : '-',
    },
    { title: 'Currency', dataIndex: 'currency', key: 'currency', width: 90 },
    {
      title: 'Effective From',
      dataIndex: 'effectiveFrom',
      key: 'effectiveFrom',
      render: (val: string) => dayjs(val).format('YYYY-MM-DD'),
    },
    {
      title: 'Effective To',
      dataIndex: 'effectiveTo',
      key: 'effectiveTo',
      render: (val?: string) => val ? dayjs(val).format('YYYY-MM-DD') : '-',
    },
    {
      title: 'Active',
      dataIndex: 'active',
      key: 'active',
      width: 80,
      render: (active: boolean) => (
        <Tag color={active ? 'green' : 'default'}>{active ? 'Yes' : 'No'}</Tag>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 80,
      render: (_, record) => (
        <Button type="link" icon={<EditOutlined />} onClick={() => openEditModal(record)} />
      ),
    },
  ];

  return (
    <Card
      title="Fee Schedules"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchFees}>Refresh</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
            Add Fee
          </Button>
        </Space>
      }
    >
      <Table<FeeSchedule>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} fee schedules`,
          onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
        }}
      />

      <Modal
        title={editingFee ? 'Edit Fee Schedule' : 'Add Fee Schedule'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        confirmLoading={submitting}
        destroyOnClose
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="product" label="Product" rules={[{ required: true, message: 'Product is required' }]}>
            <Input placeholder="e.g. DOMESTIC_TRANSFER" />
          </Form.Item>
          <Form.Item name="rail" label="Rail" rules={[{ required: true, message: 'Rail is required' }]}>
            <Select
              placeholder="Select rail"
              options={railOptions.map((r) => ({ label: r, value: r }))}
            />
          </Form.Item>
          <Form.Item name="feeType" label="Fee Type" rules={[{ required: true, message: 'Fee type is required' }]}>
            <Select
              placeholder="Select fee type"
              options={feeTypeOptions.map((f) => ({ label: f, value: f }))}
            />
          </Form.Item>
          <Form.Item name="value" label="Value" rules={[{ required: true, message: 'Value is required' }]}>
            <InputNumber min={0} step={0.01} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="minFee" label="Min Fee" rules={[{ required: true, message: 'Min fee is required' }]}>
            <InputNumber min={0} step={0.01} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="maxFee" label="Max Fee">
            <InputNumber min={0} step={0.01} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="currency" label="Currency" rules={[{ required: true, message: 'Currency is required' }]}>
            <Input placeholder="INR" />
          </Form.Item>
          <Form.Item name="effectiveFrom" label="Effective From" rules={[{ required: true, message: 'Effective from is required' }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="effectiveTo" label="Effective To">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default FeeSchedulePage;
