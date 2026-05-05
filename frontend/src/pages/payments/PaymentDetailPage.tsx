import React, { useEffect, useState } from 'react';
import { Card, Descriptions, Tag, Table, Button, Spin, Empty, message, Space, Modal, Select, Input } from 'antd';
import { ArrowLeftOutlined, WarningOutlined, RedoOutlined, StopOutlined, PauseCircleOutlined } from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router';
import dayjs from 'dayjs';
import type { ColumnsType } from 'antd/es/table';
import { paymentApi } from '../../api/payment.api';
import { accountApi } from '../../api/party.api';
import { exceptionApi } from '../../api/exception.api';
import { useAuthStore } from '../../stores/authStore';
import { paymentRef } from '../../utils/paymentRef';
import type { Account, PaymentOrder, PaymentStatus, ValidationResult } from '../../types';

const exceptionCategoryOptions = ['VALIDATION', 'RAIL', 'POSTING', 'COMPLIANCE', 'SYSTEM'];
const exceptionPriorityOptions = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

/** Mask all but the last 4 digits of an account number. */
const maskAccount = (acc?: string) => {
  if (!acc) return '-';
  if (acc.length <= 4) return acc;
  return '••••' + acc.slice(-4);
};

const statusColorMap: Record<PaymentStatus, string> = {
  INITIATED: 'default',
  VALIDATED: 'cyan',
  VALIDATION_FAILED: 'red',
  SCREENING: 'geekblue',
  HELD: 'orange',
  ROUTED: 'blue',
  PROCESSING: 'processing',
  COMPLETED: 'green',
  FAILED: 'red',
  CANCELLED: 'volcano',
  REVERSED: 'purple',
};

const PaymentDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const isCustomer = user?.role === 'CUSTOMER';
  const canRaiseException = user?.role === 'OPERATIONS' || user?.role === 'ADMIN';
  const canRetry = user?.role === 'OPERATIONS' || user?.role === 'ADMIN';
  const canActOnPayment = user?.role === 'OPERATIONS' || user?.role === 'ADMIN';
  const [retrying, setRetrying] = useState(false);
  const [actionModal, setActionModal] = useState<null | 'cancel' | 'hold'>(null);
  const [actionReason, setActionReason] = useState('');
  const [acting, setActing] = useState(false);
  const [payment, setPayment] = useState<PaymentOrder | null>(null);
  const [debtor, setDebtor] = useState<Account | null>(null);
  const [creditor, setCreditor] = useState<Account | null>(null);
  const [loading, setLoading] = useState(true);

  // Raise-exception modal state
  const [exceptionModalOpen, setExceptionModalOpen] = useState(false);
  const [exceptionCategory, setExceptionCategory] = useState<string | undefined>('SYSTEM');
  const [exceptionPriority, setExceptionPriority] = useState<string | undefined>('MEDIUM');
  const [exceptionDescription, setExceptionDescription] = useState('');
  const [raisingException, setRaisingException] = useState(false);

  useEffect(() => {
    if (!id) return;
    const fetchPayment = async () => {
      setLoading(true);
      try {
        const response = await paymentApi.getById(Number(id));
        const p = response.data.data;
        setPayment(p);
        // For customer view, resolve account details to show friendly From/To
        if (isCustomer && p) {
          const [d, c] = await Promise.allSettled([
            accountApi.getById(p.debtorAccountId),
            accountApi.getById(p.creditorAccountId),
          ]);
          if (d.status === 'fulfilled') setDebtor(d.value.data.data);
          if (c.status === 'fulfilled') setCreditor(c.value.data.data);
        }
      } catch {
        message.error('Failed to load payment details');
      } finally {
        setLoading(false);
      }
    };
    fetchPayment();
  }, [id, isCustomer]);

  const handleAction = async () => {
    if (!payment || !actionModal) return;
    if (!actionReason.trim()) {
      message.warning('Reason is required');
      return;
    }
    setActing(true);
    try {
      const r = actionModal === 'cancel'
        ? await paymentApi.cancel(payment.id, actionReason.trim())
        : await paymentApi.hold(payment.id, actionReason.trim());
      message.success(actionModal === 'cancel' ? 'Payment cancelled' : 'Payment placed on hold');
      setPayment(r.data.data);
      setActionModal(null);
      setActionReason('');
    } catch (e) {
      const errAxios = e as { response?: { data?: { message?: string } } };
      message.error(errAxios.response?.data?.message || `Failed to ${actionModal} payment`);
    } finally {
      setActing(false);
    }
  };

  const handleRetry = async () => {
    if (!payment) return;
    setRetrying(true);
    try {
      const r = await paymentApi.retry(payment.id);
      message.success('Retry scheduled — orchestration re-running');
      // Refresh payment to show new INITIATED status; orchestration runs async
      setPayment(r.data.data);
    } catch (e) {
      const errAxios = e as { response?: { data?: { message?: string } } };
      message.error(errAxios.response?.data?.message || 'Failed to retry payment');
    } finally {
      setRetrying(false);
    }
  };

  const handleRaiseException = async () => {
    if (!payment || !exceptionCategory || !exceptionPriority) {
      message.warning('Please select a category and priority');
      return;
    }
    setRaisingException(true);
    try {
      await exceptionApi.create({
        paymentId: payment.id,
        category: exceptionCategory,
        priority: exceptionPriority,
        description: exceptionDescription.trim() || undefined,
      });
      message.success('Exception raised — visible in the Exception Queue');
      setExceptionModalOpen(false);
      setExceptionDescription('');
      setExceptionCategory('SYSTEM');
      setExceptionPriority('MEDIUM');
    } catch {
      message.error('Failed to raise exception');
    } finally {
      setRaisingException(false);
    }
  };

  const validationColumns: ColumnsType<ValidationResult> = [
    { title: 'Rule', dataIndex: 'ruleName', key: 'ruleName' },
    {
      title: 'Result',
      dataIndex: 'result',
      key: 'result',
      render: (val: 'PASS' | 'FAIL') => (
        <Tag color={val === 'PASS' ? 'green' : 'red'}>{val}</Tag>
      ),
    },
    { title: 'Message', dataIndex: 'message', key: 'message', render: (v: string) => v || '-' },
    {
      title: 'Checked At',
      dataIndex: 'checkedAt',
      key: 'checkedAt',
      render: (val: string) => dayjs(val).format('YYYY-MM-DD HH:mm:ss'),
    },
  ];

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!payment) {
    return (
      <Card>
        <Empty description="Payment not found" />
        <div style={{ textAlign: 'center', marginTop: 16 }}>
          <Button onClick={() => navigate('/payments')}>Back to Payments</Button>
        </div>
      </Card>
    );
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={
          isCustomer
            ? `Payment ${paymentRef(payment)}`
            : `Payment #${payment.id} · ${paymentRef(payment)}`
        }
        extra={
          <Space wrap>
            {canRetry && (payment.status === 'FAILED' || payment.status === 'VALIDATION_FAILED') && payment.status !== 'CANCELLED' && (
              <Button
                type="primary"
                icon={<RedoOutlined />}
                loading={retrying}
                onClick={handleRetry}
              >
                Retry Payment
              </Button>
            )}
            {canActOnPayment && !['COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED', 'HELD'].includes(payment.status) && (
              <Button
                icon={<PauseCircleOutlined />}
                onClick={() => { setActionModal('hold'); setActionReason(''); }}
              >
                Hold
              </Button>
            )}
            {canActOnPayment && !['COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED'].includes(payment.status) && (
              <Button
                danger
                icon={<StopOutlined />}
                onClick={() => { setActionModal('cancel'); setActionReason(''); }}
              >
                Cancel
              </Button>
            )}
            {canRaiseException && (
              <Button
                danger
                icon={<WarningOutlined />}
                onClick={() => setExceptionModalOpen(true)}
              >
                Raise Exception
              </Button>
            )}
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/payments')}>
              Back to Payments
            </Button>
          </Space>
        }
      >
        <Descriptions bordered column={{ xs: 1, sm: 2 }}>
          <Descriptions.Item label="Reference No">
            <span style={{ fontFamily: 'monospace' }}>
              {isCustomer
                ? paymentRef(payment)
                : `#${payment.id} · ${paymentRef(payment)}`}
            </span>
          </Descriptions.Item>
          <Descriptions.Item label="Status">
            <Tag color={statusColorMap[payment.status]} style={{ fontSize: 14, padding: '2px 12px' }}>
              {payment.status}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Amount">
            {payment.currency}{' '}
            {payment.amount.toLocaleString(undefined, {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })}
          </Descriptions.Item>
          <Descriptions.Item label="Currency">{payment.currency}</Descriptions.Item>
          {isCustomer ? (
            <>
              <Descriptions.Item label="From">
                {debtor
                  ? `${debtor.alias || debtor.accountType || 'Account'} · ${maskAccount(debtor.accountNumber)}`
                  : maskAccount(undefined)}
              </Descriptions.Item>
              <Descriptions.Item label="To">
                {creditor
                  ? `${creditor.partyName || creditor.alias || 'Beneficiary'} · ${maskAccount(creditor.accountNumber)}`
                  : maskAccount(undefined)}
              </Descriptions.Item>
            </>
          ) : (
            <>
              <Descriptions.Item label="Debtor Account ID">{payment.debtorAccountId}</Descriptions.Item>
              <Descriptions.Item label="Creditor Account ID">{payment.creditorAccountId}</Descriptions.Item>
            </>
          )}
          <Descriptions.Item label="Channel">
            <Tag>{payment.initiationChannel}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Purpose Code">{payment.purposeCode || '-'}</Descriptions.Item>
          {!isCustomer && (
            <Descriptions.Item label="Idempotency Key">{payment.idempotencyKey || '-'}</Descriptions.Item>
          )}
          {!isCustomer && (
            <Descriptions.Item label="Initiated By">{payment.initiatedBy ?? '-'}</Descriptions.Item>
          )}
          <Descriptions.Item label="Created At">
            {dayjs(payment.createdAt).format('YYYY-MM-DD HH:mm:ss')}
          </Descriptions.Item>
          <Descriptions.Item label="Updated At">
            {dayjs(payment.updatedAt).format('YYYY-MM-DD HH:mm:ss')}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {!isCustomer && payment.validationResults && payment.validationResults.length > 0 && (
        <Card title="Validation Results">
          <Table<ValidationResult>
            rowKey="id"
            columns={validationColumns}
            dataSource={payment.validationResults}
            pagination={false}
            size="small"
          />
        </Card>
      )}

      <Modal
        title={actionModal === 'cancel'
          ? `Cancel Payment #${payment.id}`
          : `Place Payment #${payment.id} on Hold`}
        open={actionModal !== null}
        onOk={handleAction}
        onCancel={() => { setActionModal(null); setActionReason(''); }}
        confirmLoading={acting}
        okText={actionModal === 'cancel' ? 'Cancel Payment' : 'Place on Hold'}
        okButtonProps={{ danger: actionModal === 'cancel' }}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <div style={{ marginBottom: 4 }}>Reason (required)</div>
            <Input.TextArea
              rows={3}
              value={actionReason}
              onChange={(e) => setActionReason(e.target.value)}
              placeholder={actionModal === 'cancel'
                ? 'e.g. Customer requested cancellation'
                : 'e.g. Suspicious activity — pause for analyst review'}
            />
          </div>
        </Space>
      </Modal>

      <Modal
        title={`Raise Exception for Payment #${payment.id}`}
        open={exceptionModalOpen}
        onOk={handleRaiseException}
        onCancel={() => setExceptionModalOpen(false)}
        confirmLoading={raisingException}
        okText="Raise"
        okButtonProps={{ danger: true }}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <div style={{ marginBottom: 4 }}>Category</div>
            <Select
              style={{ width: '100%' }}
              value={exceptionCategory}
              onChange={setExceptionCategory}
              options={exceptionCategoryOptions.map((c) => ({ label: c, value: c }))}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4 }}>Priority</div>
            <Select
              style={{ width: '100%' }}
              value={exceptionPriority}
              onChange={setExceptionPriority}
              options={exceptionPriorityOptions.map((p) => ({ label: p, value: p }))}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4 }}>Description</div>
            <Input.TextArea
              rows={4}
              value={exceptionDescription}
              onChange={(e) => setExceptionDescription(e.target.value)}
              placeholder="Describe what went wrong (e.g. customer reported failed delivery)..."
            />
          </div>
        </Space>
      </Modal>
    </Space>
  );
};

export default PaymentDetailPage;
