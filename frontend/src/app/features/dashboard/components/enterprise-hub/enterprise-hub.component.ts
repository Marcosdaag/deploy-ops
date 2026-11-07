import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EnterpriseService } from '../../../../core/services/enterprise.service';
import { AuthService } from '../../../../core/services/auth.service';
import {
  ChaosState,
  DistributedOverview,
  INITIAL_SHOWCASE_DLQ,
  INITIAL_SHOWCASE_TELEMETRY,
  INITIAL_SHOWCASE_WEBHOOKS,
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
  readonly authService = inject(AuthService);

  readonly activeTab = signal<EnterpriseTab>('WEBHOOKS');
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  // Webhooks State
  readonly webhooks = signal<WebhookEndpoint[]>(INITIAL_SHOWCASE_WEBHOOKS);
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
  readonly dlqFailures = signal<WebhookDeliveryFailure[]>(INITIAL_SHOWCASE_DLQ);
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
  readonly telemetry = signal<DistributedOverview>(INITIAL_SHOWCASE_TELEMETRY);

  ngOnInit(): void {
    if (this.authService.isAuthenticated() && !this.authService.isDemoSession()) {
      this.loadActiveTabData();
    }
  }

  setTab(tab: EnterpriseTab): void {
    this.activeTab.set(tab);
    this.clearAlerts();
    if (this.authService.isAuthenticated() && !this.authService.isDemoSession()) {
      this.loadActiveTabData();
    }
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
        this.webhooks.set(list && list.length > 0 ? list : INITIAL_SHOWCASE_WEBHOOKS);
        this.isLoading.set(false);
      },
      error: () => {
        // En caso de fallo o sesion expirada, mantener showcase sin romper la pantalla
        this.webhooks.set(INITIAL_SHOWCASE_WEBHOOKS);
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

    if (this.authService.isAuthenticated() && !this.authService.isDemoSession()) {
      this.enterpriseService.createWebhook(payload).subscribe({
        next: (created) => {
          this.isSubmittingWebhook.set(false);
          this.closeCreateModal();
          this.loadWebhooks();
          if (created.rawSecretOnce) {
            this.revealedSecret.set(created.rawSecretOnce);
            this.secretCopied.set(false);
          }
          this.successMessage.set(`Webhook '${created.name}' registrado exitosamente.`);
        },
        error: (err) => {
          this.isSubmittingWebhook.set(false);
          const msg = err.error?.messages?.[0] || 'Error al registrar el webhook.';
          this.errorMessage.set(msg);
        }
      });
    } else {
      // Modo Demo / Showcase interactivo
      setTimeout(() => {
        const dummyId = 'wh-custom-' + Date.now().toString(36);
        const dummySecret = 'dops_sec_' + Math.random().toString(36).substring(2) + 'a8f9c0e2';
        const newWh: WebhookEndpoint = {
          id: dummyId,
          ...payload,
          maskedSecret: 'dops_sec_••••••••' + dummySecret.slice(-4),
          rawSecretOnce: dummySecret,
          totalDeliveries: 0,
          failedDeliveries: 0,
          createdAt: new Date().toISOString(),
          lastDeliveredAt: null
        };
        this.webhooks.update((list) => [newWh, ...list]);
        this.isSubmittingWebhook.set(false);
        this.closeCreateModal();
        this.revealedSecret.set(dummySecret);
        this.secretCopied.set(false);
        this.successMessage.set(`Webhook de demostracion '${newWh.name}' simulado correctamente.`);
      }, 350);
    }
  }

  testPing(webhook: WebhookEndpoint): void {
    this.pingStatusMap.update((m) => ({ ...m, [webhook.id]: 'Enviando...' }));

    if (this.authService.isAuthenticated() && !this.authService.isDemoSession()) {
      this.enterpriseService.pingWebhook(webhook.id).subscribe({
        next: () => {
          this.pingStatusMap.update((m) => ({ ...m, [webhook.id]: 'Ping OK ✓' }));
          setTimeout(() => {
            this.pingStatusMap.update((m) => {
              const next = { ...m };
              delete next[webhook.id];
              return next;
            });
          }, 3500);
        },
        error: () => {
          this.pingStatusMap.update((m) => ({ ...m, [webhook.id]: 'Fallo entrega' }));
          setTimeout(() => {
            this.pingStatusMap.update((m) => {
              const next = { ...m };
              delete next[webhook.id];
              return next;
            });
          }, 3500);
        }
      });
    } else {
      // Modo Demo
      setTimeout(() => {
        this.pingStatusMap.update((m) => ({ ...m, [webhook.id]: 'Ping OK ✓' }));
        setTimeout(() => {
          this.pingStatusMap.update((m) => {
            const next = { ...m };
            delete next[webhook.id];
            return next;
          });
        }, 3500);
      }, 400);
    }
  }

  rotateSecret(webhook: WebhookEndpoint): void {
    if (!confirm(`¿Rotar la clave de firma del webhook '${webhook.name}'? La clave anterior quedara invalidada.`)) {
      return;
    }

    this.clearAlerts();

    if (this.authService.isAuthenticated() && !this.authService.isDemoSession()) {
      this.enterpriseService.rotateSecret(webhook.id).subscribe({
        next: (res) => {
          this.loadWebhooks();
          if (res.rawSecretOnce) {
            this.revealedSecret.set(res.rawSecretOnce);
            this.secretCopied.set(false);
          }
          this.successMessage.set(`Clave criptografica rotada exitosamente.`);
        },
        error: () => {
          this.errorMessage.set('Error al rotar la clave de firma.');
        }
      });
    } else {
      // Modo Demo
      const newSecret = 'dops_sec_rot_' + Math.random().toString(36).substring(2) + 'ff01';
      this.webhooks.update((list) =>
        list.map((w) =>
          w.id === webhook.id
            ? { ...w, maskedSecret: 'dops_sec_••••••••' + newSecret.slice(-4), rawSecretOnce: newSecret }
            : w
        )
      );
      this.revealedSecret.set(newSecret);
      this.secretCopied.set(false);
      this.successMessage.set(`Clave criptografica rotada (Modo Demostracion).`);
    }
  }

  deleteWebhook(webhook: WebhookEndpoint): void {
    if (!confirm(`¿Desea eliminar definitivamente el webhook '${webhook.name}'?`)) {
      return;
    }

    this.clearAlerts();

    if (this.authService.isAuthenticated() && !this.authService.isDemoSession()) {
      this.enterpriseService.deleteWebhook(webhook.id).subscribe({
        next: () => {
          this.loadWebhooks();
          this.successMessage.set(`Webhook '${webhook.name}' eliminado.`);
        },
        error: () => {
          this.errorMessage.set('Error al eliminar el webhook.');
        }
      });
    } else {
      this.webhooks.update((list) => list.filter((w) => w.id !== webhook.id));
      this.successMessage.set(`Webhook '${webhook.name}' eliminado del visor.`);
    }
  }

  copySecret(): void {
    const s = this.revealedSecret();
    if (s && navigator?.clipboard) {
      navigator.clipboard.writeText(s).then(() => {
        this.secretCopied.set(true);
        setTimeout(() => this.secretCopied.set(false), 2500);
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
        this.dlqFailures.set(list && list.length > 0 ? list : INITIAL_SHOWCASE_DLQ);
        this.isLoading.set(false);
      },
      error: () => {
        this.dlqFailures.set(INITIAL_SHOWCASE_DLQ);
        this.isLoading.set(false);
      }
    });
  }

  retryFailure(failure: WebhookDeliveryFailure): void {
    this.retryingMap.update((m) => ({ ...m, [failure.id]: true }));
    this.clearAlerts();

    if (this.authService.isAuthenticated() && !this.authService.isDemoSession()) {
      this.enterpriseService.retryDlqFailure(failure.id).subscribe({
        next: () => {
          this.retryingMap.update((m) => ({ ...m, [failure.id]: false }));
          this.successMessage.set(`Reintento de entrega ejecutado con exito.`);
          this.loadDlqFailures();
        },
        error: (err) => {
          this.retryingMap.update((m) => ({ ...m, [failure.id]: false }));
          const msg = err.error?.messages?.[0] || 'Fallo al reintentar la entrega del webhook.';
          this.errorMessage.set(msg);
        }
      });
    } else {
      setTimeout(() => {
        this.retryingMap.update((m) => ({ ...m, [failure.id]: false }));
        this.dlqFailures.update((list) =>
          list.map((f) => (f.id === failure.id ? { ...f, status: 'RETRIED_OK', retriedAt: new Date().toISOString() } : f))
        );
        this.successMessage.set(`Reintento exitoso simulado en Dead Letter Queue.`);
      }, 500);
    }
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
        this.isLoading.set(false);
      }
    });
  }

  saveChaosState(): void {
    this.isSavingChaos.set(true);
    this.clearAlerts();

    if (this.authService.isAuthenticated() && !this.authService.isDemoSession()) {
      this.enterpriseService.updateChaosState(this.chaosState()).subscribe({
        next: (updated) => {
          this.chaosState.set(updated);
          this.isSavingChaos.set(false);
          this.successMessage.set('Parametros de simulacion de caos actualizados.');
        },
        error: () => {
          this.isSavingChaos.set(false);
          this.errorMessage.set('Error al guardar parametros de caos.');
        }
      });
    } else {
      setTimeout(() => {
        this.isSavingChaos.set(false);
        this.successMessage.set('Parametros de caos aplicados en sesion interactiva.');
      }, 350);
    }
  }

  triggerSimulatedEvent(): void {
    this.isTriggeringChaos.set(true);
    this.clearAlerts();

    if (this.authService.isAuthenticated() && !this.authService.isDemoSession()) {
      this.enterpriseService.triggerChaosEvent().subscribe({
        next: (res) => {
          this.isTriggeringChaos.set(false);
          this.successMessage.set(`Evento simulado emitido [ID: ${res.eventId.substring(0, 8)}].`);
        },
        error: () => {
          this.isTriggeringChaos.set(false);
          this.errorMessage.set('Error al emitir el evento simulado.');
        }
      });
    } else {
      setTimeout(() => {
        this.isTriggeringChaos.set(false);
        const dummyEvtId = Math.random().toString(36).substring(2, 10);
        this.successMessage.set(`Incidente simulado disparado [ID: ${dummyEvtId}] via Transactional Outbox.`);
      }, 450);
    }
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
        this.isLoading.set(false);
      }
    });
  }
}
