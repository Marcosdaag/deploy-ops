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

export const INITIAL_SHOWCASE_WEBHOOKS: WebhookEndpoint[] = [
  {
    id: 'wh-slack-demo',
    name: 'Slack #alertas-sre-prod',
    targetUrl: 'https://hooks.slack.com/services/T00/B00/deployops-alerts',
    providerType: 'SLACK',
    eventTypes: ['INCIDENT_OPENED', 'INCIDENT_RESOLVED'],
    enabled: true,
    maskedSecret: 'dops_sec_••••••••4f9a',
    rawSecretOnce: null,
    totalDeliveries: 42,
    failedDeliveries: 0,
    createdAt: '2026-11-07T12:00:00Z',
    lastDeliveredAt: '2026-11-07T18:45:12Z'
  },
  {
    id: 'wh-discord-demo',
    name: 'Discord Guardia Activa',
    targetUrl: 'https://discord.com/api/webhooks/123456789/deployops-bot',
    providerType: 'DISCORD',
    eventTypes: ['INCIDENT_OPENED'],
    enabled: true,
    maskedSecret: 'dops_sec_••••••••b2e1',
    rawSecretOnce: null,
    totalDeliveries: 18,
    failedDeliveries: 1,
    createdAt: '2026-11-07T14:30:00Z',
    lastDeliveredAt: '2026-11-07T18:30:00Z'
  }
];

export const INITIAL_SHOWCASE_DLQ: WebhookDeliveryFailure[] = [
  {
    id: 'fail-sample-01',
    eventId: 'evt-7b9d-4c1a',
    webhookId: 'wh-discord-demo',
    webhookName: 'Discord Guardia Activa',
    targetUrl: 'https://discord.com/api/webhooks/123456789/deployops-bot',
    providerType: 'DISCORD',
    eventType: 'INCIDENT_OPENED',
    payloadJson: JSON.stringify(
      {
        event: 'INCIDENT_OPENED',
        serviceName: 'Database Cluster Primario',
        status: 'DOWN',
        httpStatus: 503,
        consecutiveFailures: 3,
        timestamp: '2026-11-07T18:30:00Z'
      },
      null,
      2
    ),
    attemptsCount: 3,
    lastStatusCode: 504,
    lastErrorMessage: 'Gateway Timeout (504): discord.com/api upstream no respondio en 4000ms',
    status: 'DEAD_LETTER',
    failedAt: '2026-11-07T18:30:00Z',
    retriedAt: null
  }
];

export const INITIAL_SHOWCASE_TELEMETRY: DistributedOverview = {
  nodeId: 'deployops-worker-prod-01',
  redisConnected: true,
  rabbitMqConnected: true,
  l2CacheHits: 1284,
  l2CacheMisses: 42,
  activeListeners: 2,
  queueDepthWebhooksDispatch: 0,
  queueDepthWebhooksDlq: 1,
  totalOutboxEventsPending: 0,
  totalOutboxEventsProcessed: 148,
  totalOutboxEventsFailed: 1,
  timestamp: new Date().toISOString()
};
