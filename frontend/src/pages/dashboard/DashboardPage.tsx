import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { Card, Col, Row, Statistic, Typography, Table, Tag, Space, Spin, message, Button } from 'antd';
import {
  TransactionOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  WarningOutlined,
  SafetyCertificateOutlined,
  BankOutlined,
  PlusOutlined,
  ArrowRightOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import { useAuthStore } from '../../stores/authStore';
import { paymentApi } from '../../api/payment.api';
import { notificationApi } from '../../api/notification.api';
import PageHeader from '../../components/common/PageHeader';
import { paymentRef } from '../../utils/paymentRef';
import type { PaymentOrder, PaymentStatus, Notification } from '../../types';

dayjs.extend(relativeTime);

const { Text } = Typography;

const statusColorMap: Record<PaymentStatus, string> = {
  INITIATED: 'default',
  VALIDATED: 'processing',
  VALIDATION_FAILED: 'error',
  SCREENING: 'warning',
  HELD: 'orange',
  ROUTED: 'cyan',
  PROCESSING: 'blue',
  COMPLETED: 'success',
  FAILED: 'error',
  REVERSED: 'purple',
};

type IconVariant = 'brand' | 'success' | 'warning' | 'error' | 'info' | 'purple' | 'teal';

function StatCard({
  title,
  value,
  icon,
  variant,
  onClick,
  suffix,
}: {
  title: string;
  value: number | string;
  icon: React.ReactNode;
  variant: IconVariant;
  onClick?: () => void;
  suffix?: React.ReactNode;
}) {
  return (
    <Card hoverable={!!onClick} onClick={onClick} bodyStyle={{ padding: 20 }}>
      <div className={`pr-stat-icon ${variant}`}>{icon}</div>
      <Statistic title={title} value={value} suffix={suffix} valueStyle={{ fontSize: 26, fontWeight: 700 }} />
    </Card>
  );
}

export default function DashboardPage() {
  const { user } = useAuthStore();
  const isCustomer = user?.role === 'CUSTOMER';
  const navigate = useNavigate();
  const [payments, setPayments] = useState<PaymentOrder[]>([]);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({
    total: 0,
    completed: 0,
    processing: 0,
    exceptions: 0,
  });

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        // Two listing calls: 5 most-recent rows for the table, and a wide page
        // (1000 rows) used purely to compute accurate status breakdowns.
        // The backend already scopes /payments by user/role, so this works
        // for customers (their own payments) and staff (everyone's) alike.
        const [recentRes, allRes, notificationsRes] = await Promise.allSettled([
          paymentApi.getAll({ page: 0, size: 5 }),
          paymentApi.getAll({ page: 0, size: 1000 }),
          notificationApi.getAll({ page: 0, size: 5 }),
        ]);

        if (recentRes.status === 'fulfilled') {
          setPayments(recentRes.value.data.data.content);
        }

        if (allRes.status === 'fulfilled') {
          const all = allRes.value.data.data;
          const rows = all.content;
          setStats({
            total: all.totalElements,
            completed: rows.filter((p) => p.status === 'COMPLETED').length,
            processing: rows.filter((p) =>
              ['INITIATED', 'VALIDATED', 'SCREENING', 'ROUTED', 'PROCESSING'].includes(p.status)
            ).length,
            exceptions: rows.filter((p) =>
              ['FAILED', 'VALIDATION_FAILED', 'HELD'].includes(p.status)
            ).length,
          });
        }

        if (notificationsRes.status === 'fulfilled') {
          setNotifications(notificationsRes.value.data.data.content);
        }
      } catch {
        message.error('Failed to load dashboard data');
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const paymentColumns = [
    {
      title: 'Reference No',
      key: 'ref',
      width: isCustomer ? 140 : 180,
      render: (_: unknown, record: PaymentOrder) => {
        const ref = paymentRef(record);
        return (
          <Text style={{ fontFamily: 'monospace', fontSize: 12 }}>
            {isCustomer ? ref : `#${record.id} · ${ref}`}
          </Text>
        );
      },
    },
    {
      title: 'Amount',
      key: 'amount',
      render: (_: unknown, record: PaymentOrder) => (
        <Text strong>
          {record.currency} {record.amount?.toLocaleString()}
        </Text>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: PaymentStatus) => <Tag color={statusColorMap[status]}>{status}</Tag>,
    },
    {
      title: 'Channel',
      dataIndex: 'initiationChannel',
      key: 'channel',
      render: (channel: string) => <Tag>{channel}</Tag>,
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => dayjs(date).fromNow(),
    },
  ];

  const roleGreeting = () => {
    switch (user?.role) {
      case 'ADMIN':
        return 'System Administrator';
      case 'OPERATIONS':
        return 'Operations Officer';
      case 'COMPLIANCE':
        return 'Compliance Analyst';
      case 'RECONCILIATION':
        return 'Reconciliation Analyst';
      case 'CUSTOMER':
        return 'Customer';
      default:
        return user?.role;
    }
  };

  return (
    <Spin spinning={loading}>
      <div>
        <PageHeader
          title={`Welcome back, ${user?.username}`}
          subtitle={`${roleGreeting()} · ${dayjs().format('dddd, MMMM D, YYYY')}`}
          extra={
            isCustomer ? (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/payments/new')}>
                New Payment
              </Button>
            ) : undefined
          }
        />

        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              title="Total Payments"
              value={stats.total}
              icon={<TransactionOutlined />}
              variant="brand"
              onClick={() => navigate('/payments')}
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              title="Completed"
              value={stats.completed}
              icon={<CheckCircleOutlined />}
              variant="success"
              onClick={() => navigate('/payments')}
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              title="Processing"
              value={stats.processing}
              icon={<ClockCircleOutlined />}
              variant="warning"
              onClick={() => navigate('/payments')}
            />
          </Col>
          {!isCustomer && (
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="Exceptions"
                value={stats.exceptions}
                icon={<WarningOutlined />}
                variant="error"
                onClick={() => navigate('/exceptions')}
              />
            </Col>
          )}
        </Row>

        {!isCustomer && (
          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="Compliance"
                value="View"
                icon={<SafetyCertificateOutlined />}
                variant="purple"
                onClick={() => navigate('/compliance')}
                suffix={<ArrowRightOutlined style={{ fontSize: 14, marginLeft: 6 }} />}
              />
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="Settlement"
                value="View"
                icon={<BankOutlined />}
                variant="teal"
                onClick={() => navigate('/settlement')}
                suffix={<ArrowRightOutlined style={{ fontSize: 14, marginLeft: 6 }} />}
              />
            </Col>
          </Row>
        )}

        <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
          <Col xs={24} lg={14}>
            <Card
              title="Recent Payments"
              extra={
                <a onClick={() => navigate('/payments')} style={{ fontWeight: 500 }}>
                  View All <ArrowRightOutlined style={{ fontSize: 11 }} />
                </a>
              }
            >
              {payments.length > 0 ? (
                <Table
                  dataSource={payments}
                  columns={paymentColumns}
                  rowKey="id"
                  pagination={false}
                  size="small"
                  onRow={(record) => ({
                    onClick: () => navigate(`/payments/${record.id}`),
                    style: { cursor: 'pointer' },
                  })}
                />
              ) : (
                <div style={{ padding: '32px 0', textAlign: 'center' }}>
                  <Text type="secondary">No recent payments. Create a payment to get started.</Text>
                </div>
              )}
            </Card>
          </Col>
          <Col xs={24} lg={10}>
            <Card
              title="Recent Notifications"
              extra={
                <a onClick={() => navigate('/notifications')} style={{ fontWeight: 500 }}>
                  View All <ArrowRightOutlined style={{ fontSize: 11 }} />
                </a>
              }
            >
              {notifications.length > 0 ? (
                <Space direction="vertical" style={{ width: '100%' }} size={8}>
                  {notifications.map((n) => {
                    const accent =
                      n.severity === 'CRITICAL'
                        ? 'var(--pr-error)'
                        : n.severity === 'ERROR'
                        ? '#ff7a45'
                        : n.severity === 'WARNING'
                        ? 'var(--pr-warning)'
                        : 'var(--pr-brand-1)';
                    return (
                      <div
                        key={n.id}
                        style={{
                          padding: '12px 14px',
                          background: n.isRead ? 'transparent' : 'var(--pr-brand-grad-soft)',
                          border: '1px solid var(--pr-border)',
                          borderLeft: `3px solid ${accent}`,
                          borderRadius: 10,
                          transition: 'background 160ms ease',
                        }}
                      >
                        <Text strong={!n.isRead} style={{ display: 'block', marginBottom: 4 }}>
                          {n.title}
                        </Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {dayjs(n.createdAt).fromNow()}
                          {' · '}
                          <Tag style={{ fontSize: 11 }}>{n.category}</Tag>
                        </Text>
                      </div>
                    );
                  })}
                </Space>
              ) : (
                <div style={{ padding: '32px 0', textAlign: 'center' }}>
                  <Text type="secondary">No recent notifications.</Text>
                </div>
              )}
            </Card>
          </Col>
        </Row>
      </div>
    </Spin>
  );
}
