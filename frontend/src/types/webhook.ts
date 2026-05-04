export type DeliveryStatus = 'PENDING' | 'RETRYING' | 'SUCCESS' | 'FAILED';
export type WebhookEventType =
  | 'PAYMENT_INITIATED'
  | 'PAYMENT_VALIDATED'
  | 'PAYMENT_HELD'
  | 'PAYMENT_ROUTED'
  | 'PAYMENT_COMPLETED'
  | 'PAYMENT_FAILED'
  | 'PAYMENT_REVERSED';

export interface WebhookEndpoint {
  id: number;
  userId: number;
  name?: string;
  url: string;
  secret: string;
  events: string;
  active: boolean;
  lastSuccessAt?: string;
  lastFailureAt?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface WebhookDelivery {
  id: number;
  endpointId: number;
  endpointUrl?: string;
  eventType: WebhookEventType;
  referenceId?: number;
  payload: string;
  status: DeliveryStatus;
  attempts: number;
  nextAttemptAt?: string;
  lastAttemptAt?: string;
  responseStatus?: number;
  responseBody?: string;
  createdAt: string;
}

export interface WebhookEndpointRequest {
  name?: string;
  url: string;
  secret?: string;
  events?: string;
  active?: boolean;
}
