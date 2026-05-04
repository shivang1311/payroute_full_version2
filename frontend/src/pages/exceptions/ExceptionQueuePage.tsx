import React, { useEffect, useState, useCallback, useMemo } from 'react';
import {
  Table, Card, Tag, Select, Space, Button, Modal, Input, message, Switch, Tooltip,
  Descriptions, Segmented, Row, Col, Statistic, Empty,
} from 'antd';
import {
  ReloadOutlined, UserOutlined, EditOutlined, LinkOutlined,
  UserDeleteOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { exceptionApi } from '../../api/exception.api';
import { useAuthStore } from '../../stores/authStore';
import type { ExceptionCase, ExceptionCategory, ExceptionStatus } from '../../types';

const statusColorMap: Record<ExceptionStatus, string> = {
  OPEN: 'red',
  IN_PROGRESS: 'blue',
  RESOLVED: 'green',
  ESCALATED: 'orange',
  CLOSED: 'default',
};

const priorityColorMap: Record<string, string> = {
  CRITICAL: 'magenta',
  HIGH: 'red',
  MEDIUM: 'orange',
  LOW: 'green',
};

const categoryColorMap: Record<string, string> = {
  VALIDATION: 'geekblue',
  RAIL: 'cyan',
  POSTING: 'purple',
  COMPLIANCE: 'volcano',
  SYSTEM: 'default',
};

const categoryOptions: ExceptionCategory[] = ['VALIDATION', 'RAIL', 'POSTING', 'COMPLIANCE', 'SYSTEM'];
const statusOptions: ExceptionStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'ESCALATED', 'CLOSED'];

const SLA_WARN_HOURS = 4;
const SLA_BREACH_HOURS = 24;

const slaInfo = (createdAt: string, status: ExceptionStatus) => {
  if (status === 'RESOLVED' || status === 'CLOSED') {
    return { label: '—', color: 'default' as const, tooltip: 'Closed' };
  }
  const hours = (Date.now() - new Date(createdAt).getTime()) / 36e5;
  if (hours < 1) return { label: `${Math.round(hours * 60)}m`, color: 'green', tooltip: 'Within SLA' };
  if (hours < SLA_WARN_HOURS) return { label: `${hours.toFixed(1)}h`, color: 'cyan', tooltip: 'Within SLA' };
  if (hours < SLA_BREACH_HOURS) return { label: `${hours.toFixed(1)}h`, color: 'orange', tooltip: `Approaching SLA breach (${SLA_BREACH_HOURS}h)` };
  return { label: `${(hours / 24).toFixed(1)}d`, color: 'red', tooltip: 'SLA breached' };
};

