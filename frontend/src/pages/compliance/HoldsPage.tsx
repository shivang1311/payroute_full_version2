import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Card, Tag, Select, Space, message, Modal, Button, Input,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { complianceApi } from '../../api/compliance.api';
import { useAuthStore } from '../../stores/authStore';
import type { Hold, HoldStatus } from '../../types';

const { TextArea } = Input;

const holdStatusColorMap: Record<HoldStatus, string> = {
  ACTIVE: 'red',
  RELEASED: 'green',
  ESCALATED: 'orange',
  REJECTED: 'volcano',
};

const holdStatusOptions: HoldStatus[] = ['ACTIVE', 'RELEASED', 'ESCALATED', 'REJECTED'];

type ActionMode = 'release' | 'reject';

const HoldsPage: React.FC = () => {
  const { user } = useAuthStore();
  // OPERATIONS gets read-only access — they can view holds but not release/reject
  const canActOnHolds = user?.role === 'COMPLIANCE' || user?.role === 'ADMIN';
  const [data, setData] = useState<Hold[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [actionModalOpen, setActionModalOpen] = useState(false);
  const [actionMode, setActionMode] = useState<ActionMode>('release');
  const [actingHold, setActingHold] = useState<Hold | null>(null);
  const [actionNotes, setActionNotes] = useState('');
  const [acting, setActing] = useState(false);

  const fetchHolds = useCallback(async () => {
    setLoading(true);
    try {
      const response = await complianceApi.getHolds({
        page,
        size: pageSize,
        status: statusFilter,
      });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load holds');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statusFilter]);

  useEffect(() => {
    fetchHolds();
  }, [fetchHolds]);

  const openActionModal = (hold: Hold, mode: ActionMode) => {
    setActingHold(hold);
    setActionMode(mode);
    setActionNotes('');
    setActionModalOpen(true);
  };

  const handleAction = async () => {
    if (!actingHold) return;
    if (!actionNotes.trim()) {
      message.warning(`Please provide ${actionMode === 'release' ? 'release' : 'reject'} notes`);
      return;
    }
    setActing(true);
    try {
      if (actionMode === 'release') {
        await complianceApi.releaseHold(actingHold.id, actionNotes.trim());
        message.success('Hold released — payment will resume processing');
      } else {
        await complianceApi.rejectHold(actingHold.id, actionNotes.trim());
        message.success('Hold rejected — payment marked as failed');
      }
      setActionModalOpen(false);
      fetchHolds();
    } catch {
      message.error(`Failed to ${actionMode} hold`);
    } finally {
      setActing(false);
    }
  };

  const columns: ColumnsType<Hold> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: 'Payment ID', dataIndex: 'paymentId', key: 'paymentId', width: 110 },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      ellipsis: true,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: HoldStatus) => <Tag color={holdStatusColorMap[status]}>{status}</Tag>,
    },
    { title: 'Placed By', dataIndex: 'placedBy', key: 'placedBy', width: 100 },
    {
      title: 'Placed At',
      dataIndex: 'placedAt',
      key: 'placedAt',
      render: (val: string) => dayjs(val).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: 'Released At',
      dataIndex: 'releasedAt',
      key: 'releasedAt',
      render: (val?: string) => val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: 'Release Notes',
      dataIndex: 'releaseNotes',
      key: 'releaseNotes',
      ellipsis: true,
      render: (val?: string) => val || '-',
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 180,
      render: (_, record) => {
        if (record.status !== 'ACTIVE') return null;
        if (!canActOnHolds) return <Tag>Read-only</Tag>;
        return (
          <Space size="small">
            <Button type="primary" size="small" onClick={() => openActionModal(record, 'release')}>
              Release
            </Button>
            <Button danger size="small" onClick={() => openActionModal(record, 'reject')}>
              Reject
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <Card
      title="Compliance Holds"
      extra={
        <Button icon={<ReloadOutlined />} onClick={fetchHolds}>Refresh</Button>
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          placeholder="Filter by Status"
          allowClear
          style={{ width: 180 }}
          value={statusFilter}
          onChange={(val) => { setStatusFilter(val); setPage(0); }}
          options={holdStatusOptions.map((s) => ({ label: s, value: s }))}
        />
      </Space>

      <Table<Hold>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `Total ${t} holds`,
          onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
        }}
      />

      <Modal
        title={`${actionMode === 'release' ? 'Release' : 'Reject'} Hold #${actingHold?.id}`}
        open={actionModalOpen}
        onCancel={() => setActionModalOpen(false)}
        onOk={handleAction}
        confirmLoading={acting}
        okText={actionMode === 'release' ? 'Release' : 'Reject'}
        okButtonProps={{ danger: actionMode === 'reject' }}
        destroyOnClose
      >
        <p>
          Payment ID: <strong>{actingHold?.paymentId}</strong>
        </p>
        <p>
          Reason: <strong>{actingHold?.reason}</strong>
        </p>
        <p style={{ color: 'var(--pr-text-muted)', fontSize: 13, marginBottom: 12 }}>
          {actionMode === 'release'
            ? 'Releasing will resume payment processing (routing → settlement).'
            : 'Rejecting will permanently fail this payment with the supplied reason.'}
        </p>
        <TextArea
          rows={4}
          placeholder={actionMode === 'release'
            ? 'Why are you releasing this hold?'
            : 'Why are you rejecting this payment?'}
          value={actionNotes}
          onChange={(e) => setActionNotes(e.target.value)}
        />
      </Modal>
    </Card>
  );
};

export default HoldsPage;
