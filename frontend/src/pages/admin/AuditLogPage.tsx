import React, { useEffect, useState, useCallback, useMemo } from 'react';
import {
  Table,
  Card,
  Button,
  Typography,
  message,
  Tag,
  Input,
  Select,
  Space,
  DatePicker,
  Row,
  Col,
  Statistic,
  Tooltip,
  Descriptions,
  Avatar,
  Empty,
  theme,
} from 'antd';
import {
  ReloadOutlined,
  SearchOutlined,
  ClearOutlined,
  UserOutlined,
  ClockCircleOutlined,
  AuditOutlined,
  GlobalOutlined,
  CalendarOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import dayjs, { Dayjs } from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import type { ColumnsType } from 'antd/es/table';
import { auditApi } from '../../api/auth.api';
import type { AuditLog } from '../../types';

dayjs.extend(relativeTime);

const { Text, Paragraph } = Typography;
const { RangePicker } = DatePicker;

/**
 * Map an action verb (CREATE / UPDATE / DELETE / LOGIN / ...) to an antd
 * Tag color so the table is scannable at a glance.
 */
const actionColor = (action: string): string => {
  const a = action.toUpperCase();
  if (a.includes('CREATE') || a.includes('REGISTER') || a.includes('INITIATE')) return 'green';
  if (a.includes('UPDATE') || a.includes('CHANGE') || a.includes('MODIFY')) return 'blue';
  if (a.includes('DELETE') || a.includes('REJECT') || a.includes('CANCEL') || a.includes('FAIL')) return 'red';
  if (a.includes('LOGIN') || a.includes('LOGOUT') || a.includes('AUTH')) return 'cyan';
  if (a.includes('APPROVE') || a.includes('RELEASE') || a.includes('COMPLETE')) return 'success';
  if (a.includes('HOLD') || a.includes('SUSPEND') || a.includes('FLAG')) return 'orange';
  if (a.includes('VIEW') || a.includes('READ') || a.includes('EXPORT')) return 'default';
  return 'purple';
};

/**
 * If `details` looks like JSON, pretty-print it. Otherwise return as-is.
 */
const formatDetails = (raw?: string): string => {
  if (!raw) return '';
  const trimmed = raw.trim();
  if ((trimmed.startsWith('{') && trimmed.endsWith('}')) ||
      (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
    try {
      return JSON.stringify(JSON.parse(trimmed), null, 2);
    } catch {
      return raw;
    }
  }
  return raw;
};

const AuditLogPage: React.FC = () => {
  // AntD theme tokens — auto-switch between light/dark per the global ConfigProvider.
  // Using these instead of hardcoded hex values keeps the expanded-row legible in both modes.
  const { token } = theme.useToken();

  const [data, setData] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);

  // Filter state
  const [searchText, setSearchText] = useState('');
  const [actionFilter, setActionFilter] = useState<string | undefined>();
  const [entityFilter, setEntityFilter] = useState<string | undefined>();
  const [dateRange, setDateRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      // Pull a generous page so client-side filters have data to work on.
      // (Backend doesn't yet expose filter params; we'll filter in-memory.)
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

  // ---- Derived: distinct values for filter dropdowns ----------------
  const uniqueActions = useMemo(
    () => Array.from(new Set(data.map((d) => d.action))).sort(),
    [data],
  );
  const uniqueEntityTypes = useMemo(
    () => Array.from(new Set(data.map((d) => d.entityType).filter(Boolean) as string[])).sort(),
    [data],
  );

  // ---- Filtered rows -----------------------------------------------
  const filtered = useMemo(() => {
    return data.filter((row) => {
      if (actionFilter && row.action !== actionFilter) return false;
      if (entityFilter && row.entityType !== entityFilter) return false;
      if (searchText) {
        const q = searchText.toLowerCase();
        const haystack = [
          row.username,
          row.action,
          row.entityType,
          String(row.entityId ?? ''),
          row.details,
          row.ipAddress,
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();
        if (!haystack.includes(q)) return false;
      }
      if (dateRange && (dateRange[0] || dateRange[1])) {
        const t = dayjs(row.createdAt);
        if (dateRange[0] && t.isBefore(dateRange[0].startOf('day'))) return false;
        if (dateRange[1] && t.isAfter(dateRange[1].endOf('day'))) return false;
      }
      return true;
    });
  }, [data, actionFilter, entityFilter, searchText, dateRange]);

  // ---- Stats for the top cards -------------------------------------
  const stats = useMemo(() => {
    const today = dayjs().startOf('day');
    const eventsToday = data.filter((d) => dayjs(d.createdAt).isAfter(today)).length;

    const userCounts = new Map<string, number>();
    data.forEach((d) => {
      const k = d.username || `User ${d.userId}`;
      userCounts.set(k, (userCounts.get(k) ?? 0) + 1);
    });
    const topUser = [...userCounts.entries()].sort((a, b) => b[1] - a[1])[0];

    const actionCounts = new Map<string, number>();
    data.forEach((d) => actionCounts.set(d.action, (actionCounts.get(d.action) ?? 0) + 1));
    const topAction = [...actionCounts.entries()].sort((a, b) => b[1] - a[1])[0];

    return {
      totalLoaded: data.length,
      eventsToday,
      topUser: topUser ? `${topUser[0]} (${topUser[1]})` : '—',
      topAction: topAction ? `${topAction[0]} (${topAction[1]})` : '—',
    };
  }, [data]);

  const clearFilters = () => {
    setSearchText('');
    setActionFilter(undefined);
    setEntityFilter(undefined);
    setDateRange(null);
  };
  const hasActiveFilter =
    !!searchText || !!actionFilter || !!entityFilter || (!!dateRange && (!!dateRange[0] || !!dateRange[1]));

  // ---- Table columns -----------------------------------------------
  const columns: ColumnsType<AuditLog> = [
    {
      title: '#',
      dataIndex: 'id',
      key: 'id',
      width: 80,
      render: (val: number) => <Text type="secondary">#{val}</Text>,
    },
    {
      title: 'When',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 200,
      render: (val: string) => (
        <Tooltip title={dayjs(val).format('YYYY-MM-DD HH:mm:ss')}>
          <Space direction="vertical" size={0}>
            <Text strong style={{ fontSize: 13 }}>
              {dayjs(val).format('DD MMM YYYY')}
            </Text>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {dayjs(val).format('HH:mm:ss')} · {dayjs(val).fromNow()}
            </Text>
          </Space>
        </Tooltip>
      ),
      sorter: (a, b) => dayjs(a.createdAt).valueOf() - dayjs(b.createdAt).valueOf(),
      defaultSortOrder: 'descend',
    },
    {
      title: 'User',
      key: 'user',
      width: 200,
      render: (_, record) => (
        <Space>
          <Avatar size="small" icon={<UserOutlined />} style={{ backgroundColor: '#1677ff' }} />
          <Space direction="vertical" size={0}>
            <Text strong>{record.username || `User #${record.userId}`}</Text>
            <Text type="secondary" style={{ fontSize: 11 }}>
              ID: {record.userId}
            </Text>
          </Space>
        </Space>
      ),
    },
    {
      title: 'Action',
      dataIndex: 'action',
      key: 'action',
      width: 200,
      render: (val: string) => (
        <Tag color={actionColor(val)} style={{ fontWeight: 500, fontSize: 12 }}>
          {val}
        </Tag>
      ),
      filters: uniqueActions.map((a) => ({ text: a, value: a })),
      onFilter: (val, record) => record.action === val,
    },
    {
      title: 'Target',
      key: 'target',
      width: 180,
      render: (_, record) =>
        record.entityType ? (
          <Space direction="vertical" size={0}>
            <Tag color="geekblue">{record.entityType}</Tag>
            {record.entityId != null && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                ID: {record.entityId}
              </Text>
            )}
          </Space>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: 'IP Address',
      dataIndex: 'ipAddress',
      key: 'ipAddress',
      width: 170,
      render: (val?: string) =>
        val ? (
          <Space>
            <GlobalOutlined style={{ color: '#8c8c8c' }} />
            <Text code style={{ fontSize: 12 }}>{val}</Text>
          </Space>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
  ];

  return (
    <div style={{ padding: '0 4px' }}>
      {/* ---- Header ---- */}
      <div style={{ marginBottom: 16 }}>
        <Space align="center" size="middle">
          <AuditOutlined style={{ fontSize: 28, color: '#1677ff' }} />
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              Audit Log
            </Typography.Title>
            <Text type="secondary">
              Track every action performed across the platform — security, debugging, and compliance trail.
            </Text>
          </div>
        </Space>
      </div>

      {/* ---- Stats cards ---- */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="Loaded Events"
              value={stats.totalLoaded}
              prefix={<FileTextOutlined style={{ color: '#1677ff' }} />}
              suffix={<Text type="secondary" style={{ fontSize: 12 }}>of {total}</Text>}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="Events Today"
              value={stats.eventsToday}
              prefix={<CalendarOutlined style={{ color: '#52c41a' }} />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="Most Active User"
              value={stats.topUser}
              prefix={<UserOutlined style={{ color: '#722ed1' }} />}
              valueStyle={{ fontSize: 16 }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="Top Action"
              value={stats.topAction}
              prefix={<ClockCircleOutlined style={{ color: '#fa8c16' }} />}
              valueStyle={{ fontSize: 16 }}
            />
          </Card>
        </Col>
      </Row>

      {/* ---- Main card with filters + table ---- */}
      <Card
        title={
          <Space>
            <Text strong>Activity Trail</Text>
            <Tag color="blue">{filtered.length} shown</Tag>
            {hasActiveFilter && <Tag color="orange">filtered</Tag>}
          </Space>
        }
        extra={
          <Space>
            {hasActiveFilter && (
              <Button icon={<ClearOutlined />} onClick={clearFilters}>
                Clear Filters
              </Button>
            )}
            <Button type="primary" icon={<ReloadOutlined />} onClick={fetchData} loading={loading}>
              Refresh
            </Button>
          </Space>
        }
      >
        {/* Filter row */}
        <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
          <Col xs={24} sm={12} md={8} lg={8}>
            <Input
              allowClear
              placeholder="Search username, action, IP, target..."
              prefix={<SearchOutlined />}
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
            />
          </Col>
          <Col xs={12} sm={6} md={4} lg={4}>
            <Select
              allowClear
              placeholder="Action"
              style={{ width: '100%' }}
              value={actionFilter}
              onChange={setActionFilter}
              options={uniqueActions.map((a) => ({ label: a, value: a }))}
              showSearch
              optionFilterProp="label"
            />
          </Col>
          <Col xs={12} sm={6} md={4} lg={4}>
            <Select
              allowClear
              placeholder="Entity"
              style={{ width: '100%' }}
              value={entityFilter}
              onChange={setEntityFilter}
              options={uniqueEntityTypes.map((e) => ({ label: e, value: e }))}
              showSearch
              optionFilterProp="label"
            />
          </Col>
          <Col xs={24} sm={24} md={8} lg={8}>
            <RangePicker
              style={{ width: '100%' }}
              value={dateRange as [Dayjs, Dayjs] | null}
              onChange={(values) => setDateRange(values as [Dayjs | null, Dayjs | null] | null)}
              placeholder={['From date', 'To date']}
            />
          </Col>
        </Row>

        <Table<AuditLog>
          rowKey="id"
          columns={columns}
          dataSource={filtered}
          loading={loading}
          scroll={{ x: 1100 }}
          locale={{
            emptyText: (
              <Empty
                description={
                  hasActiveFilter
                    ? 'No events match the current filters'
                    : 'No audit events yet'
                }
              />
            ),
          }}
          expandable={{
            expandedRowRender: (record) => (
              <div
                style={{
                  padding: '8px 16px',
                  background: token.colorFillAlter,
                  borderRadius: token.borderRadius,
                }}
              >
                <Descriptions
                  bordered
                  size="small"
                  column={{ xs: 1, sm: 2, md: 2 }}
                  styles={{
                    label: {
                      fontWeight: 600,
                      width: 140,
                      background: token.colorFillSecondary,
                      color: token.colorText,
                    },
                    content: {
                      background: token.colorBgContainer,
                      color: token.colorText,
                    },
                  }}
                >
                  <Descriptions.Item label="Event ID">#{record.id}</Descriptions.Item>
                  <Descriptions.Item label="Timestamp">
                    {dayjs(record.createdAt).format('YYYY-MM-DD HH:mm:ss')} ({dayjs(record.createdAt).fromNow()})
                  </Descriptions.Item>
                  <Descriptions.Item label="Username">
                    {record.username || <Text type="secondary">—</Text>}
                  </Descriptions.Item>
                  <Descriptions.Item label="User ID">{record.userId}</Descriptions.Item>
                  <Descriptions.Item label="Action">
                    <Tag color={actionColor(record.action)}>{record.action}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="IP Address">
                    {record.ipAddress ? <Text code>{record.ipAddress}</Text> : <Text type="secondary">—</Text>}
                  </Descriptions.Item>
                  <Descriptions.Item label="Entity Type">
                    {record.entityType ? <Tag color="geekblue">{record.entityType}</Tag> : <Text type="secondary">—</Text>}
                  </Descriptions.Item>
                  <Descriptions.Item label="Entity ID">
                    {record.entityId != null ? `#${record.entityId}` : <Text type="secondary">—</Text>}
                  </Descriptions.Item>
                  <Descriptions.Item label="Details" span={2}>
                    {record.details ? (
                      <Paragraph
                        copyable={{ text: record.details, tooltips: ['Copy', 'Copied!'] }}
                        style={{ marginBottom: 0 }}
                      >
                        <pre
                          style={{
                            margin: 0,
                            padding: 12,
                            background: token.colorBgElevated,
                            color: token.colorText,
                            border: `1px solid ${token.colorBorder}`,
                            borderRadius: token.borderRadiusSM,
                            whiteSpace: 'pre-wrap',
                            wordBreak: 'break-word',
                            maxHeight: 280,
                            overflow: 'auto',
                            fontSize: 12,
                            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                          }}
                        >
                          {formatDetails(record.details)}
                        </pre>
                      </Paragraph>
                    ) : (
                      <Text type="secondary">No additional details captured for this event</Text>
                    )}
                  </Descriptions.Item>
                </Descriptions>
              </div>
            ),
            // Always allow expand — even rows without details show the full structured view.
            rowExpandable: () => true,
          }}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            showTotal: (t, range) => `Showing ${range[0]}-${range[1]} of ${t} events`,
            onChange: (p, ps) => {
              setPage(p - 1);
              setPageSize(ps);
            },
          }}
          size="middle"
        />
      </Card>
    </div>
  );
};

export default AuditLogPage;
