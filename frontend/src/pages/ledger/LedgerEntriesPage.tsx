import React, { useEffect, useState, useCallback } from 'react';
import { Table, Card, Tag, Space, message, InputNumber, Select, Button, DatePicker } from 'antd';
import { ReloadOutlined, DownloadOutlined } from '@ant-design/icons';
import dayjs, { Dayjs } from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { ledgerApi } from '../../api/ledger.api';
import { accountApi } from '../../api/party.api';
import { useAuthStore } from '../../stores/authStore';
import type { LedgerEntry, EntryType, Account } from '../../types';

const entryTypeColorMap: Record<EntryType, string> = {
  DEBIT: 'red',
  CREDIT: 'green',
  FEE: 'orange',
  REVERSAL: 'purple',
  TAX: 'default',
};

const LedgerEntriesPage: React.FC = () => {
  const [data, setData] = useState<LedgerEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [paymentIdFilter, setPaymentIdFilter] = useState<number | undefined>();
  const [accountIdFilter, setAccountIdFilter] = useState<number | undefined>();
  const [exportRange, setExportRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(30, 'day').startOf('day'),
    dayjs().endOf('day'),
  ]);
  const [exporting, setExporting] = useState(false);
  const { user } = useAuthStore();
  const isCustomer = user?.role === 'CUSTOMER';
  const [myAccounts, setMyAccounts] = useState<Account[]>([]);
  const [loadingAccounts, setLoadingAccounts] = useState(false);

  useEffect(() => {
    if (!isCustomer || !user?.partyId) return;
    setLoadingAccounts(true);
    accountApi
      .getAll({ partyId: user.partyId, size: 100 })
      .then((res) => {
        const accts = res.data.data.content || [];
        setMyAccounts(accts);
        // Auto-select first account so customer sees their own entries immediately
        if (accts.length > 0 && accountIdFilter == null) {
          setAccountIdFilter(accts[0].id);
        }
      })
      .catch(() => message.error('Failed to load your accounts'))
      .finally(() => setLoadingAccounts(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isCustomer, user?.partyId]);

  const handleExport = async () => {
    setExporting(true);
    try {
      const from = exportRange[0].format('YYYY-MM-DD');
      const to = exportRange[1].format('YYYY-MM-DD');
      const res = await ledgerApi.exportCsv(from, to);
      const blob = new Blob([res.data as BlobPart], { type: 'text/csv;charset=utf-8' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `ledger-export-${from}-to-${to}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
      message.success('GL export downloaded');
    } catch {
      message.error('Failed to export ledger');
    } finally {
      setExporting(false);
    }
  };

  const fetchEntries = useCallback(async () => {
    setLoading(true);
    try {
      const response = await ledgerApi.getEntries({
        page,
        size: pageSize,
        paymentId: paymentIdFilter,
        accountId: accountIdFilter,
      });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load ledger entries');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, paymentIdFilter, accountIdFilter]);

  useEffect(() => {
    fetchEntries();
  }, [fetchEntries]);

  const columns: ColumnsType<LedgerEntry> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: 'Payment ID', dataIndex: 'paymentId', key: 'paymentId', width: 110 },
    { title: 'Account ID', dataIndex: 'accountId', key: 'accountId', width: 110 },
    {
      title: 'Entry Type',
      dataIndex: 'entryType',
      key: 'entryType',
      render: (type: EntryType) => <Tag color={entryTypeColorMap[type]}>{type}</Tag>,
    },
    {
      title: 'Amount',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right',
      render: (val: number) =>
        val.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    },
    { title: 'Currency', dataIndex: 'currency', key: 'currency', width: 90 },
    {
      title: 'Narrative',
      dataIndex: 'narrative',
      key: 'narrative',
      ellipsis: true,
      render: (val?: string) => val || '-',
    },
    {
      title: 'Balance After',
      dataIndex: 'balanceAfter',
      key: 'balanceAfter',
      align: 'right',
      render: (val?: number) =>
        val != null
          ? val.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
          : '-',
    },
    {
      title: 'Entry Date',
      dataIndex: 'entryDate',
      key: 'entryDate',
      render: (val: string) => dayjs(val).format('YYYY-MM-DD HH:mm'),
    },
  ];

  return (
    <Card
      title="Ledger Entries"
      extra={
        <Button icon={<ReloadOutlined />} onClick={fetchEntries}>Refresh</Button>
      }
    >
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }} wrap>
        <Space wrap>
          <InputNumber
            placeholder="Payment ID"
            style={{ width: 160 }}
            value={paymentIdFilter}
            onChange={(val) => { setPaymentIdFilter(val ?? undefined); setPage(0); }}
            min={1}
          />
          {isCustomer ? (
            <Select
              placeholder="Select your account"
              loading={loadingAccounts}
              style={{ width: 280 }}
              value={accountIdFilter}
              onChange={(val) => { setAccountIdFilter(val ?? undefined); setPage(0); }}
              notFoundContent="No accounts. Add one in Account Directory first."
              options={myAccounts.map((a) => ({
                value: a.id,
                label: `${a.accountNumber} · ${a.currency}`,
              }))}
            />
          ) : (
            <InputNumber
              placeholder="Account ID"
              style={{ width: 160 }}
              value={accountIdFilter}
              onChange={(val) => { setAccountIdFilter(val ?? undefined); setPage(0); }}
              min={1}
            />
          )}
        </Space>
        {!isCustomer && (
          <Space>
            <DatePicker.RangePicker
              value={exportRange}
              onChange={(v) => {
                if (v && v[0] && v[1]) setExportRange([v[0].startOf('day'), v[1].endOf('day')]);
              }}
              allowClear={false}
            />
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              loading={exporting}
              onClick={handleExport}
            >
              Export GL CSV
            </Button>
          </Space>
        )}
      </Space>

      <Table<LedgerEntry>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} entries`,
          onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
        }}
      />
    </Card>
  );
};

export default LedgerEntriesPage;
