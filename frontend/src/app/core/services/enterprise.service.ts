import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ChaosState,
  DistributedOverview,
  WebhookCreateRequest,
  WebhookDeliveryFailure,
  WebhookEndpoint,
  WebhookUpdateRequest
} from '../models/enterprise.model';

/**
 * Servicio Angular para el Centro de Control Enterprise.
 * Provee metodos reactivos para la administracion de webhooks criptograficos,
 * inspeccion y reintento de mensajes en Dead Letter Queue (DLQ),
 * telemetria distribuida del cluster y simulacion de contingencia SRE (Chaos).
 */
@Injectable({
  providedIn: 'root'
})
export class EnterpriseService {
  private readonly http = inject(HttpClient);
  readonly baseApiUrl = environment.apiUrl.replace(/\/endpoints$/, '');

  /**
   * Obtiene la lista de receptores de webhook configurados para el usuario actual.
   */
  getWebhooks(): Observable<WebhookEndpoint[]> {
    return this.http.get<WebhookEndpoint[]>(`${this.baseApiUrl}/webhooks`);
  }

  /**
   * Registra un nuevo webhook externo con generacion de secreto HMAC.
   */
  createWebhook(payload: WebhookCreateRequest): Observable<WebhookEndpoint> {
    return this.http.post<WebhookEndpoint>(`${this.baseApiUrl}/webhooks`, payload);
  }

  /**
   * Actualiza los parametros de un webhook existente (nombre, URL o estado habilitado).
   */
  updateWebhook(id: string, payload: WebhookUpdateRequest): Observable<WebhookEndpoint> {
    return this.http.put<WebhookEndpoint>(`${this.baseApiUrl}/webhooks/${id}`, payload);
  }

  /**
   * Elimina de forma permanente un receptor de webhook.
   */
  deleteWebhook(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseApiUrl}/webhooks/${id}`);
  }

  /**
   * Ejecuta un envio de prueba de evento WEBHOOK_TEST_PING firmado criptograficamente.
   */
  pingWebhook(id: string): Observable<{ message: string; timestamp: string }> {
    return this.http.post<{ message: string; timestamp: string }>(
      `${this.baseApiUrl}/webhooks/${id}/ping`,
      {}
    );
  }

  /**
   * Rota inmediatamente el secreto de firma HMAC-SHA256 del webhook.
   */
  rotateSecret(id: string): Observable<WebhookEndpoint> {
    return this.http.post<WebhookEndpoint>(
      `${this.baseApiUrl}/webhooks/${id}/rotate-secret`,
      {}
    );
  }

  /**
   * Obtiene la bitacora de entregas fallidas derivadas a la Dead Letter Queue.
   */
  getDlqFailures(): Observable<WebhookDeliveryFailure[]> {
    return this.http.get<WebhookDeliveryFailure[]>(`${this.baseApiUrl}/webhooks/failures`);
  }

  /**
   * Reintenta manualmente la entrega de un mensaje encolado en DLQ.
   */
  retryDlqFailure(failureId: string): Observable<{ message: string; status: string }> {
    return this.http.post<{ message: string; status: string }>(
      `${this.baseApiUrl}/webhooks/failures/${failureId}/retry`,
      {}
    );
  }

  /**
   * Consulta los indicadores KPI de telemetria distribuida del cluster.
   */
  getDistributedOverview(): Observable<DistributedOverview> {
    return this.http.get<DistributedOverview>(`${this.baseApiUrl}/distributed/overview`);
  }

  /**
   * Obtiene el estado actual del simulador de contingencia y caos SRE.
   */
  getChaosState(): Observable<ChaosState> {
    return this.http.get<ChaosState>(`${this.baseApiUrl}/chaos/state`);
  }

  /**
   * Conmuta el codigo HTTP o latencia sintetica simulada por el sandbox.
   */
  updateChaosState(state: ChaosState): Observable<ChaosState> {
    return this.http.put<ChaosState>(`${this.baseApiUrl}/chaos/state`, state);
  }

  /**
   * Dispara un evento de caos manual para verificar el flujo de entrega y desvio a DLQ.
   */
  triggerChaosEvent(): Observable<{ message: string; eventId: string }> {
    return this.http.post<{ message: string; eventId: string }>(
      `${this.baseApiUrl}/chaos/trigger-event`,
      {}
    );
  }
}
