import React, { useEffect, useState, useCallback } from 'react';
import { Table, Card, Tag, Select, Space, message, InputNumber, Button } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { complianceApi } from '../../api/compliance.api';
import type { ComplianceCheck, CheckType, CheckResult } from '../../types';

const checkTypeColorMap: Record<CheckType, string> = {
  SANCTIONS: 'red',
  AML: 'orange',
  PEP: 'purple',
  GEO: 'blue',
};

const severityColorMap: Record<string, string> = {
  HIGH: 'red',
  MEDIUM: 'orange',
  LOW: 'green',
};

const resultColorMap: Record<CheckResult, string> = {
  CLEAR: 'green',
  FLAG: 'orange',
  HOLD: 'red',
};

const resultOptions: CheckResult[] = ['CLEAR', 'FLAG', 'HOLD'];

const ComplianceChecksPage: React.FC = () => {
  const [data, setData] = useState<ComplianceCheck[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [resultFilter, setResultFilter] = useState<string | undefined>();
  const [paymentIdFilter, setPaymentIdFilter] = useState<number | undefined>();

  const fetchChecks = useCallback(async () => {
    setLoading(true);
    try {
      const response = await complianceApi.getChecks({
        page,
        size: pageSize,
        result: resultFilter,
        paymentId: paymentIdFilter,
      });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load compliance checks');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, resultFilter, paymentIdFilter]);

  useEffect(() => {
    fetchChecks();
  }, [fetchChecks]);

  const columns: ColumnsType<ComplianceCheck> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: 'Payment ID', dataIndex: 'paymentId', key: 'paymentId', width: 110 },
    {
      title: 'Check Type',
      dataIndex: 'checkType',
      key: 'checkType',
      render: (type: CheckType) => <Tag color={checkTypeColorMap[type]}>{type}</Tag>,
    },
    {
      title: 'Severity',
      dataIndex: 'severity',
      key: 'severity',
      render: (severity: string) => (
        <Tag color={severityColorMap[severity] || 'default'}>{severity}</Tag>
      ),
    },
    {
      title: 'Result',
      dataIndex: 'result',
      key: 'result',
      render: (result: CheckResult) => <Tag color={resultColorMap[result]}>{result}</Tag>,
    },
    {
      title: 'Details',
      dataIndex: 'details',
      key: 'details',
      ellipsis: true,
      render: (val?: Record<string, unknown>) =>
        val ? JSON.stringify(val) : '-',
    },
    { title: 'Checked By', dataIndex: 'checkedBy', key: 'checkedBy' },
    {
      title: 'Checked At',
      dataIndex: 'checkedAt',
      key: 'checkedAt',
      render: (val: string) => dayjs(val).format('YYYY-MM-DD HH:mm'),
    },
  ];

  return (
    <Card
      title="Compliance Checks"
      extra={
        <Button icon={<ReloadOutlined />} onClick={fetchChecks}>Refresh</Button>
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
          placeholder="Filter by Result"
          allowClear
          style={{ width: 160 }}
          value={resultFilter}
          onChange={(val) => { setResultFilter(val); setPage(0); }}
          options={resultOptions.map((r) => ({ label: r, value: r }))}
        />
      </Space>

      <Table<ComplianceCheck>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} checks`,
          onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
        }}
      />
    </Card>
  );
};

export default ComplianceChecksPage;
