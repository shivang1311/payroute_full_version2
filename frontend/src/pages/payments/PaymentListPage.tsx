import React, { useEffect, useState, useCallback } from 'react';
import { Table, Card, Tag, Select, Button, Space, message } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { paymentApi } from '../../api/payment.api';
import { useAuthStore } from '../../stores/authStore';
import { paymentRef } from '../../utils/paymentRef';
import type { PaymentOrder, PaymentStatus, InitiationChannel } from '../../types';

const statusColorMap: Record<PaymentStatus, string> = {
  INITIATED: 'default',
  VALIDATED: 'cyan',
  VALIDATION_FAILED: 'red',
  SCREENING: 'geekblue',
  HELD: 'orange',
  ROUTED: 'blue',
  PROCESSING: 'processing',
  COMPLETED: 'green',
  FAILED: 'red',
  CANCELLED: 'volcano',
  REVERSED: 'purple',
};

const channelColorMap: Record<InitiationChannel, string> = {
  BRANCH: 'volcano',
  MOBILE: 'blue',
  ONLINE: 'cyan',
  API: 'geekblue',
};

const PaymentListPage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const isCustomer = user?.role === 'CUSTOMER';
  const [data, setData] = useState<PaymentOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [channelFilter, setChannelFilter] = useState<string | undefined>();

  const fetchPayments = useCallback(async () => {
    setLoading(true);
    try {
      const response = await paymentApi.getAll({
        page,
        size: pageSize,
        status: statusFilter,
        channel: channelFilter,
      });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load payments');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statusFilter, channelFilter]);

  useEffect(() => {
    fetchPayments();
  }, [fetchPayments]);

  const columns: ColumnsType<PaymentOrder> = [
    {
      title: 'Reference No',
      key: 'ref',
      width: isCustomer ? 150 : 200,
      render: (_, record) => {
        const ref = paymentRef(record);
        return (
          <span style={{ fontFamily: 'monospace', fontSize: 12 }}>
            {isCustomer ? ref : `#${record.id} · ${ref}`}
          </span>
        );
      },
    },
    {
      title: 'Amount',
      key: 'amount',
      render: (_, record) =>
        `${record.currency} ${record.amount.toLocaleString(undefined, {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        })}`,
      align: 'right',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: PaymentStatus) => (
        <Tag color={statusColorMap[status]}>{status}</Tag>
      ),
    },
    {
      title: 'Channel',
      dataIndex: 'initiationChannel',
      key: 'initiationChannel',
      render: (channel: InitiationChannel) => (
        <Tag color={channelColorMap[channel]}>{channel}</Tag>
      ),
    },
    {
      title: 'Created At',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (val: string) => dayjs(val).format('YYYY-MM-DD HH:mm'),
    },
  ];

  const statusOptions: PaymentStatus[] = [
    'INITIATED', 'VALIDATED', 'VALIDATION_FAILED', 'SCREENING',
    'HELD', 'ROUTED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED',
  ];

  const channelOptions: InitiationChannel[] = ['BRANCH', 'MOBILE', 'ONLINE', 'API'];

  return (
    <Card
      title="Payments"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchPayments}>
            Refresh
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/payments/new')}
          >
            New Payment
          </Button>
        </Space>
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
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
        <Select
          placeholder="Filter by Channel"
          allowClear
          style={{ width: 180 }}
          value={channelFilter}
          onChange={(val) => {
            setChannelFilter(val);
            setPage(0);
          }}
          options={channelOptions.map((c) => ({ label: c, value: c }))}
        />
      </Space>

      <Table<PaymentOrder>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        onRow={(record) => ({
          onClick: () => navigate(`/payments/${record.id}`),
          style: { cursor: 'pointer' },
        })}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} payments`,
          onChange: (p, ps) => {
            setPage(p - 1);
            setPageSize(ps);
          },
        }}
      />
    </Card>
  );
};

export default PaymentListPage;
