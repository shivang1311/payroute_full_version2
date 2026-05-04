import React, { useEffect, useState } from 'react';
import {
  Card,
  Form,
  InputNumber,
  Select,
  DatePicker,
  Button,
  Space,
  Table,
  Tag,
  Statistic,
  Row,
  Col,
  message,
  Empty,
} from 'antd';
import { DownloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router';
import dayjs, { Dayjs } from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { statementApi } from '../../api/statement.api';
import { accountApi } from '../../api/party.api';
import { useAuthStore } from '../../stores/authStore';
import { paymentRef } from '../../utils/paymentRef';
import type { Statement, StatementLine, EntryType } from '../../types/statement';
import type { Account } from '../../types';

const { RangePicker } = DatePicker;

const entryTypeColorMap: Record<EntryType, string> = {
  DEBIT: 'red',
  CREDIT: 'green',
  FEE: 'orange',
  TAX: 'gold',
  REVERSAL: 'purple',
};

const toNum = (v: string | number | null | undefined): number => {
  if (v == null) return 0;
  return typeof v === 'number' ? v : Number(v);
};

const fmtMoney = (v: string | number | null | undefined) =>
  toNum(v).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const StatementPage: React.FC = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm<{ accountId: number; range: [Dayjs, Dayjs] }>();
  const [loading, setLoading] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [data, setData] = useState<Statement | null>(null);
  const { user } = useAuthStore();
  const isCustomer = user?.role === 'CUSTOMER';
  const [myAccounts, setMyAccounts] = useState<Account[]>([]);
  const [loadingAccounts, setLoadingAccounts] = useState(false);

  useEffect(() => {
    if (!isCustomer || !user?.partyId) return;
    setLoadingAccounts(true);
    accountApi
      .getAll({ partyId: user.partyId, size: 100 })
      .then((res) => setMyAccounts(res.data.data.content || []))
      .catch(() => message.error('Failed to load your accounts'))
      .finally(() => setLoadingAccounts(false));
  }, [isCustomer, user?.partyId]);

  const fetchStatement = async () => {
    const vals = await form.validateFields();
    const [from, to] = vals.range;
    setLoading(true);
    try {
      const res = await statementApi.get(vals.accountId, from.format('YYYY-MM-DD'), to.format('YYYY-MM-DD'));
      setData(res.data.data);
    } catch {
      message.error('Failed to load statement');
    } finally {
      setLoading(false);
    }
  };

  const downloadCsv = async () => {
    const vals = await form.validateFields();
    const [from, to] = vals.range;
    setDownloading(true);
    try {
      const res = await statementApi.downloadCsv(
        vals.accountId,
        from.format('YYYY-MM-DD'),
        to.format('YYYY-MM-DD'),
      );
      const blob = new Blob([res.data as BlobPart], { type: 'text/csv;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `statement-acct${vals.accountId}-${from.format('YYYY-MM-DD')}-to-${to.format('YYYY-MM-DD')}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch {
      message.error('Failed to download CSV');
    } finally {
      setDownloading(false);
    }
  };

  const columns: ColumnsType<StatementLine> = [
    { title: 'Date', dataIndex: 'entryDate', key: 'entryDate', width: 120 },
    // Entry # is an internal ledger row id — hide from customers
    ...(isCustomer
      ? []
      : ([{ title: 'Entry #', dataIndex: 'entryId', key: 'entryId', width: 90 }] as ColumnsType<StatementLine>)),
    {
      title: isCustomer ? 'Reference No' : 'Payment',
      dataIndex: 'paymentId',
      key: 'paymentId',
      width: isCustomer ? 150 : 110,
      render: (v: number | null, row) => {
        if (!v) return '-';
        if (isCustomer) {
          // Use entryDate as a stable proxy for the payment's createdAt-day so the
          // computed ref matches the stored backend ref in the common same-day case.
          const ref = paymentRef({ id: v, createdAt: row.entryDate });
          return (
            <a
              style={{ fontFamily: 'monospace', fontSize: 12 }}
              onClick={() => navigate(`/payments/${v}`)}
            >
              {ref}
            </a>
          );
        }
        return `#${v}`;
      },
    },
    {
      title: 'Type',
      dataIndex: 'entryType',
      key: 'entryType',
      width: 110,
      render: (t: EntryType) => <Tag color={entryTypeColorMap[t]}>{t}</Tag>,
    },
    {
      title: 'Narrative',
      dataIndex: 'narrative',
      key: 'narrative',
      ellipsis: true,
      render: (v: string | null) => {
        if (!v) return '-';
        if (!isCustomer) return v;
        // Strip internal payment id from text like "Payment debit - PaymentId: 42"
        // and any "(id=123)" suffixes used for reversal narratives.
        return v
          .replace(/\s*[-–]\s*PaymentId:\s*\d+/gi, '')
          .replace(/,\s*PaymentId:\s*\d+/gi, '')
          .replace(/\s*\(id\s*=\s*\d+\)/gi, '')
          .trim() || '-';
      },
    },
    {
      title: 'Debit',
      dataIndex: 'debit',
      key: 'debit',
      align: 'right',
      width: 130,
      render: (v: string | number) => (toNum(v) ? <span style={{ color: '#cf1322' }}>{fmtMoney(v)}</span> : '-'),
    },
    {
      title: 'Credit',
      dataIndex: 'credit',
      key: 'credit',
      align: 'right',
      width: 130,
      render: (v: string | number) => (toNum(v) ? <span style={{ color: '#389e0d' }}>{fmtMoney(v)}</span> : '-'),
    },
    {
      title: 'Balance',
      dataIndex: 'runningBalance',
      key: 'runningBalance',
      align: 'right',
      width: 140,
      render: (v: string | number) => <strong>{fmtMoney(v)}</strong>,
    },
  ];

  return (
    <Card title="Customer Account Statement">
      <Form
        form={form}
        layout="inline"
        initialValues={{ range: [dayjs().subtract(30, 'day'), dayjs()] }}
        style={{ marginBottom: 16, flexWrap: 'wrap', rowGap: 8 }}
      >
        <Form.Item
          name="accountId"
          label="Account"
          rules={[{ required: true, message: isCustomer ? 'Select an account' : 'Enter account id' }]}
        >
          {isCustomer ? (
            <Select
              placeholder="Select one of your accounts"
              loading={loadingAccounts}
              style={{ width: 320 }}
              notFoundContent="No accounts. Add one in Account Directory first."
              options={myAccounts.map((a) => ({
                value: a.id,
                label: `${a.accountNumber} · ${a.currency}${a.accountType ? ' · ' + a.accountType : ''}`,
              }))}
            />
          ) : (
            <InputNumber min={1} placeholder="e.g. 42" style={{ width: 160 }} />
          )}
        </Form.Item>
        <Form.Item
          name="range"
          label="Period"
          rules={[{ required: true, message: 'Pick a date range' }]}
        >
          <RangePicker allowClear={false} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" icon={<SearchOutlined />} loading={loading} onClick={fetchStatement}>
              Load
            </Button>
            <Button
              icon={<DownloadOutlined />}
              loading={downloading}
              disabled={!data}
              onClick={downloadCsv}
            >
              Download CSV
            </Button>
          </Space>
        </Form.Item>
      </Form>

      {data ? (
        <>
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col xs={12} md={6}>
              <Card size="small">
                <Statistic
                  title="Opening Balance"
                  value={fmtMoney(data.openingBalance)}
                  prefix={data.currency || ''}
                />
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card size="small">
                <Statistic
                  title="Total Credits"
                  value={fmtMoney(data.totalCredits)}
                  valueStyle={{ color: '#389e0d' }}
                  prefix={data.currency || ''}
                />
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card size="small">
                <Statistic
                  title="Total Debits"
                  value={fmtMoney(data.totalDebits)}
                  valueStyle={{ color: '#cf1322' }}
                  prefix={data.currency || ''}
                />
              </Card>
            </Col>
            <Col xs={12} md={6}>
              <Card size="small">
                <Statistic
                  title="Closing Balance"
                  value={fmtMoney(data.closingBalance)}
                  prefix={data.currency || ''}
                />
              </Card>
            </Col>
          </Row>
          <Table<StatementLine>
            rowKey="entryId"
            columns={columns}
            dataSource={data.entries}
            loading={loading}
            pagination={{ pageSize: 25, showSizeChanger: true, showTotal: (t) => `Total ${t} entries` }}
            size="small"
          />
        </>
      ) : (
        <Empty description="Pick an account and date range to view the statement" />
      )}
    </Card>
  );
};

export default StatementPage;
