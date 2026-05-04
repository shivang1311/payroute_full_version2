import React, { useEffect, useState, useCallback } from 'react';
import { Table, Card, Tag, Select, Space, Button, Modal, DatePicker, message } from 'antd';
import { ReloadOutlined, PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { settlementApi } from '../../api/settlement.api';
import { useAuthStore } from '../../stores/authStore';
import type { SettlementBatch, RailType } from '../../types';

const railColorMap: Record<RailType, string> = {
  BOOK: 'default',
  NEFT: 'blue',
  RTGS: 'purple',
  IMPS: 'cyan',
  ACH: 'geekblue',
  WIRE: 'volcano',
};

const railOptions: RailType[] = ['BOOK', 'NEFT', 'RTGS', 'IMPS', 'ACH', 'WIRE'];

const SettlementBatchesPage: React.FC = () => {
  const { user } = useAuthStore();
  // OPERATIONS gets read-only — they can view batches but not create new ones
  const canCreateBatch = user?.role === 'RECONCILIATION' || user?.role === 'ADMIN';
  const [data, setData] = useState<SettlementBatch[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [railFilter, setRailFilter] = useState<string | undefined>();

  const [modalOpen, setModalOpen] = useState(false);
  const [createRail, setCreateRail] = useState<RailType | undefined>();
  const [periodStart, setPeriodStart] = useState<Dayjs | null>(null);
  const [periodEnd, setPeriodEnd] = useState<Dayjs | null>(null);
  const [creating, setCreating] = useState(false);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await settlementApi.getBatches({
        page,
        size: pageSize,
        rail: railFilter,
      });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load settlement batches');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, railFilter]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleCreate = async () => {
    if (!createRail || !periodStart || !periodEnd) {
      message.warning('Please fill all fields');
      return;
    }
    setCreating(true);
    try {
      // Backend expects LocalDateTime (ISO with time component). Use start-of-day for
       // periodStart and end-of-day for periodEnd so the window covers the whole day(s).
       await settlementApi.createBatch({
         rail: createRail,
         periodStart: periodStart.startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
         periodEnd: periodEnd.endOf('day').format('YYYY-MM-DDTHH:mm:ss'),
       });
      message.success('Settlement batch created successfully');
      setModalOpen(false);
      setCreateRail(undefined);
      setPeriodStart(null);
      setPeriodEnd(null);
      fetchData();
    } catch {
      message.error('Failed to create settlement batch');
    } finally {
      setCreating(false);
    }
  };

  const formatAmount = (val?: number) =>
    val != null
      ? val.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      : '-';

  const columns: ColumnsType<SettlementBatch> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
    {
      title: 'Rail',
      dataIndex: 'rail',
      key: 'rail',
      render: (r: RailType) => <Tag color={railColorMap[r]}>{r}</Tag>,
    },
    {
      title: 'Period Start',
      dataIndex: 'periodStart',
      key: 'periodStart',
      render: (val: string) => dayjs(val).format('YYYY-MM-DD'),
    },
    {
      title: 'Period End',
      dataIndex: 'periodEnd',
      key: 'periodEnd',
      render: (val: string) => dayjs(val).format('YYYY-MM-DD'),
    },
    { title: 'Total Count', dataIndex: 'totalCount', key: 'totalCount', align: 'right', width: 100 },
    {
      title: 'Total Amount',
      key: 'totalAmount',
      align: 'right',
      render: (_, record) => formatAmount(record.totalAmount),
    },
    {
      title: 'Net Amount',
      key: 'netAmount',
      align: 'right',
      render: (_, record) => formatAmount(record.netAmount),
    },
    {
      title: 'Total Fees',
      key: 'totalFees',
      align: 'right',
      render: (_, record) => formatAmount(record.totalFees),
    },
    { title: 'Currency', dataIndex: 'currency', key: 'currency', width: 80 },
    {
      title: 'Created Date',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (val?: string) => (val ? dayjs(val).format('YYYY-MM-DD') : '-'),
    },
  ];

  return (
    <Card
      title="Settlement Batches"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchData}>
            Refresh
          </Button>
          {canCreateBatch && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
              Create Batch
            </Button>
          )}
        </Space>
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          placeholder="Filter by Rail"
          allowClear
          style={{ width: 160 }}
          value={railFilter}
          onChange={(val) => { setRailFilter(val); setPage(0); }}
          options={railOptions.map((r) => ({ label: r, value: r }))}
        />
      </Space>

      <Table<SettlementBatch>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        scroll={{ x: 1200 }}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} batches`,
          onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
        }}
      />

      <Modal
        title="Create Settlement Batch"
        open={modalOpen}
        onOk={handleCreate}
        onCancel={() => {
          setModalOpen(false);
          setCreateRail(undefined);
          setPeriodStart(null);
          setPeriodEnd(null);
        }}
        confirmLoading={creating}
        okText="Create"
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <div style={{ marginBottom: 4 }}>Rail</div>
            <Select
              style={{ width: '100%' }}
              placeholder="Select rail"
              value={createRail}
              onChange={setCreateRail}
              options={railOptions.map((r) => ({ label: r, value: r }))}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4 }}>Period Start</div>
            <DatePicker
              style={{ width: '100%' }}
              value={periodStart}
              onChange={setPeriodStart}
              format="YYYY-MM-DD"
            />
          </div>
          <div>
            <div style={{ marginBottom: 4 }}>Period End</div>
            <DatePicker
              style={{ width: '100%' }}
              value={periodEnd}
              onChange={setPeriodEnd}
              format="YYYY-MM-DD"
            />
          </div>
        </Space>
      </Modal>
    </Card>
  );
};

export default SettlementBatchesPage;
