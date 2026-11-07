import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EnterpriseService } from './enterprise.service';
import {
  ChaosState,
  DistributedOverview,
  WebhookCreateRequest,
  WebhookDeliveryFailure,
  WebhookEndpoint
} from '../models/enterprise.model';

describe('EnterpriseService', () => {
  let service: EnterpriseService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [EnterpriseService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(EnterpriseService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('deberia consultar lista de webhooks', () => {
    const mockWebhooks: WebhookEndpoint[] = [
      {
        id: 'wh-1',
        name: 'Slack Alerts',
        targetUrl: 'https://hooks.slack.com/services/test',
        providerType: 'SLACK',
        eventTypes: ['INCIDENT_OPENED'],
        enabled: true,
        maskedSecret: 'whsec_••••••••1234',
        totalDeliveries: 10,
        failedDeliveries: 0,
        createdAt: '2026-11-07T12:00:00Z',
        lastDeliveredAt: '2026-11-07T12:05:00Z'
      }
    ];

    service.getWebhooks().subscribe((res) => {
      expect(res.length).toBe(1);
      expect(res[0].name).toBe('Slack Alerts');
    });

    const req = httpMock.expectOne(`${service.baseApiUrl}/webhooks`);
    expect(req.request.method).toBe('GET');
    req.flush(mockWebhooks);
  });

  it('deberia crear un nuevo webhook', () => {
    const payload: WebhookCreateRequest = {
      name: 'Discord Ops',
      targetUrl: 'https://discord.com/api/webhooks/test',
      providerType: 'DISCORD',
      eventTypes: ['INCIDENT_OPENED', 'INCIDENT_RESOLVED'],
      enabled: true
    };

    const mockCreated: WebhookEndpoint = {
      id: 'wh-2',
      ...payload,
      maskedSecret: 'whsec_••••••••abcd',
      rawSecretOnce: 'whsec_secret_full_token',
      totalDeliveries: 0,
      failedDeliveries: 0,
      createdAt: '2026-11-07T12:10:00Z',
      lastDeliveredAt: null
    };

    service.createWebhook(payload).subscribe((res) => {
      expect(res.id).toBe('wh-2');
      expect(res.rawSecretOnce).toBe('whsec_secret_full_token');
    });

    const req = httpMock.expectOne(`${service.baseApiUrl}/webhooks`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(mockCreated);
  });

  it('deberia rotar el secreto criptografico de un webhook', () => {
    const mockRotated: WebhookEndpoint = {
      id: 'wh-1',
      name: 'Slack Alerts',
      targetUrl: 'https://hooks.slack.com/services/test',
      providerType: 'SLACK',
      eventTypes: ['INCIDENT_OPENED'],
      enabled: true,
      maskedSecret: 'whsec_••••••••9999',
      rawSecretOnce: 'whsec_new_rotated_secret',
      totalDeliveries: 10,
      failedDeliveries: 0,
      createdAt: '2026-11-07T12:00:00Z',
      lastDeliveredAt: null
    };

    service.rotateSecret('wh-1').subscribe((res) => {
      expect(res.rawSecretOnce).toBe('whsec_new_rotated_secret');
    });

    const req = httpMock.expectOne(`${service.baseApiUrl}/webhooks/wh-1/rotate-secret`);
    expect(req.request.method).toBe('POST');
    req.flush(mockRotated);
  });

  it('deberia consultar fallas en DLQ y reintentar una entrega', () => {
    const mockFailures: WebhookDeliveryFailure[] = [
      {
        id: 'fail-1',
        eventId: 'evt-1',
        webhookId: 'wh-1',
        webhookName: 'Slack Alerts',
        targetUrl: 'https://hooks.slack.com/services/test',
        providerType: 'SLACK',
        eventType: 'INCIDENT_OPENED',
        payloadJson: '{"event":"INCIDENT_OPENED"}',
        attemptsCount: 3,
        lastStatusCode: 503,
        lastErrorMessage: 'Service Unavailable',
        status: 'DEAD_LETTER',
        failedAt: '2026-11-07T12:20:00Z',
        retriedAt: null
      }
    ];

    service.getDlqFailures().subscribe((res) => {
      expect(res.length).toBe(1);
      expect(res[0].status).toBe('DEAD_LETTER');
    });

    const getReq = httpMock.expectOne(`${service.baseApiUrl}/webhooks/failures`);
    expect(getReq.request.method).toBe('GET');
    getReq.flush(mockFailures);

    service.retryDlqFailure('fail-1').subscribe((res) => {
      expect(res.status).toBe('RETRIED_OK');
    });

    const retryReq = httpMock.expectOne(`${service.baseApiUrl}/webhooks/failures/fail-1/retry`);
    expect(retryReq.request.method).toBe('POST');
    retryReq.flush({ message: 'Delivery reattempt successful', status: 'RETRIED_OK' });
  });

  it('deberia obtener la telemetria distribuida del cluster', () => {
    const mockOverview: DistributedOverview = {
      nodeId: 'deployops-node-1',
      redisConnected: true,
      rabbitMqConnected: true,
      l2CacheHits: 140,
      l2CacheMisses: 10,
      activeListeners: 2,
      queueDepthWebhooksDispatch: 0,
      queueDepthWebhooksDlq: 1,
      totalOutboxEventsPending: 0,
      totalOutboxEventsProcessed: 25,
      totalOutboxEventsFailed: 1,
      timestamp: '2026-11-07T12:30:00Z'
    };

    service.getDistributedOverview().subscribe((res) => {
      expect(res.nodeId).toBe('deployops-node-1');
      expect(res.redisConnected).toBeTrue();
      expect(res.rabbitMqConnected).toBeTrue();
    });

    const req = httpMock.expectOne(`${service.baseApiUrl}/distributed/overview`);
    expect(req.request.method).toBe('GET');
    req.flush(mockOverview);
  });

  it('deberia consultar y actualizar el estado del simulador de caos', () => {
    const mockChaos: ChaosState = {
      enabled: false,
      forcedHttpStatus: null,
      injectedLatencyMs: 0,
      failureRatePercentage: 0,
      serviceOverrideMap: {}
    };

    service.getChaosState().subscribe((res) => {
      expect(res.enabled).toBeFalse();
    });

    const getReq = httpMock.expectOne(`${service.baseApiUrl}/chaos/state`);
    getReq.flush(mockChaos);

    const updatedChaos: ChaosState = {
      ...mockChaos,
      enabled: true,
      forcedHttpStatus: 503,
      injectedLatencyMs: 1200
    };

    service.updateChaosState(updatedChaos).subscribe((res) => {
      expect(res.enabled).toBeTrue();
      expect(res.forcedHttpStatus).toBe(503);
    });

    const putReq = httpMock.expectOne(`${service.baseApiUrl}/chaos/state`);
    expect(putReq.request.method).toBe('PUT');
    putReq.flush(updatedChaos);
  });
});
