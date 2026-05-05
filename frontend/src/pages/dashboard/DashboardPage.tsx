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
  PauseCircleOutlined,
  CloseCircleOutlined,
  AuditOutlined,
  SwapOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import { useAuthStore } from '../../stores/authStore';
import { paymentApi } from '../../api/payment.api';
import { notificationApi } from '../../api/notification.api';
import { complianceApi } from '../../api/compliance.api';
import { reconciliationApi, returnApi } from '../../api/exception.api';
import { settlementApi } from '../../api/settlement.api';
import PageHeader from '../../components/common/PageHeader';
import { paymentRef } from '../../utils/paymentRef';
import type {
  PaymentOrder,
  PaymentStatus,
  Notification,
  Hold,
  ReconciliationRecord,
} from '../../types';

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
  const isCompliance = user?.role === 'COMPLIANCE';
  const isRecon = user?.role === 'RECONCILIATION';
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
  // Compliance-specific state
  const [activeHolds, setActiveHolds] = useState<Hold[]>([]);
  const [complianceStats, setComplianceStats] = useState({
    activeHolds: 0,
    pendingScreening: 0,
    releasedToday: 0,
    rejectedToday: 0,
  });
  // Reconciliation-specific state
  const [openBreaks, setOpenBreaks] = useState<ReconciliationRecord[]>([]);
  const [reconStats, setReconStats] = useState({
    openBreaks: 0,
    returnsToProcess: 0,
    failedSettlements: 0,
    matchedToday: 0,
  });

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        if (isCompliance) {
          // Compliance analyst sees a hold-centric view, not a generic payment dashboard.
          const startOfDay = dayjs().startOf('day');
          const [activeRes, screeningRes, releasedRes, rejectedRes, notificationsRes] =
            await Promise.allSettled([
              complianceApi.getHolds({ status: 'ACTIVE', size: 5 }),
              paymentApi.getAll({ status: 'SCREENING', size: 1 }),
              complianceApi.getHolds({ status: 'RELEASED', size: 100 }),
              complianceApi.getHolds({ status: 'REJECTED', size: 100 }),
              notificationApi.getAll({ page: 0, size: 5 }),
            ]);

          let activeCount = 0;
          if (activeRes.status === 'fulfilled') {
            const paged = activeRes.value.data.data;
            activeCount = paged.totalElements;
            setActiveHolds(paged.content);
          }
          const screeningCount =
            screeningRes.status === 'fulfilled' ? screeningRes.value.data.data.totalElements : 0;
          const releasedToday =
            releasedRes.status === 'fulfilled'
              ? releasedRes.value.data.data.content.filter((h) =>
                  h.releasedAt && dayjs(h.releasedAt).isAfter(startOfDay)
                ).length
              : 0;
          const rejectedToday =
            rejectedRes.status === 'fulfilled'
              ? rejectedRes.value.data.data.content.filter((h) =>
                  h.releasedAt && dayjs(h.releasedAt).isAfter(startOfDay)
                ).length
              : 0;

          setComplianceStats({
            activeHolds: activeCount,
            pendingScreening: screeningCount,
            releasedToday,
            rejectedToday,
          });

          if (notificationsRes.status === 'fulfilled') {
            setNotifications(notificationsRes.value.data.data.content);
          }
          return;
        }

        if (isRecon) {
          // Recon analyst sees a break-and-returns view, not a generic payment dashboard.
          // The reconciliation API doesn't expose a `resolved` filter, so we fetch a
          // wide page of UNMATCHED + DISCREPANCY records and filter client-side.
          const todayIso = dayjs().format('YYYY-MM-DD');
          const [unmatchedRes, discrepancyRes, notifiedRes, processingRes, failedBatchRes, matchedTodayRes, notificationsRes] =
            await Promise.allSettled([
              reconciliationApi.getAll({ result: 'UNMATCHED', size: 50 }),
              reconciliationApi.getAll({ result: 'DISCREPANCY', size: 50 }),
              returnApi.getAll({ status: 'NOTIFIED', size: 1 }),
              returnApi.getAll({ status: 'PROCESSING', size: 1 }),
              settlementApi.getBatches({ status: 'FAILED', size: 1 }),
              reconciliationApi.getAll({ result: 'MATCHED', reconDate: todayIso, size: 1 }),
              notificationApi.getAll({ page: 0, size: 5 }),
            ]);

          const openUnmatched =
            unmatchedRes.status === 'fulfilled'
              ? unmatchedRes.value.data.data.content.filter((r) => !r.resolved)
              : [];
          const openDiscrepancy =
            discrepancyRes.status === 'fulfilled'
              ? discrepancyRes.value.data.data.content.filter((r) => !r.resolved)
              : [];
          // Newest first across both result types — top 5 land in the queue table.
          const queue = [...openUnmatched, ...openDiscrepancy]
            .sort((a, b) => new Date(b.reconDate).getTime() - new Date(a.reconDate).getTime())
            .slice(0, 5);
          setOpenBreaks(queue);

          const returnsToProcess =
            (notifiedRes.status === 'fulfilled' ? notifiedRes.value.data.data.totalElements : 0) +
            (processingRes.status === 'fulfilled' ? processingRes.value.data.data.totalElements : 0);
          const failedSettlements =
            failedBatchRes.status === 'fulfilled' ? failedBatchRes.value.data.data.totalElements : 0;
          const matchedToday =
            matchedTodayRes.status === 'fulfilled' ? matchedTodayRes.value.data.data.totalElements : 0;

          setReconStats({
            openBreaks: openUnmatched.length + openDiscrepancy.length,
            returnsToProcess,
            failedSettlements,
            matchedToday,
          });

          if (notificationsRes.status === 'fulfilled') {
            setNotifications(notificationsRes.value.data.data.content);
          }
          return;
        }

        // Default (non-compliance) dashboard: two listing calls — 5 most-recent rows
        // for the table, and a wide page (1000) used purely to compute accurate
        // status breakdowns. The backend already scopes /payments by user/role.
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
  }, [isCompliance, isRecon]);

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

  // Columns for the Compliance "Active Holds" table.
  const holdColumns = [
    {
      title: 'Payment',
      dataIndex: 'paymentId',
      key: 'paymentId',
      render: (paymentId: number) => (
        <Text style={{ fontFamily: 'monospace', fontSize: 12 }}>#{paymentId}</Text>
      ),
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
    },
    {
      title: 'Placed',
      dataIndex: 'placedAt',
      key: 'placedAt',
      render: (date: string) => dayjs(date).fromNow(),
    },
    {
      title: '',
      key: 'action',
      width: 80,
      render: (_: unknown, record: Hold) => (
        <a onClick={(e) => { e.stopPropagation(); navigate(`/compliance/holds`); }} style={{ fontWeight: 500 }}>
          Review →
        </a>
      ),
    },
  ];

  // Columns for the Reconciliation "Open Breaks" queue.
  const breakColumns = [
    {
      title: 'Reference',
      dataIndex: 'referenceId',
      key: 'referenceId',
      render: (referenceId: number, record: ReconciliationRecord) => (
        <Text style={{ fontFamily: 'monospace', fontSize: 12 }}>
          {record.source}#{referenceId}
        </Text>
      ),
    },
    {
      title: 'Result',
      dataIndex: 'result',
      key: 'result',
      render: (val: ReconciliationRecord['result']) => (
        <Tag color={val === 'UNMATCHED' ? 'red' : val === 'DISCREPANCY' ? 'volcano' : 'orange'}>
          {val}
        </Tag>
      ),
    },
    {
      title: 'Amount',
      dataIndex: 'amount',
      key: 'amount',
      render: (amount: number, record: ReconciliationRecord) =>
        amount ? `${record.currency || ''} ${amount.toLocaleString()}` : '-',
    },
    {
      title: 'Date',
      dataIndex: 'reconDate',
      key: 'reconDate',
      render: (date: string) => dayjs(date).fromNow(),
    },
  ];

  if (isRecon) {
    return (
      <Spin spinning={loading}>
        <div>
          <PageHeader
            title={`Welcome back, ${user?.username}`}
            subtitle={`${roleGreeting()} · ${dayjs().format('dddd, MMMM D, YYYY')}`}
          />

          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="Open Breaks"
                value={reconStats.openBreaks}
                icon={<ExclamationCircleOutlined />}
                variant="error"
                onClick={() => navigate('/exceptions/reconciliation')}
              />
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="Returns to Process"
                value={reconStats.returnsToProcess}
                icon={<SwapOutlined />}
                variant="warning"
                onClick={() => navigate('/exceptions/returns')}
              />
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="Failed Settlements"
                value={reconStats.failedSettlements}
                icon={<BankOutlined />}
                variant="error"
                onClick={() => navigate('/settlement')}
              />
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="Matched Today"
                value={reconStats.matchedToday}
                icon={<CheckCircleOutlined />}
                variant="success"
                onClick={() => navigate('/exceptions/reconciliation')}
              />
            </Col>
          </Row>

          <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
            <Col xs={24} lg={14}>
              <Card
                title="Open Breaks Queue"
                extra={
                  <a onClick={() => navigate('/exceptions/reconciliation')} style={{ fontWeight: 500 }}>
                    View All <ArrowRightOutlined style={{ fontSize: 11 }} />
                  </a>
                }
              >
                {openBreaks.length > 0 ? (
                  <Table
                    dataSource={openBreaks}
                    columns={breakColumns}
                    rowKey="id"
                    pagination={false}
                    size="small"
                    onRow={() => ({
                      onClick: () => navigate('/exceptions/reconciliation'),
                      style: { cursor: 'pointer' },
                    })}
                  />
                ) : (
                  <div style={{ padding: '32px 0', textAlign: 'center' }}>
                    <Text type="secondary">No open breaks. Books are clean.</Text>
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

  if (isCompliance) {
    return (
      <Spin spinning={loading}>
        <div>
          <PageHeader
            title={`Welcome back, ${user?.username}`}
            subtitle={`${roleGreeting()} · ${dayjs().format('dddd, MMMM D, YYYY')}`}
          />

          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="Active Holds"
                value={complianceStats.activeHolds}
                icon={<PauseCircleOutlined />}
                variant="warning"
                onClick={() => navigate('/compliance/holds')}
              />
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="Pending Screening"
                value={complianceStats.pendingScreening}
                icon={<AuditOutlined />}
                variant="info"
                onClick={() => navigate('/compliance')}
              />
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="Released Today"
                value={complianceStats.releasedToday}
                icon={<CheckCircleOutlined />}
                variant="success"
                onClick={() => navigate('/compliance/holds')}
              />
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <StatCard
                title="Rejected Today"
                value={complianceStats.rejectedToday}
                icon={<CloseCircleOutlined />}
                variant="error"
                onClick={() => navigate('/compliance/holds')}
              />
            </Col>
          </Row>

          <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
            <Col xs={24} lg={14}>
              <Card
                title="Active Hold Queue"
                extra={
                  <a onClick={() => navigate('/compliance/holds')} style={{ fontWeight: 500 }}>
                    View All <ArrowRightOutlined style={{ fontSize: 11 }} />
                  </a>
                }
              >
                {activeHolds.length > 0 ? (
                  <Table
                    dataSource={activeHolds}
                    columns={holdColumns}
                    rowKey="id"
                    pagination={false}
                    size="small"
                    onRow={(record) => ({
                      onClick: () => navigate(`/payments/${record.paymentId}`),
                      style: { cursor: 'pointer' },
                    })}
                  />
                ) : (
                  <div style={{ padding: '32px 0', textAlign: 'center' }}>
                    <Text type="secondary">No active holds. Queue is clear.</Text>
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
