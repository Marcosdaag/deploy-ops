import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EnterpriseService } from '../../../../core/services/enterprise.service';
import {
  ChaosState,
  DistributedOverview,
  OutboxEventType,
  WebhookCreateRequest,
  WebhookDeliveryFailure,
  WebhookEndpoint,
  WebhookProviderType
} from '../../../../core/models/enterprise.model';

export type EnterpriseTab = 'WEBHOOKS' | 'DLQ' | 'CHAOS' | 'TELEMETRY';

@Component({
  selector: 'app-enterprise-hub',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './enterprise-hub.component.html',
  styleUrl: './enterprise-hub.component.css'
})
export class EnterpriseHubComponent implements OnInit {
  private readonly enterpriseService = inject(EnterpriseService);

  readonly activeTab = signal<EnterpriseTab>('WEBHOOKS');
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  // Webhooks State
  readonly webhooks = signal<WebhookEndpoint[]>([]);
  readonly isCreateModalOpen = signal<boolean>(false);
  readonly isSubmittingWebhook = signal<boolean>(false);
  readonly revealedSecret = signal<string | null>(null);
  readonly secretCopied = signal<boolean>(false);
  readonly pingStatusMap = signal<Record<string, string>>({});

  // Form Model for New Webhook
  newWebhookName = '';
  newWebhookUrl = '';
  newWebhookProvider: WebhookProviderType = 'CUSTOM';
  selectedEventTypes: Record<OutboxEventType, boolean> = {
    INCIDENT_OPENED: true,
    INCIDENT_RESOLVED: true,
    WEBHOOK_TEST_PING: false,
    CHAOS_SIMULATION: false
  };

  // DLQ State
  readonly dlqFailures = signal<WebhookDeliveryFailure[]>([]);
  readonly selectedPayload = signal<string | null>(null);
  readonly retryingMap = signal<Record<string, boolean>>({});

  // Chaos State
  readonly chaosState = signal<ChaosState>({
    enabled: false,
    forcedHttpStatus: null,
    injectedLatencyMs: 0,
    failureRatePercentage: 0,
    serviceOverrideMap: {}
  });
  readonly isSavingChaos = signal<boolean>(false);
  readonly isTriggeringChaos = signal<boolean>(false);

  // Telemetry State
  readonly telemetry = signal<DistributedOverview | null>(null);

  ngOnInit(): void {
    this.loadActiveTabData();
  }

  setTab(tab: EnterpriseTab): void {
    this.activeTab.set(tab);
    this.clearAlerts();
    this.loadActiveTabData();
  }

  loadActiveTabData(): void {
    switch (this.activeTab()) {
      case 'WEBHOOKS':
        this.loadWebhooks();
        break;
      case 'DLQ':
        this.loadDlqFailures();
        break;
      case 'CHAOS':
        this.loadChaosState();
        break;
      case 'TELEMETRY':
        this.loadTelemetry();
        break;
    }
  }

