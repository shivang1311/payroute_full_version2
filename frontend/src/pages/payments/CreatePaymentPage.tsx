import React, { useEffect, useMemo, useState } from 'react';
import { Card, Form, InputNumber, Input, Select, Button, message, Space, Alert, Modal } from 'antd';
import { ArrowLeftOutlined, PlusOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router';
import { paymentApi } from '../../api/payment.api';
import { accountApi } from '../../api/party.api';
import { useAuthStore } from '../../stores/authStore';
import BeneficiaryPicker from '../../components/common/BeneficiaryPicker';
import { paymentRef } from '../../utils/paymentRef';
import type { PaymentInitiationRequest, InitiationChannel, Account } from '../../types';

const currencyOptions = ['INR', 'USD', 'EUR', 'GBP'];
// BRANCH and API are staff-side rails — customers never use them in self-service.
// They only see MOBILE and ONLINE; staff (OPS/ADMIN) see all four.
const staffChannelOptions: InitiationChannel[] = ['BRANCH', 'MOBILE', 'ONLINE', 'API'];
const customerChannelOptions: InitiationChannel[] = ['MOBILE', 'ONLINE'];

/**
 * RBI / SWIFT-style purpose codes. Each transaction must be tagged with one
 * of these so downstream compliance + balance-of-payments reporting can
 * categorize the flow.
 */
const purposeCodeOptions: { value: string; label: string }[] = [
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
];

const CreatePaymentPage: React.FC = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
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

  const selectedDebtor = Form.useWatch('debtorAccountId', form) as number | undefined;
  const debtorAccount = useMemo(
    () => myAccounts.find((a) => a.id === selectedDebtor),
    [myAccounts, selectedDebtor],
  );

  // Live fee preview — drives the small advisory under the Amount field
  const watchedAmount = Form.useWatch('amount', form) as number | string | undefined;
  const watchedMethod = Form.useWatch('paymentMethod', form) as string | undefined;
  const watchedCurrency = (Form.useWatch('currency', form) as string | undefined) || 'INR';

  // Limits kept in sync with backend PaymentService / LimitChecker
  const UPI_TXN_LIMIT = 100000;
  const BANK_TRANSFER_TXN_LIMIT = 500000;

  /** Returns a structured warning if the amount exceeds the per-method per-txn cap. */
  const limitWarning = useMemo(() => {
    const amt = Number(watchedAmount);
    if (!isCustomer || !Number.isFinite(amt) || amt <= 0) return null;
    if (watchedMethod === 'UPI' && amt > UPI_TXN_LIMIT) {
      return {
        kind: 'switch-to-bank' as const,
        message:
          `UPI is capped at ₹${UPI_TXN_LIMIT.toLocaleString('en-IN')} per transaction. ` +
          `Switch to Bank Transfer for amounts up to ₹${BANK_TRANSFER_TXN_LIMIT.toLocaleString('en-IN')}.`,
      };
    }
    if (watchedMethod === 'BANK_TRANSFER' && amt > BANK_TRANSFER_TXN_LIMIT) {
      return {
        kind: 'bank-over-cap' as const,
        message:
          `Bank Transfer is capped at ₹${BANK_TRANSFER_TXN_LIMIT.toLocaleString('en-IN')} per transaction. ` +
          `Please split this into smaller payments.`,
      };
    }
    return null;
  }, [watchedAmount, watchedMethod, isCustomer]);

  const feePreview = useMemo(() => {
    const amt = Number(watchedAmount);
    if (!isCustomer) return null;
    if (!Number.isFinite(amt) || amt <= 0) return null;
    // Suppress the fee preview while a hard limit is being violated — the warning
    // banner takes priority and the values would be misleading at this point.
    if (limitWarning) return null;
    if (watchedMethod === 'UPI') {
      return { kind: 'upi' as const, amount: amt };
    }
    if (watchedMethod === 'BANK_TRANSFER') {
      // Rates kept in sync with backend FeeRateBootstrap
      const round2 = (n: number) => Math.round(n * 100) / 100;
      return {
        kind: 'bank' as const,
        amount: amt,
        neft: round2(amt * 0.0025),
        imps: round2(amt * 0.004),
        rtgs: round2(amt * 0.001),
      };
    }
    return null;
  }, [watchedAmount, watchedMethod, isCustomer, limitWarning]);

  // Auto-align currency to debtor account when customer picks one
  useEffect(() => {
    if (debtorAccount?.currency) {
      form.setFieldValue('currency', debtorAccount.currency);
    }
  }, [debtorAccount, form]);

  // Customer payments require a transaction PIN at submit time. We collect it
  // via a small modal AFTER the form has validated, then forward to the API.
  const [pinModalOpen, setPinModalOpen] = useState(false);
  const [pendingValues, setPendingValues] = useState<PaymentInitiationRequest | null>(null);
  const [pin, setPin] = useState('');

  const submitWithPin = async (values: PaymentInitiationRequest) => {
    setSubmitting(true);
    try {
      const response = await paymentApi.create(values);
      const created = response.data.data;
      message.success(
        isCustomer
          ? `Payment ${paymentRef(created)} created successfully`
          : `Payment #${created.id} (${paymentRef(created)}) created successfully`,
      );
      navigate(`/payments/${created.id}`);
    } catch (err: unknown) {
      const e = err as { response?: { status?: number; data?: { message?: string } } };
      if (e.response?.status === 403) {
        message.error(e.response.data?.message || 'Not authorized to debit this account');
      } else if (e.response?.status === 422) {
        message.error(e.response.data?.message || 'Payment validation failed');
      } else {
        message.error('Failed to create payment');
      }
    } finally {
      setSubmitting(false);
    }
  };

  /**
   * Form-submit handler. For customers we open a PIN modal and then call
   * submitWithPin once the PIN is captured; for staff we go straight through.
   */
  const onFinish = (values: PaymentInitiationRequest) => {
    if (isCustomer) {
      setPendingValues(values);
      setPin('');
      setPinModalOpen(true);
      return;
    }
    submitWithPin(values);
  };

  const confirmPinAndSubmit = async () => {
    if (!pendingValues) return;
    if (!/^\d{4,6}$/.test(pin)) {
      message.error('PIN must be 4 to 6 digits');
      return;
    }
    setPinModalOpen(false);
    await submitWithPin({ ...pendingValues, transactionPin: pin });
    setPin('');
    setPendingValues(null);
  };

  const noAccounts = isCustomer && !loadingAccounts && myAccounts.length === 0;

  return (
    <Card
      title="Create Payment"
      extra={
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/payments')}>
          Back to Payments
        </Button>
      }
      style={{ maxWidth: 640, margin: '0 auto' }}
    >
      {noAccounts && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="You don't have any accounts yet"
          description="You need to add at least one account before you can make a payment."
          action={
            <Button
              size="small"
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => navigate('/accounts')}
            >
              Add Account
            </Button>
          }
        />
      )}

      {isCustomer && myAccounts.length > 0 && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="You can only debit accounts you own. Select one of your accounts below."
        />
      )}

      <Form<PaymentInitiationRequest>
        form={form}
        layout="vertical"
        onFinish={onFinish}
        initialValues={{ currency: 'INR', initiationChannel: 'ONLINE' }}
        disabled={noAccounts}
      >
        <Form.Item
          label="From Account (Debtor)"
          name="debtorAccountId"
          rules={[{ required: true, message: 'Please select the source account' }]}
          extra={
            isCustomer
              ? 'Only your own accounts appear here.'
              : 'Enter the debtor account ID.'
          }
        >
          {isCustomer ? (
            <Select
              placeholder="Select one of your accounts"
              loading={loadingAccounts}
              notFoundContent="No accounts. Add one in Account Directory first."
              options={myAccounts.map((a) => ({
                value: a.id,
                label: `${a.accountType || 'ACCOUNT'} · ${a.accountNumber} · ${a.currency} · ${a.ifscIban || ''}`,
              }))}
            />
          ) : (
            <InputNumber style={{ width: '100%' }} min={1} placeholder="Enter debtor account ID" />
          )}
        </Form.Item>

        {isCustomer ? (
          <>
            <Form.Item label="To (Beneficiary)" required>
              <BeneficiaryPicker
                onResolved={(r) => {
                  form.setFieldValue('creditorAccountId', r?.accountId);
                  // Capture the payment method picked at the BeneficiaryPicker
                  // (UPI vs BANK_TRANSFER) — drives backend daily limit checks.
                  form.setFieldValue('paymentMethod', r?.method);
                  // If no debtor is selected yet, auto-align currency to beneficiary
                  if (r?.currency && !debtorAccount) {
                    form.setFieldValue('currency', r.currency);
                  }
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
            <Form.Item name="paymentMethod" hidden>
              <Input />
            </Form.Item>
          </>
        ) : (
          <Form.Item
            label="To Account (Creditor)"
            name="creditorAccountId"
            rules={[{ required: true, message: 'Please enter the beneficiary account ID' }]}
            extra="Beneficiary account ID (add the beneficiary via Account Directory if not already saved)."
          >
            <InputNumber style={{ width: '100%' }} min={1} placeholder="Enter creditor account ID" />
          </Form.Item>
        )}

        <Form.Item
          label="Amount"
          name="amount"
          rules={[
            { required: true, message: 'Please enter amount' },
            {
              validator: (_, value) => {
                if (value === undefined || value === null || value === '') return Promise.resolve();
                const n = Number(value);
                if (Number.isNaN(n)) return Promise.reject(new Error('Amount must be a valid number'));
                if (n < 0) return Promise.reject(new Error('Amount cannot be negative'));
                if (n === 0) return Promise.reject(new Error('Amount cannot be zero'));
                if (n < 0.01) return Promise.reject(new Error('Amount must be at least 0.01'));
                return Promise.resolve();
              },
            },
          ]}
        >
          <InputNumber
            style={{ width: '100%' }}
            step={0.01}
            precision={2}
            placeholder="Enter amount"
          />
        </Form.Item>

        {limitWarning && (
          <div
            style={{
              marginTop: -12,
              marginBottom: 16,
              padding: '10px 14px',
              background: 'rgba(239, 68, 68, 0.08)',
              border: '1px solid rgba(239, 68, 68, 0.30)',
              borderLeft: '3px solid var(--pr-error)',
              borderRadius: 8,
              fontSize: 13,
              color: 'var(--pr-text)',
              display: 'flex',
              alignItems: 'flex-start',
              gap: 10,
            }}
          >
            <span role="img" aria-label="warning" style={{ fontSize: 16 }}>⚠️</span>
            <div>
              <div style={{ fontWeight: 600, marginBottom: 2 }}>
                {limitWarning.kind === 'switch-to-bank' ? 'UPI limit reached' : 'Amount over Bank Transfer cap'}
              </div>
              <div style={{ color: 'var(--pr-text-muted)' }}>{limitWarning.message}</div>
            </div>
          </div>
        )}

        {feePreview && (
          <div
            style={{
              marginTop: -12,
              marginBottom: 16,
              padding: '10px 14px',
              background: 'var(--pr-brand-grad-soft)',
              border: '1px solid var(--pr-border)',
              borderLeft: '3px solid var(--pr-brand-1)',
              borderRadius: 8,
              fontSize: 13,
              color: 'var(--pr-text)',
            }}
          >
            {feePreview.kind === 'upi' ? (
              <>
                <span role="img" aria-label="check">✅</span>{' '}
                <strong>No processing fee</strong> on UPI transfers.
              </>
            ) : (
              <>
                <div style={{ fontWeight: 600, marginBottom: 4 }}>
                  Processing fee preview ({watchedCurrency})
                </div>
                <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap' }}>
                  <span>NEFT (0.25%): <strong>{feePreview.neft.toFixed(2)}</strong></span>
                  <span>IMPS (0.40%): <strong>{feePreview.imps.toFixed(2)}</strong></span>
                  <span>RTGS (0.10%): <strong>{feePreview.rtgs.toFixed(2)}</strong></span>
                </div>
                <div style={{ fontSize: 11, color: 'var(--pr-text-muted)', marginTop: 4 }}>
                  The actual rail (and thus the fee) is selected automatically by the routing engine
                  based on amount, urgency, and bank availability.
                </div>
              </>
            )}
          </div>
        )}

        <Form.Item
          label="Currency"
          name="currency"
          rules={[{ required: true, message: 'Please select currency' }]}
        >
          <Select
            options={currencyOptions.map((c) => ({ label: c, value: c }))}
            placeholder="Select currency"
            disabled={isCustomer && !!debtorAccount}
          />
        </Form.Item>

        <Form.Item label="Purpose Code" name="purposeCode">
          <Select
            placeholder="Select the reason for this payment"
            allowClear
            showSearch
            optionFilterProp="label"
            options={purposeCodeOptions}
          />
        </Form.Item>

        <Form.Item label="Initiation Channel" name="initiationChannel">
          <Select
            options={(isCustomer ? customerChannelOptions : staffChannelOptions)
              .map((c) => ({ label: c, value: c }))}
            placeholder="Select channel"
          />
        </Form.Item>

        {!isCustomer && (
          <Form.Item label="Idempotency Key" name="idempotencyKey">
            <Input placeholder="Optional unique key" />
          </Form.Item>
        )}

        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={submitting} disabled={noAccounts}>
              Submit Payment
            </Button>
            <Button onClick={() => form.resetFields()}>Reset</Button>
          </Space>
        </Form.Item>
      </Form>

      <Modal
        title={
          <span>
            <LockOutlined style={{ marginRight: 8, color: 'var(--pr-brand-1)' }} />
            Authorise this payment
          </span>
        }
        open={pinModalOpen}
        onOk={confirmPinAndSubmit}
        onCancel={() => { setPinModalOpen(false); setPin(''); }}
        confirmLoading={submitting}
        okText="Authorise"
        cancelText="Cancel"
        destroyOnClose
      >
        <p style={{ color: 'var(--pr-text-muted)', marginBottom: 16, fontSize: 13 }}>
          Enter your 4–6 digit transaction PIN to confirm this payment.
          The same PIN you set when you first signed in.
        </p>
        <Input.Password
          autoFocus
          maxLength={6}
          inputMode="numeric"
          placeholder="Enter PIN"
          value={pin}
          onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
          onPressEnter={confirmPinAndSubmit}
          prefix={<LockOutlined style={{ color: 'var(--pr-text-muted)' }} />}
        />
      </Modal>
    </Card>
  );
};

export default CreatePaymentPage;
