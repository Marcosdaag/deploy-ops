export type WebhookProviderType = 'SLACK' | 'DISCORD' | 'CUSTOM';

export type OutboxEventType =
  | 'INCIDENT_OPENED'
  | 'INCIDENT_RESOLVED'
  | 'WEBHOOK_TEST_PING'
  | 'CHAOS_SIMULATION';

export type WebhookDeliveryStatus = 'DEAD_LETTER' | 'RETRIED_OK';

export interface WebhookEndpoint {
  id: string;
  name: string;
  targetUrl: string;
  providerType: WebhookProviderType;
  eventTypes: OutboxEventType[];
  enabled: boolean;
  maskedSecret: string;
  rawSecretOnce?: string | null;
  totalDeliveries: number;
  failedDeliveries: number;
  createdAt: string;
  lastDeliveredAt: string | null;
}

export interface WebhookCreateRequest {
  name: string;
  targetUrl: string;
  providerType: WebhookProviderType;
  eventTypes: OutboxEventType[];
  enabled: boolean;
}

export interface WebhookUpdateRequest {
  name: string;
  targetUrl: string;
  providerType: WebhookProviderType;
  eventTypes: OutboxEventType[];
  enabled: boolean;
}

export interface WebhookDeliveryFailure {
  id: string;
  eventId: string;
  webhookId: string;
  webhookName: string;
  targetUrl: string;
  providerType: WebhookProviderType;
  eventType: OutboxEventType;
  payloadJson: string;
  attemptsCount: number;
  lastStatusCode: number | null;
  lastErrorMessage: string | null;
  status: WebhookDeliveryStatus;
  failedAt: string;
  retriedAt: string | null;
}

export interface DistributedOverview {
  nodeId: string;
  redisConnected: boolean;
  rabbitMqConnected: boolean;
  l2CacheHits: number;
  l2CacheMisses: number;
  activeListeners: number;
  queueDepthWebhooksDispatch: number;
  queueDepthWebhooksDlq: number;
  totalOutboxEventsPending: number;
  totalOutboxEventsProcessed: number;
  totalOutboxEventsFailed: number;
  timestamp: string;
}

export interface ChaosState {
  enabled: boolean;
  forcedHttpStatus: number | null;
  injectedLatencyMs: number;
  failureRatePercentage: number;
  serviceOverrideMap: Record<string, string>;
}
