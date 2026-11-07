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

@Injectable({
  providedIn: 'root'
})
export class EnterpriseService {
  private readonly http = inject(HttpClient);
  readonly baseApiUrl = environment.apiUrl.replace(/\/endpoints$/, '');

  getWebhooks(): Observable<WebhookEndpoint[]> {
    return this.http.get<WebhookEndpoint[]>(`${this.baseApiUrl}/webhooks`);
  }

  createWebhook(payload: WebhookCreateRequest): Observable<WebhookEndpoint> {
    return this.http.post<WebhookEndpoint>(`${this.baseApiUrl}/webhooks`, payload);
  }

  updateWebhook(id: string, payload: WebhookUpdateRequest): Observable<WebhookEndpoint> {
    return this.http.put<WebhookEndpoint>(`${this.baseApiUrl}/webhooks/${id}`, payload);
  }

  deleteWebhook(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseApiUrl}/webhooks/${id}`);
  }

  pingWebhook(id: string): Observable<{ message: string; timestamp: string }> {
    return this.http.post<{ message: string; timestamp: string }>(
      `${this.baseApiUrl}/webhooks/${id}/ping`,
      {}
    );
  }

  rotateSecret(id: string): Observable<WebhookEndpoint> {
    return this.http.post<WebhookEndpoint>(
      `${this.baseApiUrl}/webhooks/${id}/rotate-secret`,
      {}
    );
  }

  getDlqFailures(): Observable<WebhookDeliveryFailure[]> {
    return this.http.get<WebhookDeliveryFailure[]>(`${this.baseApiUrl}/webhooks/failures`);
  }

  retryDlqFailure(failureId: string): Observable<{ message: string; status: string }> {
    return this.http.post<{ message: string; status: string }>(
      `${this.baseApiUrl}/webhooks/failures/${failureId}/retry`,
      {}
    );
  }

  getDistributedOverview(): Observable<DistributedOverview> {
    return this.http.get<DistributedOverview>(`${this.baseApiUrl}/distributed/overview`);
  }

  getChaosState(): Observable<ChaosState> {
    return this.http.get<ChaosState>(`${this.baseApiUrl}/chaos/state`);
  }

  updateChaosState(state: ChaosState): Observable<ChaosState> {
    return this.http.put<ChaosState>(`${this.baseApiUrl}/chaos/state`, state);
  }

  triggerChaosEvent(): Observable<{ message: string; eventId: string }> {
    return this.http.post<{ message: string; eventId: string }>(
      `${this.baseApiUrl}/chaos/trigger-event`,
      {}
    );
  }
}