  clearAlerts(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  // --- Webhooks Actions ---

  loadWebhooks(): void {
    this.isLoading.set(true);
    this.enterpriseService.getWebhooks().subscribe({
      next: (list) => {
        this.webhooks.set(list);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.errorMessage.set('Error al cargar la lista de webhooks.');
        this.isLoading.set(false);
      }
    });
  }

  openCreateModal(): void {
    this.newWebhookName = '';
    this.newWebhookUrl = '';
    this.newWebhookProvider = 'CUSTOM';
    this.selectedEventTypes = {
      INCIDENT_OPENED: true,
      INCIDENT_RESOLVED: true,
      WEBHOOK_TEST_PING: false,
      CHAOS_SIMULATION: false
    };
    this.clearAlerts();
    this.isCreateModalOpen.set(true);
  }

  closeCreateModal(): void {
    this.isCreateModalOpen.set(false);
  }

  submitCreateWebhook(): void {
    if (!this.newWebhookName.trim() || !this.newWebhookUrl.trim()) {
      this.errorMessage.set('Nombre y URL de destino son campos obligatorios.');
      return;
    }

    const events: OutboxEventType[] = (Object.keys(this.selectedEventTypes) as OutboxEventType[])
      .filter((k) => this.selectedEventTypes[k]);

    if (events.length === 0) {
      this.errorMessage.set('Debe seleccionar al menos un evento para suscribir.');
      return;
    }

    const payload: WebhookCreateRequest = {
      name: this.newWebhookName.trim(),
      targetUrl: this.newWebhookUrl.trim(),
      providerType: this.newWebhookProvider,
      eventTypes: events,
      enabled: true
    };

    this.isSubmittingWebhook.set(true);
    this.clearAlerts();

    this.enterpriseService.createWebhook(payload).subscribe({
      next: (created) => {
        this.isSubmittingWebhook.set(false);
        this.closeCreateModal();
        this.loadWebhooks();
        if (created.rawSecretOnce) {
          this.revealedSecret.set(created.rawSecretOnce);
          this.secretCopied.set(false);
        }
        this.successMessage.set(`Webhook '${created.name}' creado correctamente.`);
      },
      error: (err) => {
        this.isSubmittingWebhook.set(false);
        const msg = err.error?.messages?.[0] || 'Error al registrar el webhook.';
        this.errorMessage.set(msg);
      }
    });
  }

  testPing(webhook: WebhookEndpoint): void {
    this.pingStatusMap.update((m) => ({ ...m, [webhook.id]: 'Enviando...' }));
    this.enterpriseService.pingWebhook(webhook.id).subscribe({
      next: (res) => {
        this.pingStatusMap.update((m) => ({ ...m, [webhook.id]: 'Ping OK' }));
        setTimeout(() => {
          this.pingStatusMap.update((m) => {
            const next = { ...m };
            delete next[webhook.id];
            return next;
          });
        }, 4000);
      },
      error: () => {
        this.pingStatusMap.update((m) => ({ ...m, [webhook.id]: 'Fallo de entrega' }));
      }
    });
  }

  rotateSecret(webhook: WebhookEndpoint): void {
    if (!confirm(`¿Desea rotar el secreto del webhook '${webhook.name}'? La firma anterior quedará invalidada inmediatamente.`)) {
      return;
    }

    this.clearAlerts();
    this.enterpriseService.rotateSecret(webhook.id).subscribe({
      next: (res) => {
        this.loadWebhooks();
        if (res.rawSecretOnce) {
          this.revealedSecret.set(res.rawSecretOnce);
          this.secretCopied.set(false);
        }
        this.successMessage.set(`Secreto criptográfico rotado para '${webhook.name}'.`);
      },
      error: () => {
        this.errorMessage.set('Error al rotar el secreto criptográfico.');
      }
    });
  }

  deleteWebhook(webhook: WebhookEndpoint): void {
    if (!confirm(`¿Eliminar definitivamente el webhook '${webhook.name}'?`)) {
      return;
    }

    this.clearAlerts();
    this.enterpriseService.deleteWebhook(webhook.id).subscribe({
      next: () => {
        this.loadWebhooks();
        this.successMessage.set(`Webhook '${webhook.name}' eliminado.`);
      },
      error: () => {
        this.errorMessage.set('Error al eliminar el webhook.');
      }
    });
  }

  copySecret(): void {
    const s = this.revealedSecret();
    if (s && navigator?.clipboard) {
      navigator.clipboard.writeText(s).then(() => {
        this.secretCopied.set(true);
        setTimeout(() => this.secretCopied.set(false), 3000);
      });
    }
  }

  closeSecretModal(): void {
    this.revealedSecret.set(null);
    this.secretCopied.set(false);
  }

  // --- DLQ Actions ---

  loadDlqFailures(): void {
    this.isLoading.set(true);
    this.enterpriseService.getDlqFailures().subscribe({
      next: (list) => {
        this.dlqFailures.set(list);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al cargar la lista de fallas en Dead Letter Queue.');
        this.isLoading.set(false);
      }
    });
  }

  retryFailure(failure: WebhookDeliveryFailure): void {
    this.retryingMap.update((m) => ({ ...m, [failure.id]: true }));
    this.clearAlerts();

    this.enterpriseService.retryDlqFailure(failure.id).subscribe({
      next: () => {
        this.retryingMap.update((m) => ({ ...m, [failure.id]: false }));
        this.successMessage.set(`Reintento de entrega ejecutado con éxito.`);
        this.loadDlqFailures();
      },
      error: (err) => {
        this.retryingMap.update((m) => ({ ...m, [failure.id]: false }));
        const msg = err.error?.messages?.[0] || 'Fallo al reintentar la entrega del webhook.';
        this.errorMessage.set(msg);
      }
    });
  }

  viewPayload(failure: WebhookDeliveryFailure): void {
    try {
      const parsed = JSON.parse(failure.payloadJson);
      this.selectedPayload.set(JSON.stringify(parsed, null, 2));
    } catch {
      this.selectedPayload.set(failure.payloadJson);
    }
  }

  closePayloadModal(): void {
    this.selectedPayload.set(null);
  }

  // --- Chaos Actions ---

  loadChaosState(): void {
    this.isLoading.set(true);
    this.enterpriseService.getChaosState().subscribe({
      next: (state) => {
        this.chaosState.set(state);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al sincronizar estado de simulación de caos.');
        this.isLoading.set(false);
      }
    });
  }

  saveChaosState(): void {
    this.isSavingChaos.set(true);
    this.clearAlerts();

    this.enterpriseService.updateChaosState(this.chaosState()).subscribe({
      next: (updated) => {
        this.chaosState.set(updated);
        this.isSavingChaos.set(false);
        this.successMessage.set('Parámetros de caos actualizados correctamente.');
      },
      error: () => {
        this.isSavingChaos.set(false);
        this.errorMessage.set('Error al guardar la configuración de caos.');
      }
    });
  }

  triggerSimulatedEvent(): void {
    this.isTriggeringChaos.set(true);
    this.clearAlerts();

    this.enterpriseService.triggerChaosEvent().subscribe({
      next: (res) => {
        this.isTriggeringChaos.set(false);
        this.successMessage.set(`Evento simulado emitido [ID: ${res.eventId.substring(0, 8)}].`);
      },
      error: () => {
        this.isTriggeringChaos.set(false);
        this.errorMessage.set('Error al disparar el evento de caos simulado.');
      }
    });
  }

  // --- Telemetry Actions ---

  loadTelemetry(): void {
    this.isLoading.set(true);
    this.enterpriseService.getDistributedOverview().subscribe({
      next: (overview) => {
        this.telemetry.set(overview);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al consultar telemetría distribuida del cluster.');
        this.isLoading.set(false);
      }
    });
  }
}
