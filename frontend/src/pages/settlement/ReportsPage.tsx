import React, { useEffect, useState, useCallback } from 'react';
import { Table, Card, Tag, Space, Button, Modal, Input, Select, message, Descriptions, Empty } from 'antd';
import { ReloadOutlined, FileAddOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { reportApi } from '../../api/settlement.api';
import type { PaymentReport, ReportScope } from '../../types';

const statusColorMap: Record<string, string> = {
  COMPLETED: 'green',
  GENERATING: 'blue',
  FAILED: 'red',
};

const scopeOptions: ReportScope[] = ['PRODUCT', 'RAIL', 'PERIOD', 'DAILY', 'MONTHLY', 'CUSTOM'];

// Pretty label for camelCase keys: "totalAmount" -> "Total Amount"
const prettyLabel = (key: string) =>
  key
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, (s) => s.toUpperCase())
    .trim();

// Format a primitive value for display
const formatValue = (val: unknown): string => {
  if (val == null) return '-';
  if (typeof val === 'number') {
    return val.toLocaleString(undefined, { maximumFractionDigits: 2 });
  }
  if (typeof val === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(val)) {
    return dayjs(val).format('YYYY-MM-DD HH:mm');
  }
  return String(val);
};

// Render a nested breakdown object as a table.
// Two shapes are handled:
//   { KEY: number }                  → 2-col table  (e.g. statusBreakdown)
//   { KEY: { col1, col2, ... } }     → multi-col table (e.g. railBreakdown)
const BreakdownTable: React.FC<{ title: string; obj: Record<string, unknown> }> = ({ title, obj }) => {
  const entries = Object.entries(obj);
  if (entries.length === 0) {
    return (
      <div style={{ marginTop: 12 }}>
        <strong>{title}</strong>
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No data" />
      </div>
    );
  }

  // Check shape: are values primitives or sub-objects?
  const firstVal = entries[0][1];
  const isNested = firstVal != null && typeof firstVal === 'object' && !Array.isArray(firstVal);

  if (!isNested) {
    // Simple key→value
    const dataSource = entries.map(([k, v]) => ({ key: k, label: k, value: v }));
    return (
      <div style={{ marginTop: 12 }}>
        <strong>{prettyLabel(title)}</strong>
        <Table
          size="small"
          pagination={false}
          dataSource={dataSource}
          style={{ marginTop: 8 }}
          columns={[
            { title: 'Key', dataIndex: 'label', key: 'label', render: (v: string) => prettyLabel(v) },
            { title: 'Value', dataIndex: 'value', key: 'value', align: 'right', render: (v) => formatValue(v) },
          ]}
        />
      </div>
    );
  }

  // Nested: collect column keys from the first object (assume consistent shape)
  const subKeys = Object.keys(firstVal as Record<string, unknown>);
  const dataSource = entries.map(([k, v]) => ({ key: k, _name: k, ...(v as Record<string, unknown>) }));

  const columns: ColumnsType<Record<string, unknown>> = [
    { title: prettyLabel(title).replace(/ Breakdown$/i, ''), dataIndex: '_name', key: '_name' },
    ...subKeys.map((sk) => ({
      title: prettyLabel(sk),
      dataIndex: sk,
      key: sk,
      align: 'right' as const,
      render: (v: unknown) => formatValue(v),
    })),
  ];

  return (
    <div style={{ marginTop: 12 }}>
      <strong>{prettyLabel(title)}</strong>
      <Table
        size="small"
        pagination={false}
        dataSource={dataSource}
        columns={columns}
        style={{ marginTop: 8 }}
      />
    </div>
  );
};

const MetricsView: React.FC<{ raw: unknown }> = ({ raw }) => {
  if (raw == null) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No metrics" />;

  let parsed: Record<string, unknown>;
  try {
    parsed = typeof raw === 'string' ? JSON.parse(raw) : (raw as Record<string, unknown>);
  } catch {
    return <pre style={{ background: '#f5f5f5', padding: 12, borderRadius: 4 }}>{String(raw)}</pre>;
  }

  // Split top-level scalars vs nested objects
  const scalars: [string, unknown][] = [];
  const nested: [string, Record<string, unknown>][] = [];

  Object.entries(parsed).forEach(([k, v]) => {
    if (v != null && typeof v === 'object' && !Array.isArray(v)) {
      nested.push([k, v as Record<string, unknown>]);
    } else {
      scalars.push([k, v]);
    }
  });

  return (
    <div>
      {scalars.length > 0 && (
        <Descriptions
          size="small"
          bordered
          column={{ xs: 1, sm: 2, md: 3 }}
          title="Summary"
        >
          {scalars.map(([k, v]) => (
            <Descriptions.Item key={k} label={prettyLabel(k)}>
              {formatValue(v)}
            </Descriptions.Item>
          ))}
        </Descriptions>
      )}
      {nested.map(([k, v]) => (
        <BreakdownTable key={k} title={k} obj={v} />
      ))}
    </div>
  );
};

const ReportsPage: React.FC = () => {
  const [data, setData] = useState<PaymentReport[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);

  const [modalOpen, setModalOpen] = useState(false);
  const [reportName, setReportName] = useState('');
  const [scope, setScope] = useState<ReportScope | undefined>();
  const [generating, setGenerating] = useState(false);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await reportApi.getAll({ page, size: pageSize });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load reports');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleGenerate = async () => {
    if (!reportName.trim() || !scope) {
      message.warning('Please fill report name and scope');
      return;
    }
    setGenerating(true);
    try {
      await reportApi.generate({ reportName: reportName.trim(), scope });
      message.success('Report generation started');
      setModalOpen(false);
      setReportName('');
      setScope(undefined);
      fetchData();
    } catch {
      message.error('Failed to generate report');
    } finally {
      setGenerating(false);
    }
  };

  const columns: ColumnsType<PaymentReport> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
    { title: 'Report Name', dataIndex: 'reportName', key: 'reportName', ellipsis: true },
    {
      title: 'Scope',
      dataIndex: 'scope',
      key: 'scope',
      render: (s: ReportScope) => <Tag color="geekblue">{s}</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => <Tag color={statusColorMap[s] || 'default'}>{s}</Tag>,
    },
    {
      title: 'Generated At',
      dataIndex: 'generatedAt',
      key: 'generatedAt',
      render: (val?: string) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-'),
    },
  ];

  return (
    <Card
      title="Reports"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchData}>
            Refresh
          </Button>
          <Button type="primary" icon={<FileAddOutlined />} onClick={() => setModalOpen(true)}>
            Generate Report
          </Button>
        </Space>
      }
    >
      <Table<PaymentReport>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        expandable={{
          expandedRowRender: (record) => (
            <div style={{ padding: 8 }}>
              <MetricsView raw={record.metrics} />
            </div>
          ),
        }}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} reports`,
          onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
        }}
      />

      <Modal
        title="Generate Report"
        open={modalOpen}
        onOk={handleGenerate}
        onCancel={() => {
          setModalOpen(false);
          setReportName('');
          setScope(undefined);
        }}
        confirmLoading={generating}
        okText="Generate"
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <div style={{ marginBottom: 4 }}>Report Name</div>
            <Input
              placeholder="Enter report name"
              value={reportName}
              onChange={(e) => setReportName(e.target.value)}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4 }}>Scope</div>
            <Select
              style={{ width: '100%' }}
              placeholder="Select scope"
              value={scope}
              onChange={setScope}
              options={scopeOptions.map((s) => ({ label: s, value: s }))}
            />
          </div>
        </Space>
      </Modal>
    </Card>
  );
};

export default ReportsPage;
