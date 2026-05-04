import React, { useEffect, useState, useCallback } from 'react';
import { Card, List, Tag, Badge, Button, Space, Typography, Empty, Spin, message } from 'antd';
import { ReloadOutlined, CheckOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import { notificationApi } from '../../api/notification.api';
import { useNotificationStore } from '../../stores/notificationStore';
import { paymentRef } from '../../utils/paymentRef';
import type { Notification, NotificationCategory } from '../../types';

dayjs.extend(relativeTime);

const { Text } = Typography;

const categoryColorMap: Record<NotificationCategory, string> = {
  PAYMENT: 'blue',
  COMPLIANCE: 'purple',
  EXCEPTION: 'orange',
  SETTLEMENT: 'cyan',
  SYSTEM: 'default',
};

const severityColorMap: Record<string, string> = {
  INFO: 'blue',
  WARNING: 'gold',
  ERROR: 'red',
  CRITICAL: 'magenta',
};

const NotificationsPage: React.FC = () => {
  const [data, setData] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [markingAll, setMarkingAll] = useState(false);
  const fetchUnreadCount = useNotificationStore((s) => s.fetchUnreadCount);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await notificationApi.getAll({ page, size: pageSize });
      const paged = response.data.data;
      setData(paged.content);
      setTotal(paged.totalElements);
    } catch {
      message.error('Failed to load notifications');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleMarkAsRead = async (id: number) => {
    try {
      await notificationApi.markAsRead(id);
      setData((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true, readAt: new Date().toISOString() } : n))
      );
      fetchUnreadCount();
    } catch {
      message.error('Failed to mark notification as read');
    }
  };

  const handleMarkAllAsRead = async () => {
    setMarkingAll(true);
    try {
      await notificationApi.markAllAsRead();
      message.success('All notifications marked as read');
      fetchData();
      fetchUnreadCount();
    } catch {
      message.error('Failed to mark all as read');
    } finally {
      setMarkingAll(false);
    }
  };

  return (
    <Card
      title="Notifications"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchData}>
            Refresh
          </Button>
          <Button
            icon={<CheckOutlined />}
            onClick={handleMarkAllAsRead}
            loading={markingAll}
          >
            Mark All as Read
          </Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        {data.length === 0 && !loading ? (
          <Empty description="No notifications" />
        ) : (
          <List
            itemLayout="horizontal"
            dataSource={data}
            pagination={{
              current: page + 1,
              pageSize,
              total,
              showSizeChanger: true,
              showTotal: (t) => `Total ${t} notifications`,
              onChange: (p, ps) => { setPage(p - 1); setPageSize(ps); },
            }}
            renderItem={(item) => (
              <List.Item
                className={item.isRead ? 'pr-notif-row pr-notif-row--read' : 'pr-notif-row pr-notif-row--unread'}
                style={{
                  cursor: item.isRead ? 'default' : 'pointer',
                  padding: '12px 16px',
                  borderRadius: 8,
                  marginBottom: 6,
                }}
                onClick={() => {
                  if (!item.isRead) handleMarkAsRead(item.id);
                }}
                extra={
                  <Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                    {dayjs(item.createdAt).fromNow()}
                  </Text>
                }
              >
                <List.Item.Meta
                  avatar={
                    <Badge dot={!item.isRead} offset={[-2, 2]}>
                      <Badge
                        count={item.severity}
                        style={{
                          backgroundColor: severityColorMap[item.severity] === 'gold' ? '#faad14' :
                            severityColorMap[item.severity] === 'magenta' ? '#eb2f96' :
                            severityColorMap[item.severity] === 'red' ? '#ff4d4f' : '#1677ff',
                          fontSize: 10,
                          padding: '0 6px',
                        }}
                      />
                    </Badge>
                  }
                  title={
                    <Space>
                      <Text strong={!item.isRead}>{item.title}</Text>
                      <Tag color={categoryColorMap[item.category]}>{item.category}</Tag>
                    </Space>
                  }
                  description={
                    <div>
                      {item.message && <div>{item.message}</div>}
                      {item.referenceType && item.referenceId !== undefined && (
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          Ref: {item.referenceType}{' '}
                          {item.referenceType === 'PAYMENT'
                            ? paymentRef({ id: item.referenceId, createdAt: item.createdAt })
                            : `#${item.referenceId}`}
                        </Text>
                      )}
                    </div>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Spin>
    </Card>
  );
};

export default NotificationsPage;