const ExceptionQueuePage: React.FC = () => {
  const { user } = useAuthStore();
  const navigate = useNavigate();
  const [data, setData] = useState<ExceptionCase[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [statusFilter, setStatusFilter] = useState<ExceptionStatus | 'ALL'>('ALL');
  const [categoryFilter, setCategoryFilter] = useState<string | undefined>();
  const [myQueueOnly, setMyQueueOnly] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<ExceptionCase | null>(null);
  const [updateStatus, setUpdateStatus] = useState<ExceptionStatus | undefined>();
  const [resolution, setResolution] = useState('');
  const [updating, setUpdating] = useState(false);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await exceptionApi.getAll({
        page,
        size: pageSize,
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        category: categoryFilter,
        ownerId: myQueueOnly && user?.id ? user.id : undefined,
      });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load exception queue');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statusFilter, categoryFilter, myQueueOnly, user?.id]);

  useEffect(() => { fetchData(); }, [fetchData]);

  // Local stats (counts on the current page) — gives the user a quick at-a-glance summary
  const stats = useMemo(() => {
    const byStatus: Record<string, number> = {};
    const breached = data.filter(e => {
      if (e.status === 'RESOLVED' || e.status === 'CLOSED') return false;
      return (Date.now() - new Date(e.createdAt).getTime()) / 36e5 >= SLA_BREACH_HOURS;
    }).length;
    data.forEach(e => { byStatus[e.status] = (byStatus[e.status] || 0) + 1; });
    return { total, open: byStatus.OPEN || 0, inProgress: byStatus.IN_PROGRESS || 0, breached };
  }, [data, total]);

  const handleUpdateStatus = async () => {
    if (!selectedRecord || !updateStatus) return;
    setUpdating(true);
    try {
      await exceptionApi.update(selectedRecord.id, {
        status: updateStatus,
        resolution,
      });
      message.success('Exception updated');
      setModalOpen(false);
      setSelectedRecord(null);
      setUpdateStatus(undefined);
      setResolution('');
      fetchData();
    } catch {
      message.error('Failed to update exception');
    } finally {
      setUpdating(false);
    }
  };

  const handleAssignToMe = async (record: ExceptionCase) => {
    if (!user?.id) return;
    try {
      await exceptionApi.assign(record.id, user.id);
      message.success(`Claimed exception #${record.id}`);
      fetchData();
    } catch { message.error('Failed to claim exception'); }
  };

  const handleRelease = async (record: ExceptionCase) => {
    try {
      await exceptionApi.assign(record.id, null);
      message.success(`Released exception #${record.id}`);
      fetchData();
    } catch { message.error('Failed to release exception'); }
  };

  const openUpdateModal = (record: ExceptionCase) => {
    setSelectedRecord(record);
    setUpdateStatus(record.status);
    setResolution(record.resolution || '');
    setModalOpen(true);
  };

  const columns: ColumnsType<ExceptionCase> = [
    {
      title: '#',
      dataIndex: 'id',
      key: 'id',
      width: 70,
      fixed: 'left',
      render: (id: number) => (
        <span style={{ fontFamily: 'var(--pr-mono, monospace)', fontWeight: 600, color: 'var(--pr-primary, #6366f1)' }}>
          #{id}
        </span>
      ),
    },
    {
      title: 'Payment',
      dataIndex: 'paymentId',
      key: 'paymentId',
      width: 100,
      render: (pid: number) => (
        <Button
          type="link"
          size="small"
          icon={<LinkOutlined />}
          style={{ padding: 0, fontFamily: 'var(--pr-mono, monospace)' }}
          onClick={() => navigate(`/payments/${pid}`)}
        >
          P#{pid}
        </Button>
      ),
    },
    {
      title: 'Category',
      dataIndex: 'category',
      key: 'category',
      width: 130,
      render: (cat: ExceptionCategory) => (
        <Tag color={categoryColorMap[cat] || 'default'} style={{ margin: 0, minWidth: 90, textAlign: 'center' }}>{cat}</Tag>
      ),
    },
    {
      title: 'Priority',
      dataIndex: 'priority',
      key: 'priority',
      width: 100,
      render: (p: string) => (
        <Tag color={priorityColorMap[p] || 'default'} style={{ margin: 0, minWidth: 70, textAlign: 'center' }}>{p}</Tag>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (s: ExceptionStatus) => (
        <Tag color={statusColorMap[s]} style={{ margin: 0, minWidth: 100, textAlign: 'center' }}>
          {s.replace('_', ' ')}
        </Tag>
      ),
    },
    {
      title: 'Owner',
      dataIndex: 'ownerId',
      key: 'ownerId',
      width: 120,
      render: (oid?: number) => {
        if (oid == null) return <Tag style={{ margin: 0 }}>Unassigned</Tag>;
        if (oid === user?.id) return <Tag color="green" style={{ margin: 0 }}>Me</Tag>;
        return <Tag color="blue" style={{ margin: 0 }}>User #{oid}</Tag>;
      },
    },
    {
      title: 'SLA',
      key: 'age',
      width: 80,
      align: 'center',
      render: (_, record) => {
        const sla = slaInfo(record.createdAt, record.status);
        return (
          <Tooltip title={sla.tooltip}>
            <Tag color={sla.color} style={{ margin: 0 }}>{sla.label}</Tag>
          </Tooltip>
        );
      },
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 120,
      render: (val: string) => (
        <Tooltip title={dayjs(val).format('YYYY-MM-DD HH:mm:ss')}>
          <span style={{ fontSize: 12, color: 'var(--pr-text-muted, rgba(255,255,255,0.65))' }}>
            {dayjs(val).fromNow ? dayjs(val).format('MMM D, HH:mm') : dayjs(val).format('YYYY-MM-DD HH:mm')}
          </span>
        </Tooltip>
      ),
    },
    {
      title: 'Actions',
      key: 'action',
      width: 160,
      fixed: 'right',
      render: (_, record) => {
        const isTerminal = record.status === 'RESOLVED' || record.status === 'CLOSED';
        return (
          <Space size={4}>
            <Tooltip title="Update status / resolution">
              <Button
                type="text"
                size="small"
                icon={<EditOutlined />}
                onClick={() => openUpdateModal(record)}
              />
            </Tooltip>
            {!isTerminal && record.ownerId == null && (
              <Tooltip title="Claim — assign this case to me">
                <Button
                  type="text"
                  size="small"
                  icon={<UserOutlined />}
                  onClick={() => handleAssignToMe(record)}
                />
              </Tooltip>
            )}
            {record.ownerId === user?.id && (
              <Tooltip title="Release — un-assign myself">
                <Button
                  type="text"
                  size="small"
                  danger
                  icon={<UserDeleteOutlined />}
                  onClick={() => handleRelease(record)}
                />
              </Tooltip>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {/* Stats header */}
      <Row gutter={[12, 12]}>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic title="Total" value={stats.total} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic title="Open" value={stats.open} valueStyle={{ color: '#cf1322' }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic title="In Progress" value={stats.inProgress} valueStyle={{ color: '#1677ff' }} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="SLA Breached"
              value={stats.breached}
              valueStyle={{ color: stats.breached > 0 ? '#cf1322' : undefined }}
            />
          </Card>
        </Col>
      </Row>

      <Card
        title="Exception Queue"
        extra={
          <Space>
            <span style={{ color: 'var(--pr-text-muted, rgba(255,255,255,0.65))', fontSize: 13 }}>My Queue</span>
            <Switch checked={myQueueOnly} onChange={(v) => { setMyQueueOnly(v); setPage(0); }} size="small" />
            <Button icon={<ReloadOutlined />} onClick={fetchData}>Refresh</Button>
          </Space>
        }
      >
        {/* Status segmented + Category filter */}
        <Space
          style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between', flexWrap: 'wrap', rowGap: 8 }}
        >
          <Segmented
            value={statusFilter}
            onChange={(val) => { setStatusFilter(val as ExceptionStatus | 'ALL'); setPage(0); }}
            options={[
              { label: 'All', value: 'ALL' },
              { label: 'Open', value: 'OPEN' },
              { label: 'In Progress', value: 'IN_PROGRESS' },
              { label: 'Resolved', value: 'RESOLVED' },
              { label: 'Closed', value: 'CLOSED' },
            ]}
          />
          <Select
            placeholder="All Categories"
            allowClear
            style={{ width: 180 }}
            value={categoryFilter}
            onChange={(val) => { setCategoryFilter(val); setPage(0); }}
            options={categoryOptions.map((c) => ({ label: c, value: c }))}
          />
        </Space>

        <Table<ExceptionCase>
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          size="middle"
          scroll={{ x: 1100 }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No exceptions match these filters" /> }}
          rowClassName={(record) => {
            if (record.status === 'RESOLVED' || record.status === 'CLOSED') return '';
            const hours = (Date.now() - new Date(record.createdAt).getTime()) / 36e5;
            if (hours >= SLA_BREACH_HOURS) return 'pr-row-breached';
            return '';
          }}
          expandable={{
            expandedRowRender: (record) => (
              <Descriptions
                size="small"
                column={{ xs: 1, sm: 2, md: 3 }}
                bordered
                styles={{ label: { width: 120, fontWeight: 500 } }}
              >
                <Descriptions.Item label="Description" span={3}>
                  {record.description || <em style={{ opacity: 0.6 }}>No description</em>}
                </Descriptions.Item>
                <Descriptions.Item label="Created">
                  {dayjs(record.createdAt).format('YYYY-MM-DD HH:mm:ss')}
                </Descriptions.Item>
                <Descriptions.Item label="Resolved">
                  {record.resolvedAt
                    ? dayjs(record.resolvedAt).format('YYYY-MM-DD HH:mm:ss')
                    : <em style={{ opacity: 0.6 }}>Not yet</em>}
                </Descriptions.Item>
                <Descriptions.Item label="Owner ID">
                  {record.ownerId ?? <em style={{ opacity: 0.6 }}>Unassigned</em>}
                </Descriptions.Item>
                <Descriptions.Item label="Resolution" span={3}>
                  {record.resolution || <em style={{ opacity: 0.6 }}>None</em>}
                </Descriptions.Item>
              </Descriptions>
            ),
          }}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `Total ${t} exceptions`,
            onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
          }}
        />
      </Card>

      <Modal
        title={selectedRecord ? `Update Exception #${selectedRecord.id}` : 'Update Exception'}
        open={modalOpen}
        onOk={handleUpdateStatus}
        onCancel={() => { setModalOpen(false); setSelectedRecord(null); }}
        confirmLoading={updating}
        okText="Save"
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <div style={{ marginBottom: 4 }}>Status</div>
            <Select
              style={{ width: '100%' }}
              value={updateStatus}
              onChange={setUpdateStatus}
              options={statusOptions.map((s) => ({ label: s.replace('_', ' '), value: s }))}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4 }}>Resolution</div>
            <Input.TextArea
              rows={4}
              value={resolution}
              onChange={(e) => setResolution(e.target.value)}
              placeholder="Enter resolution details..."
            />
          </div>
        </Space>
      </Modal>

      <style>{`
        .pr-row-breached > td {
          background-color: rgba(255, 77, 79, 0.06) !important;
        }
        .pr-row-breached:hover > td {
          background-color: rgba(255, 77, 79, 0.12) !important;
        }
      `}</style>
    </Space>
  );
};

export default ExceptionQueuePage;
