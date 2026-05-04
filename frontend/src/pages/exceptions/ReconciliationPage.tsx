import React, { useEffect, useState, useCallback } from 'react';
import { Table, Card, Tag, Select, Space, Button, Modal, DatePicker, message } from 'antd';
import { ReloadOutlined, SyncOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { reconciliationApi } from '../../api/exception.api';
import type { ReconciliationRecord, ReconResult } from '../../types';

const resultColorMap: Record<ReconResult, string> = {
  MATCHED: 'green',
  UNMATCHED: 'red',
  PARTIAL: 'orange',
  DISCREPANCY: 'volcano',
};

const resultOptions: ReconResult[] = ['MATCHED', 'UNMATCHED', 'PARTIAL', 'DISCREPANCY'];

const ReconciliationPage: React.FC = () => {
  const [data, setData] = useState<ReconciliationRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [resultFilter, setResultFilter] = useState<string | undefined>();

  const [reconModalOpen, setReconModalOpen] = useState(false);
  const [reconDate, setReconDate] = useState<Dayjs | null>(null);
  const [reconRunning, setReconRunning] = useState(false);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await reconciliationApi.getAll({
        page,
        size: pageSize,
        result: resultFilter,
      });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load reconciliation records');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, resultFilter]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleRunReconciliation = async () => {
    if (!reconDate) {
      message.warning('Please select a date');
      return;
    }
    setReconRunning(true);
    try {
      await reconciliationApi.runReconciliation(reconDate.format('YYYY-MM-DD'));
      message.success('Reconciliation started successfully');
      setReconModalOpen(false);
      setReconDate(null);
      fetchData();
    } catch {
      message.error('Failed to run reconciliation');
    } finally {
      setReconRunning(false);
    }
  };

  const columns: ColumnsType<ReconciliationRecord> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
    {
      title: 'Source',
      dataIndex: 'source',
      key: 'source',
      render: (s: string) => <Tag color={s === 'RAIL' ? 'blue' : 'purple'}>{s}</Tag>,
    },
    { title: 'Reference ID', dataIndex: 'referenceId', key: 'referenceId', width: 110 },
    { title: 'Counterpart ID', dataIndex: 'counterpartId', key: 'counterpartId', width: 120 },
    {
      title: 'Recon Date',
      dataIndex: 'reconDate',
      key: 'reconDate',
      render: (val: string) => dayjs(val).format('YYYY-MM-DD'),
    },
    {
      title: 'Result',
      dataIndex: 'result',
      key: 'result',
      render: (r: ReconResult) => <Tag color={resultColorMap[r]}>{r}</Tag>,
    },
    {
      title: 'Amount',
      key: 'amount',
      align: 'right',
      render: (_, record) =>
        record.amount != null
          ? `${record.currency || ''} ${record.amount.toLocaleString(undefined, {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })}`
          : '-',
    },
    { title: 'Currency', dataIndex: 'currency', key: 'currency', width: 80 },
    {
      title: 'Resolved',
      dataIndex: 'resolved',
      key: 'resolved',
      width: 90,
      render: (val: boolean) => (
        <Tag color={val ? 'green' : 'red'}>{val ? 'Yes' : 'No'}</Tag>
      ),
    },
  ];

  return (
    <Card
      title="Reconciliation"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchData}>
            Refresh
          </Button>
          <Button
            type="primary"
            icon={<SyncOutlined />}
            onClick={() => setReconModalOpen(true)}
          >
            Run Reconciliation
          </Button>
        </Space>
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          placeholder="Filter by Result"
          allowClear
          style={{ width: 180 }}
          value={resultFilter}
          onChange={(val) => { setResultFilter(val); setPage(0); }}
          options={resultOptions.map((r) => ({ label: r, value: r }))}
        />
      </Space>

      <Table<ReconciliationRecord>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} records`,
          onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
        }}
      />

      <Modal
        title="Run Reconciliation"
        open={reconModalOpen}
        onOk={handleRunReconciliation}
        onCancel={() => { setReconModalOpen(false); setReconDate(null); }}
        confirmLoading={reconRunning}
        okText="Run"
      >
        <div style={{ marginBottom: 8 }}>Select a date to reconcile:</div>
        <DatePicker
          style={{ width: '100%' }}
          value={reconDate}
          onChange={setReconDate}
          format="YYYY-MM-DD"
        />
      </Modal>
    </Card>
  );
};

export default ReconciliationPage;
