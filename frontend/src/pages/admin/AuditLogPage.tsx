import React, { useEffect, useState, useCallback } from 'react';
import { Table, Card, Button, Typography, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { auditApi } from '../../api/auth.api';
import type { AuditLog } from '../../types';

const { Text } = Typography;

const AuditLogPage: React.FC = () => {
  const [data, setData] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await auditApi.getAll(page, pageSize);
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load audit logs');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const columns: ColumnsType<AuditLog> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
    { title: 'User ID', dataIndex: 'userId', key: 'userId', width: 80 },
    { title: 'Username', dataIndex: 'username', key: 'username', render: (val?: string) => val || '-' },
    { title: 'Action', dataIndex: 'action', key: 'action' },
    { title: 'Entity Type', dataIndex: 'entityType', key: 'entityType', render: (val?: string) => val || '-' },
    { title: 'Entity ID', dataIndex: 'entityId', key: 'entityId', width: 90, render: (val?: number) => val ?? '-' },
    {
      title: 'Details',
      dataIndex: 'details',
      key: 'details',
      ellipsis: true,
      width: 200,
      render: (val?: string) => val ? <Text ellipsis={{ tooltip: val }}>{val}</Text> : '-',
    },
    { title: 'IP Address', dataIndex: 'ipAddress', key: 'ipAddress', width: 130, render: (val?: string) => val || '-' },
    {
      title: 'Created At',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (val: string) => dayjs(val).format('YYYY-MM-DD HH:mm:ss'),
    },
  ];

  return (
    <Card
      title="Audit Log"
      extra={
        <Button icon={<ReloadOutlined />} onClick={fetchData}>
          Refresh
        </Button>
      }
    >
      <Table<AuditLog>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        scroll={{ x: 1100 }}
        expandable={{
          expandedRowRender: (record) =>
            record.details ? (
              <div style={{ padding: 8 }}>
                <strong>Full Details:</strong>
                <pre style={{ marginTop: 8, background: '#f5f5f5', padding: 12, borderRadius: 4, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                  {record.details}
                </pre>
              </div>
            ) : (
              <Text type="secondary">No details available</Text>
            ),
          rowExpandable: (record) => !!record.details,
        }}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: (t) => `Total ${t} entries`,
          onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
        }}
      />
    </Card>
  );
};

export default AuditLogPage;
