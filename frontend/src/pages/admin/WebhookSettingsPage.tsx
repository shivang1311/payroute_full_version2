import { useEffect, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message,
  Popconfirm,
  Descriptions,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
  DeleteOutlined,
  EditOutlined,
  KeyOutlined,
  RedoOutlined,
  EyeOutlined,
  CopyOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { webhookApi } from '../../api/webhook.api';
import type {
  WebhookEndpoint,
  WebhookDelivery,
  WebhookEndpointRequest,
  DeliveryStatus,
} from '../../types/webhook';

const { Title, Text, Paragraph } = Typography;

const EVENT_OPTIONS = [
  'PAYMENT_INITIATED',
  'PAYMENT_VALIDATED',
  'PAYMENT_HELD',
  'PAYMENT_ROUTED',
  'PAYMENT_COMPLETED',
  'PAYMENT_FAILED',
  'PAYMENT_REVERSED',
];

const statusColor: Record<DeliveryStatus, string> = {
  PENDING: 'default',
  RETRYING: 'orange',
  SUCCESS: 'green',
  FAILED: 'red',
};

export default function WebhookSettingsPage() {
  const [tab, setTab] = useState<string>('endpoints');

  // Endpoints state
  const [endpoints, setEndpoints] = useState<WebhookEndpoint[]>([]);
  const [endpointsLoading, setEndpointsLoading] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<WebhookEndpoint | null>(null);
  const [form] = Form.useForm<WebhookEndpointRequest & { eventsArr?: string[] }>();
  const [secretModal, setSecretModal] = useState<{ open: boolean; secret: string; url?: string }>({
    open: false,
    secret: '',
  });

  // Deliveries state
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);
  const [delLoading, setDelLoading] = useState(false);
  const [delPage, setDelPage] = useState(1);
  const [delSize, setDelSize] = useState(20);
  const [delTotal, setDelTotal] = useState(0);
  const [endpointFilter, setEndpointFilter] = useState<number | undefined>(undefined);
  const [detailDelivery, setDetailDelivery] = useState<WebhookDelivery | null>(null);

  const loadEndpoints = async () => {
    setEndpointsLoading(true);
    try {
      const res = await webhookApi.listEndpoints();
      setEndpoints(res.data.data ?? []);
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Failed to load endpoints');
    } finally {
      setEndpointsLoading(false);
    }
  };

  const loadDeliveries = async () => {
    setDelLoading(true);
    try {
      const res = await webhookApi.listDeliveries({
        endpointId: endpointFilter,
        page: delPage - 1,
        size: delSize,
      });
      const paged = res.data.data;
      setDeliveries(paged?.content ?? []);
      setDelTotal(paged?.totalElements ?? 0);
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Failed to load deliveries');
    } finally {
      setDelLoading(false);
    }
  };

  useEffect(() => {
    loadEndpoints();
  }, []);

  useEffect(() => {
    if (tab === 'deliveries') loadDeliveries();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, delPage, delSize, endpointFilter]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ active: true, eventsArr: ['*'] });
    setFormOpen(true);
  };

  const openEdit = (ep: WebhookEndpoint) => {
    setEditing(ep);
    const eventsArr = ep.events === '*' ? ['*'] : ep.events.split(',').map((s) => s.trim()).filter(Boolean);
    form.setFieldsValue({
      name: ep.name,
      url: ep.url,
      events: ep.events,
      eventsArr,
      active: ep.active,
    });
    setFormOpen(true);
  };

  const submitForm = async () => {
    try {
      const values = await form.validateFields();
      const eventsArr = (values.eventsArr ?? []) as string[];
      const events = eventsArr.includes('*') ? '*' : eventsArr.join(',');
      const payload: WebhookEndpointRequest = {
        name: values.name,
        url: values.url,
        events,
        active: values.active,
      };
      if (editing) {
        await webhookApi.updateEndpoint(editing.id, payload);
        message.success('Endpoint updated');
        setFormOpen(false);
        loadEndpoints();
      } else {
        const res = await webhookApi.createEndpoint(payload);
        const created = res.data.data!;
        setFormOpen(false);
        setSecretModal({ open: true, secret: created.secret, url: created.url });
        loadEndpoints();
      }
    } catch (e: any) {
      if (e?.errorFields) return; // form validation
      message.error(e?.response?.data?.message ?? 'Save failed');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await webhookApi.deleteEndpoint(id);
      message.success('Endpoint deleted');
      loadEndpoints();
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Delete failed');
    }
  };

  const handleRotate = async (id: number) => {
    try {
      const res = await webhookApi.rotateSecret(id);
      const ep = res.data.data!;
      setSecretModal({ open: true, secret: ep.secret, url: ep.url });
      loadEndpoints();
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Rotate failed');
    }
  };

  const handleTest = async (id: number) => {
    try {
      const res = await webhookApi.testEndpoint(id);
      message.success(`Test event enqueued (${res.data.data?.enqueued ?? 1})`);
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Test failed');
    }
  };

  const handleRetry = async (id: number) => {
    try {
      await webhookApi.retryDelivery(id);
      message.success('Delivery re-queued');
      loadDeliveries();
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Retry failed');
    }
  };

  const copy = (text: string) => {
    navigator.clipboard.writeText(text);
    message.success('Copied');
  };

  const endpointColumns: ColumnsType<WebhookEndpoint> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: 'Name', dataIndex: 'name', render: (v) => v || <Text type="secondary">—</Text> },
    {
      title: 'URL',
      dataIndex: 'url',
      ellipsis: true,
      render: (v: string) => (
        <Tooltip title={v}>
          <Text code style={{ fontSize: 12 }}>{v}</Text>
        </Tooltip>
      ),
    },
    {
      title: 'Events',
      dataIndex: 'events',
      render: (v: string) =>
        v === '*' ? (
          <Tag color="blue">All events</Tag>
        ) : (
          <Space wrap size={[4, 4]}>
            {v.split(',').map((e) => (
              <Tag key={e}>{e}</Tag>
            ))}
          </Space>
        ),
    },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 90,
      render: (a: boolean) => (a ? <Tag color="green">Active</Tag> : <Tag>Disabled</Tag>),
    },
    {
      title: 'Last Success',
      dataIndex: 'lastSuccessAt',
      width: 170,
      render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '—'),
    },
    {
      title: 'Last Failure',
      dataIndex: 'lastFailureAt',
      width: 170,
      render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '—'),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 260,
      render: (_, ep) => (
        <Space size="small">
          <Tooltip title="Send test event">
            <Button size="small" icon={<ThunderboltOutlined />} onClick={() => handleTest(ep.id)} />
          </Tooltip>
          <Tooltip title="Edit">
            <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(ep)} />
          </Tooltip>
          <Tooltip title="Rotate secret">
            <Popconfirm
              title="Rotate signing secret?"
              description="The old secret will stop working immediately."
              onConfirm={() => handleRotate(ep.id)}
            >
              <Button size="small" icon={<KeyOutlined />} />
            </Popconfirm>
          </Tooltip>
          <Tooltip title="Delete">
            <Popconfirm title="Delete this endpoint?" onConfirm={() => handleDelete(ep.id)}>
              <Button size="small" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          </Tooltip>
        </Space>
      ),
    },
  ];

  const deliveryColumns: ColumnsType<WebhookDelivery> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: 'Endpoint', dataIndex: 'endpointId', width: 100 },
    { title: 'Event', dataIndex: 'eventType', width: 180, render: (v) => <Tag>{v}</Tag> },
    { title: 'Ref', dataIndex: 'referenceId', width: 80 },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: DeliveryStatus) => <Tag color={statusColor[s]}>{s}</Tag>,
    },
    { title: 'Attempts', dataIndex: 'attempts', width: 90 },
    {
      title: 'Response',
      dataIndex: 'responseStatus',
      width: 100,
      render: (v?: number) => (v ? <Tag color={v < 300 ? 'green' : 'red'}>{v}</Tag> : '—'),
    },
    {
      title: 'Next Attempt',
      dataIndex: 'nextAttemptAt',
      width: 170,
      render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '—'),
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      width: 170,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 140,
      render: (_, d) => (
        <Space size="small">
          <Tooltip title="View">
            <Button size="small" icon={<EyeOutlined />} onClick={() => setDetailDelivery(d)} />
          </Tooltip>
          {(d.status === 'FAILED' || d.status === 'RETRYING') && (
            <Tooltip title="Retry now">
              <Button size="small" icon={<RedoOutlined />} onClick={() => handleRetry(d.id)} />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Title level={3}>Webhook Settings</Title>
      <Paragraph type="secondary">
        Configure HTTPS endpoints to receive real-time payment events. Each delivery is signed with HMAC-SHA256
        using your endpoint secret — verify the <Text code>X-PayRoute-Signature</Text> header on your server.
      </Paragraph>

      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          {
            key: 'endpoints',
            label: 'Endpoints',
            children: (
              <Card
                extra={
                  <Space>
                    <Button icon={<ReloadOutlined />} onClick={loadEndpoints}>
                      Refresh
                    </Button>
                    <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      New Endpoint
                    </Button>
                  </Space>
                }
                title={`Configured Endpoints (${endpoints.length})`}
              >
                <Table
                  rowKey="id"
                  size="small"
                  loading={endpointsLoading}
                  dataSource={endpoints}
                  columns={endpointColumns}
                  pagination={false}
                />
              </Card>
            ),
          },
          {
            key: 'deliveries',
            label: 'Deliveries',
            children: (
              <Card
                title="Delivery Log"
                extra={
                  <Space>
                    <Select
                      allowClear
                      placeholder="Filter by endpoint"
                      style={{ width: 220 }}
                      value={endpointFilter}
                      onChange={(v) => {
                        setEndpointFilter(v);
                        setDelPage(1);
                      }}
                      options={endpoints.map((e) => ({
                        value: e.id,
                        label: `#${e.id} ${e.name || e.url}`,
                      }))}
                    />
                    <Button icon={<ReloadOutlined />} onClick={loadDeliveries}>
                      Refresh
                    </Button>
                  </Space>
                }
              >
                <Table
                  rowKey="id"
                  size="small"
                  loading={delLoading}
                  dataSource={deliveries}
                  columns={deliveryColumns}
                  pagination={{
                    current: delPage,
                    pageSize: delSize,
                    total: delTotal,
                    showSizeChanger: true,
                    onChange: (p, s) => {
                      setDelPage(p);
                      setDelSize(s);
                    },
                  }}
                />
              </Card>
            ),
          },
        ]}
      />

      {/* Create / Edit form */}
      <Modal
        open={formOpen}
        title={editing ? `Edit Endpoint #${editing.id}` : 'New Webhook Endpoint'}
        onCancel={() => setFormOpen(false)}
        onOk={submitForm}
        okText={editing ? 'Save' : 'Create'}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="Name">
            <Input placeholder="e.g. Production webhook" />
          </Form.Item>
          <Form.Item
            name="url"
            label="URL"
            rules={[
              { required: true, message: 'URL is required' },
              { type: 'url', message: 'Must be a valid URL' },
            ]}
          >
            <Input placeholder="https://your-domain.com/webhooks/payroute" />
          </Form.Item>
          <Form.Item
            name="eventsArr"
            label="Events"
            tooltip="Select '*' to subscribe to all event types."
            rules={[{ required: true, message: 'Select at least one event' }]}
          >
            <Select
              mode="multiple"
              placeholder="Select events"
              options={[{ value: '*', label: 'All events (*)' }, ...EVENT_OPTIONS.map((e) => ({ value: e, label: e }))]}
            />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      {/* Secret reveal modal */}
      <Modal
        open={secretModal.open}
        title="Signing Secret"
        onCancel={() => setSecretModal({ open: false, secret: '' })}
        footer={[
          <Button key="close" onClick={() => setSecretModal({ open: false, secret: '' })}>
            Close
          </Button>,
        ]}
      >
        <Paragraph>
          <Text strong>This is the only time the secret will be shown in full.</Text> Store it securely — you'll need
          it to verify the <Text code>X-PayRoute-Signature</Text> header on incoming webhooks.
        </Paragraph>
        {secretModal.url && (
          <Paragraph>
            <Text type="secondary">URL:</Text> <Text code>{secretModal.url}</Text>
          </Paragraph>
        )}
        <Input.Group compact>
          <Input value={secretModal.secret} readOnly style={{ width: 'calc(100% - 40px)' }} />
          <Button icon={<CopyOutlined />} onClick={() => copy(secretModal.secret)} />
        </Input.Group>
      </Modal>

      {/* Delivery detail */}
      <Modal
        open={!!detailDelivery}
        title={detailDelivery ? `Delivery #${detailDelivery.id}` : ''}
        onCancel={() => setDetailDelivery(null)}
        footer={[
          <Button key="close" onClick={() => setDetailDelivery(null)}>
            Close
          </Button>,
        ]}
        width={760}
      >
        {detailDelivery && (
          <>
            <Descriptions bordered size="small" column={2} style={{ marginBottom: 16 }}>
              <Descriptions.Item label="Endpoint">{detailDelivery.endpointId}</Descriptions.Item>
              <Descriptions.Item label="Event">{detailDelivery.eventType}</Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={statusColor[detailDelivery.status]}>{detailDelivery.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Attempts">{detailDelivery.attempts}</Descriptions.Item>
              <Descriptions.Item label="Response">
                {detailDelivery.responseStatus ?? '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Next Attempt">
                {detailDelivery.nextAttemptAt
                  ? dayjs(detailDelivery.nextAttemptAt).format('YYYY-MM-DD HH:mm:ss')
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Last Attempt">
                {detailDelivery.lastAttemptAt
                  ? dayjs(detailDelivery.lastAttemptAt).format('YYYY-MM-DD HH:mm:ss')
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Created">
                {dayjs(detailDelivery.createdAt).format('YYYY-MM-DD HH:mm:ss')}
              </Descriptions.Item>
            </Descriptions>
            <Title level={5}>Payload</Title>
            <pre
              style={{
                background: '#f5f5f5',
                padding: 12,
                borderRadius: 4,
                maxHeight: 240,
                overflow: 'auto',
                fontSize: 12,
              }}
            >
              {(() => {
                try {
                  return JSON.stringify(JSON.parse(detailDelivery.payload), null, 2);
                } catch {
                  return detailDelivery.payload;
                }
              })()}
            </pre>
            {detailDelivery.responseBody && (
              <>
                <Title level={5}>Response Body</Title>
                <pre
                  style={{
                    background: '#f5f5f5',
                    padding: 12,
                    borderRadius: 4,
                    maxHeight: 180,
                    overflow: 'auto',
                    fontSize: 12,
                  }}
                >
                  {detailDelivery.responseBody}
                </pre>
              </>
            )}
          </>
        )}
      </Modal>
    </div>
  );
}
