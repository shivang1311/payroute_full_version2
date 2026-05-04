import { useEffect, useState } from 'react';
import {
  Button,
  Card,
  Form,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
  Popconfirm,
  Alert,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  EditOutlined,
  DeleteOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { slaApi } from '../../api/sla.api';
import type { SlaConfig, SlaConfigRequest } from '../../types/sla';
import type { RailType } from '../../types';

const { Title, Text } = Typography;

const RAIL_OPTIONS: RailType[] = ['BOOK', 'NEFT', 'RTGS', 'IMPS', 'ACH', 'WIRE'];

const RAIL_COLORS: Record<RailType, string> = {
  BOOK: 'blue',
  NEFT: 'green',
  RTGS: 'purple',
  IMPS: 'orange',
  ACH: 'cyan',
  WIRE: 'red',
};

export default function SlaConfigPage() {
  const [loading, setLoading] = useState(false);
  const [configs, setConfigs] = useState<SlaConfig[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<SlaConfig | null>(null);
  const [form] = Form.useForm<SlaConfigRequest>();

  const fetchAll = async () => {
    setLoading(true);
    try {
      const res = await slaApi.getAll();
      setConfigs(res.data.data || []);
    } catch (err) {
      console.error(err);
      message.error('Failed to load SLA configurations');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAll();
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ active: true });
    setModalOpen(true);
  };

  const openEdit = (row: SlaConfig) => {
    setEditing(row);
    form.setFieldsValue({
      rail: row.rail,
      targetTatSeconds: row.targetTatSeconds,
      warningThresholdSeconds: row.warningThresholdSeconds,
      active: row.active,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (editing) {
        await slaApi.update(editing.id, values);
        message.success(`Updated SLA for ${values.rail}`);
      } else {
        await slaApi.create(values);
        message.success(`Created SLA for ${values.rail}`);
      }
      setModalOpen(false);
      fetchAll();
    } catch (err) {
      const e = err as { response?: { data?: { message?: string } } };
      if (e?.response?.data?.message) message.error(e.response.data.message);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await slaApi.delete(id);
      message.success('SLA config deleted');
      fetchAll();
    } catch {
      message.error('Delete failed');
    }
  };

  const columns: ColumnsType<SlaConfig> = [
    {
      title: 'Rail',
      dataIndex: 'rail',
      key: 'rail',
      width: 110,
      render: (r: RailType) => <Tag color={RAIL_COLORS[r]}>{r}</Tag>,
    },
    {
      title: 'Target TAT',
      dataIndex: 'targetTatSeconds',
      key: 'targetTatSeconds',
      render: (s: number) => (
        <Space>
          <ClockCircleOutlined />
          <Text strong>{s}s</Text>
        </Space>
      ),
    },
    {
      title: 'Warning Threshold',
      dataIndex: 'warningThresholdSeconds',
      key: 'warningThresholdSeconds',
      render: (s: number) => <Text type="warning">{s}s</Text>,
    },
    {
      title: 'Status',
      dataIndex: 'active',
      key: 'active',
      width: 100,
      render: (a: boolean) =>
        a ? <Tag color="success">ACTIVE</Tag> : <Tag color="default">INACTIVE</Tag>,
    },
    {
      title: 'Updated',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      render: (v?: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 160,
      render: (_, row) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(row)}>
            Edit
          </Button>
          <Popconfirm
            title="Delete this SLA?"
            description="The breach scanner will stop checking this rail."
            onConfirm={() => handleDelete(row.id)}
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>
            SLA Configuration
          </Title>
          <Text type="secondary">
            Per-rail target turn-around times. Rail instructions exceeding the target trigger a
            SYSTEM / WARNING notification.
          </Text>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchAll}>
            Refresh
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Add SLA
          </Button>
        </Space>
      </Space>

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Breach scanner runs every 60 seconds"
        description="Any open rail instruction (PENDING/SENT/ACKNOWLEDGED) older than its rail's target will be flagged exactly once and alerted to operations."
      />

      <Card>
        <Table<SlaConfig>
          rowKey="id"
          loading={loading}
          dataSource={configs}
          columns={columns}
          pagination={false}
        />
      </Card>

      <Modal
        title={editing ? `Edit SLA — ${editing.rail}` : 'Add SLA configuration'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        okText={editing ? 'Update' : 'Create'}
        destroyOnClose
      >
        <Form<SlaConfigRequest> form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="rail"
            label="Rail"
            rules={[{ required: true, message: 'Select a rail' }]}
          >
            <Select
              disabled={!!editing}
              placeholder="Select rail"
              options={RAIL_OPTIONS.map((r) => ({ value: r, label: r }))}
            />
          </Form.Item>
          <Form.Item
            name="targetTatSeconds"
            label="Target TAT (seconds)"
            rules={[{ required: true, type: 'number', min: 1 }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="warningThresholdSeconds"
            label="Warning threshold (seconds)"
            tooltip="Soft-warning horizon; operations tooling can surface instructions crossing this before target."
            rules={[{ required: true, type: 'number', min: 1 }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
