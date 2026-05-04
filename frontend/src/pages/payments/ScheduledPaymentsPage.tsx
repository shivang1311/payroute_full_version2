import { useEffect, useState } from 'react';
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  PauseOutlined,
  PlayCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  ScheduleOutlined,
  LockOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { Dayjs } from 'dayjs';
import { scheduledApi } from '../../api/scheduled.api';
import { accountApi } from '../../api/party.api';
import { useAuthStore } from '../../stores/authStore';
import { paymentRef } from '../../utils/paymentRef';
import BeneficiaryPicker from '../../components/common/BeneficiaryPicker';
import type {
  ScheduledPayment,
  ScheduledPaymentRequest,
  ScheduledPaymentStatus,
  ScheduleType,
} from '../../types/scheduled';
import type { Account } from '../../types';

const { Title, Text, Paragraph } = Typography;

const statusColor: Record<ScheduledPaymentStatus, string> = {
  ACTIVE: 'green',
  PAUSED: 'orange',
  COMPLETED: 'blue',
  CANCELLED: 'default',
  FAILED: 'red',
};

interface FormValues {
  name?: string;
  debtorAccountId: number;
  creditorAccountId: number;
  amount: number;
  currency: string;
  purposeCode?: string;
  remittanceInfo?: string;
  scheduleType: ScheduleType;
  startAt: Dayjs;
  endAt?: Dayjs;
  maxRuns?: number;
}

