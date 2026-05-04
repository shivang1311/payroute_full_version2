import { useEffect, useMemo, useState } from 'react';
import {
  Card,
  Col,
  Row,
  Statistic,
  Typography,
  DatePicker,
  Space,
  Spin,
  Empty,
  Button,
  message,
} from 'antd';
import {
  TransactionOutlined,
  DollarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  WarningOutlined,
  FundOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from 'recharts';
import dayjs, { Dayjs } from 'dayjs';
import { dashboardApi } from '../../api/dashboard.api';
import { slaApi } from '../../api/sla.api';
import type { PaymentStats, RailStat, FeeIncomeStats } from '../../types/dashboard';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

const STATUS_COLORS: Record<string, string> = {
  INITIATED: '#8c8c8c',
  VALIDATED: '#1890ff',
  VALIDATION_FAILED: '#ff4d4f',
  SCREENING: '#faad14',
  HELD: '#fa8c16',
  ROUTED: '#13c2c2',
  PROCESSING: '#2f54eb',
  COMPLETED: '#52c41a',
  FAILED: '#ff4d4f',
  REVERSED: '#722ed1',
};

const RAIL_COLOR = '#1677ff';
const FEE_COLOR = '#52c41a';
const CHANNEL_COLOR = '#722ed1';

export default function OpsDashboardPage() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(30, 'day').startOf('day'), dayjs().endOf('day')]);
  const [loading, setLoading] = useState(false);
  const [paymentStats, setPaymentStats] = useState<PaymentStats | null>(null);
  const [railStats, setRailStats] = useState<RailStat[]>([]);
  const [feeStats, setFeeStats] = useState<FeeIncomeStats | null>(null);
  const [breachCount, setBreachCount] = useState<number>(0);

  const fromIso = range[0].format('YYYY-MM-DDTHH:mm:ss');
  const toIso = range[1].format('YYYY-MM-DDTHH:mm:ss');
  const fromDate = range[0].format('YYYY-MM-DD');
  const toDate = range[1].format('YYYY-MM-DD');

  const fetchAll = async () => {
    setLoading(true);
    try {
      const [ps, rs, fs, bc] = await Promise.all([
        dashboardApi.getPaymentStats(fromIso, toIso),
        dashboardApi.getRailStats(fromIso, toIso),
        dashboardApi.getFeeIncomeStats(fromDate, toDate),
        slaApi.getBreachCount(fromIso, toIso).catch(() => ({ data: { data: { count: 0 } } })),
      ]);
      setPaymentStats(ps.data.data);
      setRailStats(rs.data.data || []);
      setFeeStats(fs.data.data);
      setBreachCount(bc.data.data?.count ?? 0);
    } catch (err) {
      console.error(err);
      message.error('Failed to load dashboard stats');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fromIso, toIso]);

  const statusData = useMemo(() => {
    if (!paymentStats) return [];
    return Object.entries(paymentStats.byStatus).map(([name, value]) => ({ name, value }));
  }, [paymentStats]);

  const channelData = useMemo(() => {
    if (!paymentStats) return [];
    return Object.entries(paymentStats.byChannel).map(([name, value]) => ({ name, value }));
  }, [paymentStats]);

  const volumeData = useMemo(() => {
    if (!paymentStats) return [];
    return paymentStats.byDay.map((d) => ({
      date: d.date,
      count: d.count,
      amount: Number(d.amount) || 0,
    }));
  }, [paymentStats]);

  const feeData = useMemo(() => {
    if (!feeStats) return [];
    return feeStats.byDay.map((d) => ({ date: d.date, amount: Number(d.amount) || 0 }));
  }, [feeStats]);

  const formatCurrency = (n: number) =>
    n >= 1_00_00_000 ? `₹${(n / 1_00_00_000).toFixed(2)}Cr` :
    n >= 1_00_000 ? `₹${(n / 1_00_000).toFixed(2)}L` :
    n >= 1_000 ? `₹${(n / 1_000).toFixed(1)}K` :
    `₹${n.toFixed(0)}`;

  return (
    <Spin spinning={loading}>
      <div>
        <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
          <div>
            <Title level={3} style={{ margin: 0 }}>Operations & MIS Dashboard</Title>
            <Text type="secondary">Real-time payment KPIs, volume trends, and fee income</Text>
          </div>
          <Space>
            <RangePicker
              value={range}
              onChange={(v) => {
                if (v && v[0] && v[1]) setRange([v[0].startOf('day'), v[1].endOf('day')]);
              }}
              allowClear={false}
            />
            <Button icon={<ReloadOutlined />} onClick={fetchAll}>Refresh</Button>
          </Space>
        </Space>

        {/* KPI Cards */}
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} lg={8} xl={4}>
            <Card>
              <Statistic
                title="Total Txns"
                value={paymentStats?.totalCount ?? 0}
                prefix={<TransactionOutlined />}
                valueStyle={{ color: '#1677ff' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={8} xl={4}>
            <Card>
              <Statistic
                title="Total Amount"
                value={formatCurrency(Number(paymentStats?.totalAmount ?? 0))}
                prefix={<DollarOutlined />}
                valueStyle={{ color: '#13c2c2' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={8} xl={4}>
            <Card>
              <Statistic
                title="Success Rate"
                value={paymentStats?.successRate ?? 0}
                suffix="%"
                precision={2}
                prefix={<CheckCircleOutlined />}
                valueStyle={{ color: '#52c41a' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={8} xl={4}>
            <Card>
              <Statistic
                title="Avg TAT"
                value={paymentStats?.avgTatSeconds ?? 0}
                suffix="s"
                precision={1}
                prefix={<ClockCircleOutlined />}
                valueStyle={{ color: '#faad14' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={8} xl={4}>
            <Card>
              <Statistic
                title="Fee Income"
                value={formatCurrency(Number(feeStats?.total ?? 0))}
                prefix={<FundOutlined />}
                valueStyle={{ color: '#722ed1' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={8} xl={4}>
            <Card>
              <Statistic
                title="Failed"
                value={paymentStats?.failedCount ?? 0}
                prefix={<WarningOutlined />}
                valueStyle={{ color: '#ff4d4f' }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={8} xl={4}>
            <Card>
              <Statistic
                title="SLA Breaches"
                value={breachCount}
                prefix={<ClockCircleOutlined />}
                valueStyle={{ color: breachCount > 0 ? '#ff4d4f' : '#8c8c8c' }}
              />
            </Card>
          </Col>
        </Row>

        {/* Row 2: Daily volume line chart */}
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col xs={24}>
            <Card title="Daily Payment Volume" size="small">
              {volumeData.length === 0 ? (
                <Empty description="No data for selected range" />
              ) : (
                <ResponsiveContainer width="100%" height={280}>
                  <LineChart data={volumeData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="date" />
                    <YAxis yAxisId="left" label={{ value: 'Count', angle: -90, position: 'insideLeft' }} />
                    <YAxis yAxisId="right" orientation="right" label={{ value: 'Amount', angle: 90, position: 'insideRight' }} />
                    <Tooltip />
                    <Legend />
                    <Line yAxisId="left" type="monotone" dataKey="count" stroke="#1677ff" strokeWidth={2} name="Txn Count" />
                    <Line yAxisId="right" type="monotone" dataKey="amount" stroke="#52c41a" strokeWidth={2} name="Amount" />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </Card>
          </Col>
        </Row>

        {/* Row 3: Status pie + Rail bar */}
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col xs={24} lg={12}>
            <Card title="Status Breakdown" size="small">
              {statusData.length === 0 ? (
                <Empty description="No data" />
              ) : (
                <ResponsiveContainer width="100%" height={280}>
                  <PieChart>
                    <Pie data={statusData} dataKey="value" nameKey="name" outerRadius={90} label>
                      {statusData.map((entry) => (
                        <Cell key={entry.name} fill={STATUS_COLORS[entry.name] || '#8c8c8c'} />
                      ))}
                    </Pie>
                    <Tooltip />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              )}
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title="Rail Distribution" size="small">
              {railStats.length === 0 ? (
                <Empty description="No rail instructions in range" />
              ) : (
                <ResponsiveContainer width="100%" height={280}>
                  <BarChart data={railStats}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="rail" />
                    <YAxis />
                    <Tooltip />
                    <Bar dataKey="count" fill={RAIL_COLOR} name="Instructions" />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </Card>
          </Col>
        </Row>

        {/* Row 4: Fee income + Channel */}
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col xs={24} lg={12}>
            <Card title="Fee Income by Day" size="small">
              {feeData.length === 0 ? (
                <Empty description="No fee entries in range" />
              ) : (
                <ResponsiveContainer width="100%" height={260}>
                  <LineChart data={feeData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="date" />
                    <YAxis />
                    <Tooltip />
                    <Line type="monotone" dataKey="amount" stroke={FEE_COLOR} strokeWidth={2} name="Fee Income" />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title="Initiation Channels" size="small">
              {channelData.length === 0 ? (
                <Empty description="No channel data" />
              ) : (
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={channelData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="name" />
                    <YAxis />
                    <Tooltip />
                    <Bar dataKey="value" fill={CHANNEL_COLOR} name="Payments" />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </Card>
          </Col>
        </Row>
      </div>
    </Spin>
  );
}
