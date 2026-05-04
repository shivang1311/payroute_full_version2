import React, { useEffect, useState, useCallback } from 'react';
import { Table, Card, Tag, Select, Space, Button, Popconfirm, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { returnApi } from '../../api/exception.api';
import type { ReturnItem, ReturnStatus } from '../../types';

const statusColorMap: Record<ReturnStatus, string> = {
  NOTIFIED: 'blue',
  PROCESSING: 'orange',
  POSTED: 'green',
  CLOSED: 'default',
};

const statusOptions: ReturnStatus[] = ['NOTIFIED', 'PROCESSING', 'POSTED', 'CLOSED'];

const ReturnsPage: React.FC = () => {
  const [data, setData] = useState<ReturnItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [processingId, setProcessingId] = useState<number | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await returnApi.getAll({
        page,
        size: pageSize,
        status: statusFilter,
      });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load returns');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statusFilter]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleProcess = async (id: number) => {
    setProcessingId(id);
    try {
      await returnApi.process(id);
      message.success('Return processed successfully');
      fetchData();
    } catch {
      message.error('Failed to process return');
    } finally {
      setProcessingId(null);
    }
  };

  const columns: ColumnsType<ReturnItem> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
    { title: 'Payment ID', dataIndex: 'paymentId', key: 'paymentId', width: 100 },
    { title: 'Reason Code', dataIndex: 'reasonCode', key: 'reasonCode', width: 120 },
    { title: 'Reason Desc', dataIndex: 'reasonDesc', key: 'reasonDesc', ellipsis: true },
    {
      title: 'Amount',
      key: 'amount',
      align: 'right',
      render: (_, record) =>
        `${record.currency} ${record.amount.toLocaleString(undefined, {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        })}`,
    },
    { title: 'Currency', dataIndex: 'currency', key: 'currency', width: 80 },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: ReturnStatus) => <Tag color={statusColorMap[s]}>{s}</Tag>,
    },
    {
      title: 'Return Date',
      dataIndex: 'returnDate',
      key: 'returnDate',
      render: (val: string) => dayjs(val).format('YYYY-MM-DD'),
    },
    {
      title: 'Processed At',
      dataIndex: 'processedAt',
      key: 'processedAt',
      render: (val?: string) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: 'Action',
      key: 'action',
      width: 140,
      render: (_, record) =>
        record.status === 'NOTIFIED' ? (
          <Popconfirm
            title="Process this return?"
            description="This will begin processing the return item."
            onConfirm={() => handleProcess(record.id)}
            okText="Yes"
            cancelText="No"
          >
            <Button
              type="link"
              size="small"
              loading={processingId === record.id}
            >
              Process Return
            </Button>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <Card
      title="Returns"
      extra={
        <Button icon={<ReloadOutlined />} onClick={fetchData}>
          Refresh
        </Button>
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          placeholder="Filter by Status"
          allowClear
          style={{ width: 180 }}
          value={statusFilter}
          onChange={(val) => { setStatusFilter(val); setPage(0); }}
          options={statusOptions.map((s) => ({ label: s, value: s }))}
        />
      </Space>

      <Table<ReturnItem>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} returns`,
          onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
        }}
      />
    </Card>
  );
};

export default ReturnsPage;