export default function ScheduledPaymentsPage() {
  const [rows, setRows] = useState<ScheduledPayment[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const { user } = useAuthStore();
  const isCustomer = user?.role === 'CUSTOMER';

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<ScheduledPayment | null>(null);
  const [form] = Form.useForm<FormValues>();

  const load = async () => {
    setLoading(true);
    try {
      const res = await scheduledApi.list({ page: page - 1, size });
      const paged = res.data.data;
      setRows(paged?.content ?? []);
      setTotal(paged?.totalElements ?? 0);
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Failed to load schedules');
    } finally {
      setLoading(false);
    }
  };

  const loadAccounts = async () => {
    try {
      // Customers only see their own accounts; staff see all
      const params: { page: number; size: number; partyId?: number } = { page: 0, size: 200 };
      if (isCustomer) {
        if (!user?.partyId) {
          setAccounts([]);
          return;
        }
        params.partyId = user.partyId;
      }
      const res = await accountApi.getAll(params);
      setAccounts(res.data.data?.content ?? []);
    } catch {
      /* non-fatal */
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size]);

  useEffect(() => {
    loadAccounts();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isCustomer, user?.partyId]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      currency: 'INR',
      scheduleType: 'ONCE',
      startAt: dayjs().add(1, 'minute'),
    });
    setFormOpen(true);
  };

  const openEdit = (row: ScheduledPayment) => {
    setEditing(row);
    form.setFieldsValue({
      name: row.name,
      debtorAccountId: row.debtorAccountId,
      creditorAccountId: row.creditorAccountId,
      amount: row.amount,
      currency: row.currency,
      purposeCode: row.purposeCode,
      remittanceInfo: row.remittanceInfo,
      scheduleType: row.scheduleType,
      startAt: dayjs(row.startAt),
      endAt: row.endAt ? dayjs(row.endAt) : undefined,
      maxRuns: row.maxRuns,
    });
    setFormOpen(true);
  };

  // PIN gate for customer-initiated schedules (mirrors CreatePaymentPage flow)
  const [schedulePinModalOpen, setSchedulePinModalOpen] = useState(false);
  const [schedulePin, setSchedulePin] = useState('');
  const [pendingScheduleReq, setPendingScheduleReq] = useState<ScheduledPaymentRequest | null>(null);

  const persistSchedule = async (req: ScheduledPaymentRequest) => {
    try {
      if (editing) {
        await scheduledApi.update(editing.id, req);
        message.success('Schedule updated');
      } else {
        await scheduledApi.create(req);
        message.success('Schedule created');
      }
      setFormOpen(false);
      load();
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Save failed');
    }
  };

  const submitForm = async () => {
    try {
      const values = await form.validateFields();
      const req: ScheduledPaymentRequest = {
        name: values.name,
        debtorAccountId: values.debtorAccountId,
        creditorAccountId: values.creditorAccountId,
        amount: values.amount,
        currency: values.currency,
        purposeCode: values.purposeCode,
        remittanceInfo: values.remittanceInfo,
        scheduleType: values.scheduleType,
        startAt: values.startAt.format('YYYY-MM-DDTHH:mm:ss'),
        endAt: values.endAt ? values.endAt.format('YYYY-MM-DDTHH:mm:ss') : undefined,
        maxRuns: values.maxRuns,
      };

      // Customer needs to authorise the schedule with their PIN — same gate as
      // a one-off payment. Editing existing schedules doesn't change debtor/amount,
      // so a PIN re-prompt is also requested for edits to stay consistent.
      if (isCustomer) {
        setPendingScheduleReq(req);
        setSchedulePin('');
        setSchedulePinModalOpen(true);
        return;
      }

      await persistSchedule(req);
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e?.response?.data?.message ?? 'Save failed');
    }
  };

  const confirmSchedulePinAndSubmit = async () => {
    if (!pendingScheduleReq) return;
    if (!/^\d{4,6}$/.test(schedulePin)) {
      message.error('PIN must be 4 to 6 digits');
      return;
    }
    setSchedulePinModalOpen(false);
    await persistSchedule({ ...pendingScheduleReq, transactionPin: schedulePin });
    setSchedulePin('');
    setPendingScheduleReq(null);
  };

  const handlePause = async (id: number) => {
    try {
      await scheduledApi.pause(id);
      message.success('Paused');
      load();
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Pause failed');
    }
  };

  const handleResume = async (id: number) => {
    try {
      await scheduledApi.resume(id);
      message.success('Resumed');
      load();
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Resume failed');
    }
  };

  const handleCancel = async (id: number) => {
    try {
      await scheduledApi.cancel(id);
      message.success('Cancelled');
      load();
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Cancel failed');
    }
  };

  const accountLabel = (id: number) => {
    const a = accounts.find((x) => x.id === id);
    if (!a) return isCustomer ? '—' : `#${id}`;
    const primary = a.alias || a.accountNumber;
    // Customers don't see the internal account id; staff do for ops/audit
    return isCustomer ? primary : `${primary} (#${a.id})`;
  };

  /** Mask all but the last 4 digits — used on account numbers shown to customers. */
  const maskAccount = (acc?: string) => {
    if (!acc) return '—';
    if (acc.length <= 4) return acc;
    return '••••' + acc.slice(-4);
  };

  /** Friendly two-line cell: party / alias on top, masked account number underneath. */
  const renderAccountCell = (id: number) => {
    const a = accounts.find((x) => x.id === id);
    if (!a) {
      return <Text type="secondary" style={{ fontSize: 12 }}>{isCustomer ? '—' : `#${id}`}</Text>;
    }
    const primary = a.partyName || a.alias || a.accountType || 'Account';
    const secondary = isCustomer ? maskAccount(a.accountNumber) : `${a.accountNumber} · #${a.id}`;
    return (
      <div style={{ lineHeight: 1.3 }}>
        <div style={{ fontWeight: 500, fontSize: 13 }}>{primary}</div>
        <div style={{ fontSize: 11, color: 'var(--pr-text-muted)', fontFamily: 'monospace' }}>
          {secondary}
        </div>
      </div>
    );
  };

  const columns: ColumnsType<ScheduledPayment> = [
    // Internal schedule id is for staff/audit only; customers don't need to see it
    ...(!isCustomer ? ([{ title: 'ID', dataIndex: 'id', width: 60 }] as ColumnsType<ScheduledPayment>) : []),
    {
      title: 'Name',
      dataIndex: 'name',
      render: (v?: string) => v || <Text type="secondary">—</Text>,
    },
    {
      title: 'Type',
      dataIndex: 'scheduleType',
      width: 90,
      render: (v: ScheduleType) => <Tag color={v === 'ONCE' ? 'default' : 'blue'}>{v}</Tag>,
    },
    {
      title: 'Amount',
      key: 'amount',
      width: 130,
      render: (_, r) => (
        <Text strong>
          {r.currency} {Number(r.amount).toFixed(2)}
        </Text>
      ),
    },
    {
      title: 'From',
      key: 'from',
      width: 180,
      render: (_, r) => renderAccountCell(r.debtorAccountId),
    },
    {
      title: 'To',
      key: 'to',
      width: 180,
      render: (_, r) => renderAccountCell(r.creditorAccountId),
    },
    {
      title: 'Next Run',
      dataIndex: 'nextRunAt',
      width: 170,
      render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '—'),
    },
    {
      title: 'Runs',
      key: 'runs',
      width: 90,
      render: (_, r) => `${r.runsCount}${r.maxRuns ? ` / ${r.maxRuns}` : ''}`,
    },
    {
      title: 'Last Payment',
      dataIndex: 'lastPaymentId',
      width: 150,
      render: (v?: number) => {
        if (!v) return '—';
        const label = isCustomer ? paymentRef(v) : `#${v}`;
        return (
          <a href={`/payments/${v}`} style={{ fontFamily: 'monospace', fontSize: 12 }}>
            {label}
          </a>
        );
      },
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: ScheduledPaymentStatus) => <Tag color={statusColor[s]}>{s}</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 200,
      render: (_, row) => (
        <Space size="small">
          {row.status === 'ACTIVE' && (
            <Tooltip title="Pause">
              <Button size="small" icon={<PauseOutlined />} onClick={() => handlePause(row.id)} />
            </Tooltip>
          )}
          {row.status === 'PAUSED' && (
            <Tooltip title="Resume">
              <Button
                size="small"
                icon={<PlayCircleOutlined />}
                onClick={() => handleResume(row.id)}
              />
            </Tooltip>
          )}
          {(row.status === 'ACTIVE' || row.status === 'PAUSED') && (
            <Tooltip title="Edit">
              <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(row)} />
            </Tooltip>
          )}
          {row.status !== 'CANCELLED' && row.status !== 'COMPLETED' && (
            <Tooltip title="Cancel">
              <Popconfirm title="Cancel this schedule?" onConfirm={() => handleCancel(row.id)}>
                <Button size="small" danger icon={<DeleteOutlined />} />
              </Popconfirm>
            </Tooltip>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Title level={3}>
        <ScheduleOutlined /> Scheduled Payments
      </Title>
      <Paragraph type="secondary">
        Create one-time or recurring payments that fire automatically. The scheduler polls every 30 seconds
        — schedules become payments and flow through the normal orchestration pipeline.
      </Paragraph>

      <Card
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={load}>
              Refresh
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              New Schedule
            </Button>
          </Space>
        }
        title={`Schedules (${total})`}
      >
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={rows}
          columns={columns}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (p, s) => {
              setPage(p);
              setSize(s);
            },
          }}
        />
      </Card>

      <Modal
        open={formOpen}
        title={editing ? `Edit Schedule #${editing.id}` : 'New Scheduled Payment'}
        onCancel={() => setFormOpen(false)}
        onOk={submitForm}
        okText={editing ? 'Save' : 'Create'}
        width={640}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="Name (optional)">
            <Input placeholder="e.g. Monthly rent" />
          </Form.Item>

          {isCustomer ? (
            <>
              <Form.Item
                name="debtorAccountId"
                label="From (your account)"
                rules={[{ required: true, message: 'Required' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="Select source account"
                  options={accounts.map((a) => ({
                    value: a.id,
                    label: `${a.alias || a.accountNumber} · ${a.currency}`,
                  }))}
                />
              </Form.Item>
              <Form.Item label="To (Beneficiary)" required>
                <BeneficiaryPicker
                  onResolved={(r) => {
                    form.setFieldValue('creditorAccountId', r?.accountId);
                  }}
                />
              </Form.Item>
              <Form.Item
                name="creditorAccountId"
                hidden
                rules={[{ required: true, message: 'Verify a beneficiary (VPA or bank details) first' }]}
              >
                <InputNumber />
              </Form.Item>
            </>
          ) : (
            <Space.Compact style={{ width: '100%' }}>
              <Form.Item
                name="debtorAccountId"
                label="From (debtor)"
                style={{ width: '50%' }}
                rules={[{ required: true, message: 'Required' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="Select source account"
                  options={accounts.map((a) => ({
                    value: a.id,
                    label: `${a.alias || a.accountNumber} (#${a.id})`,
                  }))}
                />
              </Form.Item>
              <Form.Item
                name="creditorAccountId"
                label="To (creditor)"
                style={{ width: '50%' }}
                rules={[{ required: true, message: 'Required' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="Select target account"
                  options={accounts.map((a) => ({
                    value: a.id,
                    label: `${a.alias || a.accountNumber} · ${a.currency} (#${a.id})`,
                  }))}
                />
              </Form.Item>
            </Space.Compact>
          )}

          <Space.Compact style={{ width: '100%' }}>
            <Form.Item
              name="amount"
              label="Amount"
              style={{ width: '60%' }}
              rules={[
                { required: true, message: 'Required' },
                {
                  validator: (_, value) => {
                    if (value === undefined || value === null || value === '') return Promise.resolve();
                    const n = Number(value);
                    if (Number.isNaN(n)) return Promise.reject(new Error('Amount must be a number'));
                    if (n < 0) return Promise.reject(new Error('Amount cannot be negative'));
                    if (n === 0) return Promise.reject(new Error('Amount cannot be zero'));
                    if (n < 0.01) return Promise.reject(new Error('Amount must be at least 0.01'));
                    return Promise.resolve();
                  },
                },
              ]}
            >
              <InputNumber step={10} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="currency"
              label="Currency"
              style={{ width: '40%' }}
              rules={[{ required: true, message: 'Required' }]}
            >
              <Select
                options={['INR', 'USD', 'EUR', 'GBP'].map((c) => ({ value: c, label: c }))}
              />
            </Form.Item>
          </Space.Compact>

          <Form.Item name="purposeCode" label="Purpose">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Select the reason for this payment"
              options={[
                { value: 'SALA', label: 'SALA — Salary payment to employee' },
                { value: 'SUPP', label: 'SUPP — Supplier / vendor payment' },
                { value: 'TRAD', label: 'TRAD — Trade-related (goods / services)' },
                { value: 'CCRD', label: 'CCRD — Credit-card repayment' },
                { value: 'LOAN', label: 'LOAN — Loan repayment' },
                { value: 'TAXS', label: 'TAXS — Tax payment' },
                { value: 'GIFT', label: 'GIFT — Gift to relative' },
                { value: 'EDUC', label: 'EDUC — Education fees' },
                { value: 'MEDC', label: 'MEDC — Medical expenses' },
                { value: 'RENT', label: 'RENT — Rent payment' },
                { value: 'INSU', label: 'INSU — Insurance premium' },
                { value: 'DIVD', label: 'DIVD — Dividend payout' },
                { value: 'INTC', label: 'INTC — Intra-company transfer' },
              ]}
            />
          </Form.Item>

          <Form.Item name="remittanceInfo" label="Remittance info">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>

          <Space.Compact style={{ width: '100%' }}>
            <Form.Item
              name="scheduleType"
              label="Frequency"
              style={{ width: '40%' }}
              rules={[{ required: true }]}
            >
              <Select
                options={[
                  { value: 'ONCE', label: 'One time' },
                  { value: 'DAILY', label: 'Daily' },
                  { value: 'WEEKLY', label: 'Weekly' },
                  { value: 'MONTHLY', label: 'Monthly' },
                ]}
              />
            </Form.Item>
            <Form.Item
              name="startAt"
              label="Start at"
              style={{ width: '60%' }}
              rules={[{ required: true, message: 'Required' }]}
            >
              <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
            </Form.Item>
          </Space.Compact>

          <Form.Item
            noStyle
            shouldUpdate={(prev, curr) => prev.scheduleType !== curr.scheduleType}
          >
            {({ getFieldValue }) =>
              getFieldValue('scheduleType') !== 'ONCE' && (
                <Space.Compact style={{ width: '100%' }}>
                  <Form.Item name="endAt" label="End at (optional)" style={{ width: '60%' }}>
                    <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="maxRuns" label="Max runs (optional)" style={{ width: '40%' }}>
                    <InputNumber min={1} style={{ width: '100%' }} placeholder="e.g. 12" />
                  </Form.Item>
                </Space.Compact>
              )
            }
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={<span><LockOutlined style={{ marginRight: 8, color: 'var(--pr-brand-1)' }} />Authorise this schedule</span>}
        open={schedulePinModalOpen}
        onOk={confirmSchedulePinAndSubmit}
        onCancel={() => { setSchedulePinModalOpen(false); setSchedulePin(''); }}
        okText="Authorise"
        cancelText="Cancel"
        destroyOnClose
      >
        <p style={{ color: 'var(--pr-text-muted)', marginBottom: 16, fontSize: 13 }}>
          Enter your 4–6 digit transaction PIN to authorise this schedule.
          Subsequent automated runs won't ask again.
        </p>
        <Input.Password
          autoFocus
          maxLength={6}
          inputMode="numeric"
          placeholder="Enter PIN"
          value={schedulePin}
          onChange={(e) => setSchedulePin(e.target.value.replace(/\D/g, ''))}
          onPressEnter={confirmSchedulePinAndSubmit}
          prefix={<LockOutlined style={{ color: 'var(--pr-text-muted)' }} />}
        />
      </Modal>
    </div>
  );
}
