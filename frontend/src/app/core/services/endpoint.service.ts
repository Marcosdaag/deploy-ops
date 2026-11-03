import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiErrorResponse,
  EndpointCreateRequest,
  EndpointResponse,
  IncidentResponse
} from '../models/endpoint.model';

@Injectable({
  providedIn: 'root'
})
export class EndpointService {
  private readonly http = inject(HttpClient);
  readonly publicApiUrl = environment.apiUrl;

  getEndpoints(): Observable<EndpointResponse[]> {
    return this.http.get<EndpointResponse[]>(this.publicApiUrl);
  }

  createEndpoint(payload: EndpointCreateRequest): Observable<EndpointResponse> {
    return this.http.post<EndpointResponse>(this.publicApiUrl, payload);
  }

  deleteEndpoint(id: number): Observable<void> {
    return this.http.delete<void>(`${this.publicApiUrl}/${id}`);
  }

  getIncidents(serviceId: number): Observable<IncidentResponse[]> {
    return this.http.get<IncidentResponse[]>(`${this.publicApiUrl}/${serviceId}/incidents`);
  }

  triggerPing(serviceId: number): Observable<EndpointResponse> {
    return this.http.post<EndpointResponse>(`${this.publicApiUrl}/${serviceId}/ping`, {});
  }

  getDashboardStream(): Observable<EndpointResponse[]> {
    return new Observable<EndpointResponse[]>((observer) => {
      if (typeof window === 'undefined' || typeof EventSource === 'undefined') {
        return;
      }

      const streamUrl = `${this.publicApiUrl}/stream`;
      const eventSource = new EventSource(streamUrl);

      eventSource.addEventListener('DASHBOARD_UPDATE', (event: MessageEvent) => {
        try {
          const data = JSON.parse(event.data) as EndpointResponse[];
          observer.next(data);
        } catch (e) {
          console.warn('Error al deserializar evento SSE', e);
        }
      });

      eventSource.onerror = () => {
        // EventSource reconecta automaticamente
      };

      return () => {
        eventSource.close();
      };
    });
  }

  extractErrorMessages(error: unknown): string[] {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        return [
          'Conectando con el servidor en Render. Si estuvo inactivo, puede tardar unos segundos en levantar.'
        ];
      }

      const body = error.error as Partial<ApiErrorResponse> | null;
      if (body && Array.isArray(body.messages) && body.messages.length > 0) {
        return body.messages;
      }

      if (typeof error.error === 'string' && error.error.trim().length > 0) {
        return [error.error];
      }

      return [`Error HTTP ${error.status}: ${error.statusText || 'Solicitud rechazada'}`];
    }

    return ['Ocurrio un problema inesperado al comunicarse con el servidor.'];
  }
}
