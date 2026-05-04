import { useState } from 'react';
import { Alert, Button, Input, Radio, Space, Tag, message } from 'antd';
import { CheckCircleOutlined, SearchOutlined } from '@ant-design/icons';
import { accountApi } from '../../api/party.api';
import type { AccountValidationResponse } from '../../types';

export interface ResolvedBeneficiary {
  accountId: number;
  partyName?: string;
  currency?: string;
  displayLabel: string;
  /** How the payer identified this beneficiary — drives method-specific limits. */
  method: 'UPI' | 'BANK_TRANSFER';
}

interface Props {
  onResolved: (b: ResolvedBeneficiary | null) => void;
}

type Mode = 'VPA' | 'BANK';

/**
 * Lets the customer identify a beneficiary by either VPA/UPI or
 * accountNumber + IFSC/IBAN, resolves to a numeric accountId via
 * party-service, and calls onResolved with the result.
 */
export default function BeneficiaryPicker({ onResolved }: Props) {
  const [mode, setMode] = useState<Mode>('VPA');
  const [vpa, setVpa] = useState('');
  const [accountNumber, setAccountNumber] = useState('');
  const [ifscIban, setIfscIban] = useState('');
  const [verifying, setVerifying] = useState(false);
  const [resolved, setResolved] = useState<ResolvedBeneficiary | null>(null);

  const reset = () => {
    setResolved(null);
    onResolved(null);
  };

  const switchMode = (m: Mode) => {
    setMode(m);
    reset();
  };

  const verify = async () => {
    setVerifying(true);
    try {
      if (mode === 'VPA') {
        if (!vpa.trim()) {
          message.warning('Enter a VPA/UPI ID');
          return;
        }
        const res = await accountApi.resolve('VPA', vpa.trim());
        const acct = res.data.data;
        if (!acct) {
          message.error('Beneficiary not found for this VPA');
          reset();
          return;
        }
        const r: ResolvedBeneficiary = {
          accountId: acct.id,
          partyName: acct.partyName,
          currency: acct.currency,
          displayLabel: `${acct.partyName || acct.accountNumber} · ${acct.currency}`,
          method: 'UPI',
        };
        setResolved(r);
        onResolved(r);
      } else {
        if (!accountNumber.trim() || !ifscIban.trim()) {
          message.warning('Enter both account number and IFSC/IBAN');
          return;
        }
        const res = await accountApi.validate(accountNumber.trim(), ifscIban.trim());
        const v: AccountValidationResponse = res.data.data;
        if (!v || !v.exists) {
          message.error('Beneficiary account not found');
          reset();
          return;
        }
        if (!v.active) {
          message.error('Beneficiary account is inactive');
          reset();
          return;
        }
        const r: ResolvedBeneficiary = {
          accountId: v.accountId,
          partyName: v.partyName,
          currency: v.currency,
          displayLabel: `${v.partyName || accountNumber} · ${v.currency}`,
          method: 'BANK_TRANSFER',
        };
        setResolved(r);
        onResolved(r);
      }
    } catch (e: unknown) {
      const err = e as { response?: { status?: number; data?: { message?: string } } };
      if (err.response?.status === 404) {
        message.error('Beneficiary not found. Ask them to register an account first.');
      } else {
        message.error(err.response?.data?.message || 'Verification failed');
      }
      reset();
    } finally {
      setVerifying(false);
    }
  };

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="small">
      <Radio.Group
        value={mode}
        onChange={(e) => switchMode(e.target.value)}
        optionType="button"
        buttonStyle="solid"
        size="small"
      >
        <Radio.Button value="VPA">VPA / UPI</Radio.Button>
        <Radio.Button value="BANK">Bank Transfer</Radio.Button>
      </Radio.Group>

      {mode === 'VPA' ? (
        <Space.Compact style={{ width: '100%' }}>
          <Input
            placeholder="beneficiary@upi"
            value={vpa}
            onChange={(e) => { setVpa(e.target.value); reset(); }}
            onPressEnter={verify}
          />
          <Button icon={<SearchOutlined />} loading={verifying} onClick={verify} type="primary">
            Verify
          </Button>
        </Space.Compact>
      ) : (
        <Space direction="vertical" style={{ width: '100%' }} size="small">
          <Input
            placeholder="Account number (16 digits)"
            maxLength={16}
            value={accountNumber}
            onChange={(e) => { setAccountNumber(e.target.value); reset(); }}
          />
          <Space.Compact style={{ width: '100%' }}>
            <Input
              placeholder="IFSC / IBAN"
              value={ifscIban}
              onChange={(e) => { setIfscIban(e.target.value); reset(); }}
              onPressEnter={verify}
            />
            <Button icon={<SearchOutlined />} loading={verifying} onClick={verify} type="primary">
              Verify
            </Button>
          </Space.Compact>
        </Space>
      )}

      {resolved && (
        <Alert
          type="success"
          showIcon
          icon={<CheckCircleOutlined />}
          message={
            <Space wrap>
              <span>Beneficiary verified:</span>
              <Tag color="green">{resolved.displayLabel}</Tag>
            </Space>
          }
        />
      )}
    </Space>
  );
}
