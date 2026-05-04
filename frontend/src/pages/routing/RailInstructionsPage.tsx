import React, { useEffect, useState, useCallback } from 'react';
import { Table, Card, Tag, Select, Space, message, InputNumber, Button } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { railInstructionApi } from '../../api/routing.api';
import type { RailInstruction, RailType, RailStatus } from '../../types';

const railColorMap: Record<RailType, string> = {
  NEFT: 'blue',
  RTGS: 'purple',
  IMPS: 'green',
  ACH: 'cyan',
  WIRE: 'orange',
  BOOK: 'default',
};

const statusColorMap: Record<RailStatus, string> = {
  SETTLED: 'green',
  FAILED: 'red',
  REJECTED: 'volcano',
  PENDING: 'default',
  SENT: 'blue',
  ACKNOWLEDGED: 'cyan',
};

const railOptions: RailType[] = ['BOOK', 'NEFT', 'RTGS', 'IMPS', 'ACH', 'WIRE'];
const statusOptions: RailStatus[] = ['PENDING', 'SENT', 'ACKNOWLEDGED', 'SETTLED', 'REJECTED', 'FAILED'];

const RailInstructionsPage: React.FC = () => {
  const [data, setData] = useState<RailInstruction[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [railFilter, setRailFilter] = useState<string | undefined>();
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [paymentIdFilter, setPaymentIdFilter] = useState<number | undefined>();

  const fetchInstructions = useCallback(async () => {
    setLoading(true);
    try {
      const response = await railInstructionApi.getAll({
        page,
        size: pageSize,
        rail: railFilter,
        status: statusFilter,
        paymentId: paymentIdFilter,
      });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load rail instructions');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, railFilter, statusFilter, paymentIdFilter]);

  useEffect(() => {
    fetchInstructions();
  }, [fetchInstructions]);

  const columns: ColumnsType<RailInstruction> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: 'Payment ID', dataIndex: 'paymentId', key: 'paymentId', width: 110 },
    {
      title: 'Rail',
      dataIndex: 'rail',
      key: 'rail',
      render: (rail: RailType) => <Tag color={railColorMap[rail]}>{rail}</Tag>,
    },
    { title: 'Correlation Ref', dataIndex: 'correlationRef', key: 'correlationRef', ellipsis: true },
    {
      title: 'Status',
      dataIndex: 'railStatus',
      key: 'railStatus',
      render: (status: RailStatus) => <Tag color={statusColorMap[status]}>{status}</Tag>,
    },
    {
      title: 'Retry',
      key: 'retry',
      width: 100,
      align: 'center',
      render: (_, record) => `${record.retryCount} / ${record.maxRetries}`,
    },
    {
      title: 'Sent At',
      dataIndex: 'sentAt',
      key: 'sentAt',
      render: (val?: string) => val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: 'Completed At',
      dataIndex: 'completedAt',
      key: 'completedAt',
      render: (val?: string) => val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: 'Failure Reason',
      dataIndex: 'failureReason',
      key: 'failureReason',
      ellipsis: true,
      render: (val?: string) => val || '-',
    },
  ];

  return (
    <Card
      title="Rail Instructions"
      extra={
        <Button icon={<ReloadOutlined />} onClick={fetchInstructions}>Refresh</Button>
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <InputNumber
          placeholder="Payment ID"
          style={{ width: 160 }}
          value={paymentIdFilter}
          onChange={(val) => { setPaymentIdFilter(val ?? undefined); setPage(0); }}
          min={1}
        />
        <Select
          placeholder="Filter by Rail"
          allowClear
          style={{ width: 160 }}
          value={railFilter}
          onChange={(val) => { setRailFilter(val); setPage(0); }}
          options={railOptions.map((r) => ({ label: r, value: r }))}
        />
        <Select
          placeholder="Filter by Status"
          allowClear
          style={{ width: 180 }}
          value={statusFilter}
          onChange={(val) => { setStatusFilter(val); setPage(0); }}
          options={statusOptions.map((s) => ({ label: s, value: s }))}
        />
      </Space>

      <Table<RailInstruction>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} instructions`,
          onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
        }}
      />
    </Card>
  );
};

export default RailInstructionsPage;
