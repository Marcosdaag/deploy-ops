import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EnterpriseHubComponent } from './enterprise-hub.component';
import { EnterpriseService } from '../../../../core/services/enterprise.service';
import { AuthService } from '../../../../core/services/auth.service';
import { of } from 'rxjs';
import {
  ChaosState,
  DistributedOverview,
  WebhookDeliveryFailure,
  WebhookEndpoint
} from '../../../../core/models/enterprise.model';

describe('EnterpriseHubComponent', () => {
  let component: EnterpriseHubComponent;
  let fixture: ComponentFixture<EnterpriseHubComponent>;
  let mockEnterpriseService: jasmine.SpyObj<EnterpriseService>;
  let mockAuthService: { isAuthenticated: jasmine.Spy; isDemoSession: jasmine.Spy };

  const mockWebhooks: WebhookEndpoint[] = [
    {
      id: 'wh-123',
      name: 'Slack SRE Alert',
      targetUrl: 'https://hooks.slack.com/services/test',
      providerType: 'SLACK',
      eventTypes: ['INCIDENT_OPENED'],
      enabled: true,
      maskedSecret: 'dops_sec_••••••••abcd',
      totalDeliveries: 5,
      failedDeliveries: 0,
      createdAt: '2026-11-07T12:00:00Z',
      lastDeliveredAt: '2026-11-07T12:05:00Z'
    }
  ];

  const mockFailures: WebhookDeliveryFailure[] = [
    {
      id: 'fail-1',
      eventId: 'evt-1',
      webhookId: 'wh-123',
      webhookName: 'Slack SRE Alert',
      targetUrl: 'https://hooks.slack.com/services/test',
      providerType: 'SLACK',
      eventType: 'INCIDENT_OPENED',
      payloadJson: '{"test":true}',
      attemptsCount: 3,
      lastStatusCode: 504,
      lastErrorMessage: 'Gateway Timeout',
      status: 'DEAD_LETTER',
      failedAt: '2026-11-07T12:10:00Z',
      retriedAt: null
    }
  ];

  const mockChaos: ChaosState = {
    enabled: true,
    forcedHttpStatus: 503,
    injectedLatencyMs: 500,
    failureRatePercentage: 20,
    serviceOverrideMap: {}
  };

  const mockOverview: DistributedOverview = {
    nodeId: 'test-node-1',
    redisConnected: true,
    rabbitMqConnected: true,
    l2CacheHits: 50,
    l2CacheMisses: 5,
    activeListeners: 2,
    queueDepthWebhooksDispatch: 0,
    queueDepthWebhooksDlq: 0,
    totalOutboxEventsPending: 0,
    totalOutboxEventsProcessed: 10,
    totalOutboxEventsFailed: 0,
    timestamp: '2026-11-07T12:15:00Z'
  };

  beforeEach(async () => {
    mockEnterpriseService = jasmine.createSpyObj('EnterpriseService', [
      'getWebhooks',
      'createWebhook',
      'updateWebhook',
      'deleteWebhook',
      'pingWebhook',
      'rotateSecret',
      'getDlqFailures',
      'retryDlqFailure',
      'getDistributedOverview',
      'getChaosState',
      'updateChaosState',
      'triggerChaosEvent'
    ]);

    mockAuthService = {
      isAuthenticated: jasmine.createSpy('isAuthenticated').and.returnValue(true),
      isDemoSession: jasmine.createSpy('isDemoSession').and.returnValue(false)
    };

    mockEnterpriseService.getWebhooks.and.returnValue(of(mockWebhooks));
    mockEnterpriseService.getDlqFailures.and.returnValue(of(mockFailures));
    mockEnterpriseService.getChaosState.and.returnValue(of(mockChaos));
    mockEnterpriseService.getDistributedOverview.and.returnValue(of(mockOverview));

    await TestBed.configureTestingModule({
      imports: [EnterpriseHubComponent],
      providers: [
        { provide: EnterpriseService, useValue: mockEnterpriseService },
        { provide: AuthService, useValue: mockAuthService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(EnterpriseHubComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deberia inicializarse y cargar los webhooks por defecto', () => {
    expect(component).toBeTruthy();
    expect(component.activeTab()).toBe('WEBHOOKS');
    expect(mockEnterpriseService.getWebhooks).toHaveBeenCalled();
    expect(component.webhooks().length).toBe(1);
    expect(component.webhooks()[0].name).toBe('Slack SRE Alert');
  });

  it('deberia cambiar de pestaña y consultar datos correspondientes', () => {
    component.setTab('DLQ');
    expect(component.activeTab()).toBe('DLQ');
    expect(mockEnterpriseService.getDlqFailures).toHaveBeenCalled();

    component.setTab('CHAOS');
    expect(component.activeTab()).toBe('CHAOS');
    expect(mockEnterpriseService.getChaosState).toHaveBeenCalled();

    component.setTab('TELEMETRY');
    expect(component.activeTab()).toBe('TELEMETRY');
    expect(mockEnterpriseService.getDistributedOverview).toHaveBeenCalled();
  });

  it('deberia crear webhook y revelar el secreto de una sola vez', () => {
    const created: WebhookEndpoint = {
      id: 'wh-new',
      name: 'Discord Ops',
      targetUrl: 'https://discord.com/api/webhooks/123',
      providerType: 'DISCORD',
      eventTypes: ['INCIDENT_OPENED'],
      enabled: true,
      maskedSecret: 'dops_sec_••••••••9999',
      rawSecretOnce: 'dops_sec_raw_single_use_token_123',
      totalDeliveries: 0,
      failedDeliveries: 0,
      createdAt: '2026-11-07T12:20:00Z',
      lastDeliveredAt: null
    };

    mockEnterpriseService.createWebhook.and.returnValue(of(created));

    component.newWebhookName = 'Discord Ops';
    component.newWebhookUrl = 'https://discord.com/api/webhooks/123';
    component.newWebhookProvider = 'DISCORD';

    component.submitCreateWebhook();

    expect(mockEnterpriseService.createWebhook).toHaveBeenCalled();
    expect(component.revealedSecret()).toBe('dops_sec_raw_single_use_token_123');
    expect(component.isCreateModalOpen()).toBeFalse();
  });

  it('deberia emitir ping de prueba y actualizar estado de ping', () => {
    mockEnterpriseService.pingWebhook.and.returnValue(
      of({ message: 'Ping OK', timestamp: '2026-11-07T12:22:00Z' })
    );

    component.testPing(mockWebhooks[0]);

    expect(mockEnterpriseService.pingWebhook).toHaveBeenCalledWith('wh-123');
    expect(component.pingStatusMap()['wh-123']).toBe('Ping OK ✓');
  });

  it('deberia reintentar una falla en DLQ', () => {
    mockEnterpriseService.retryDlqFailure.and.returnValue(
      of({ message: 'Retry OK', status: 'RETRIED_OK' })
    );

    component.retryFailure(mockFailures[0]);

    expect(mockEnterpriseService.retryDlqFailure).toHaveBeenCalledWith('fail-1');
  });

  it('deberia actualizar parametros de simulacion de caos', () => {
    mockEnterpriseService.updateChaosState.and.returnValue(of(mockChaos));

    component.saveChaosState();

    expect(mockEnterpriseService.updateChaosState).toHaveBeenCalled();
    expect(component.isSavingChaos()).toBeFalse();
  });

  it('deberia disparar evento de incidente simulado', () => {
    mockEnterpriseService.triggerChaosEvent.and.returnValue(
      of({ message: 'Chaos event triggered', eventId: 'evt-chaos-456' })
    );

    component.triggerSimulatedEvent();

    expect(mockEnterpriseService.triggerChaosEvent).toHaveBeenCalled();
    expect(component.isTriggeringChaos()).toBeFalse();
  });
});
