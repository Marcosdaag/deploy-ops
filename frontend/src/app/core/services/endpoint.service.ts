import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiErrorResponse,
  EndpointCreateRequest,
  EndpointUpdateRequest,
  EndpointResponse,
  IncidentResponse,
  MonthlyReport,
  ServiceMetricsHistory,
  StatusPageData
} from '../models/endpoint.model';

/**
 * Servicio Angular para la gestion de endpoints, telemetria SRE, incidentes y Status Pages.
 * Encapsula las peticiones HTTP hacia el backend de Spring Boot, proveyendo metodos tipados
 * para la recuperacion de series temporales, exportacion de reportes SLA y canal SSE.
 */
@Injectable({
  providedIn: 'root'
})
export class EndpointService {
  private readonly http = inject(HttpClient);
  readonly publicApiUrl = environment.apiUrl;

  /**
   * Obtiene la lista de endpoints monitoreados segun el alcance ('mine' para propios, 'showcase' para publicos).
   */
  getEndpoints(scope?: string): Observable<EndpointResponse[]> {
    const query = scope ? `?scope=${scope}` : '';
    return this.http.get<EndpointResponse[]>(`${this.publicApiUrl}${query}`);
  }

  /**
   * Siembra un conjunto de endpoints de ejemplo en la cuenta del usuario autenticado.
   */
  seedSampleEndpoints(): Observable<EndpointResponse[]> {
    return this.http.post<EndpointResponse[]>(`${this.publicApiUrl}/samples`, {});
  }

  /**
   * Registra un nuevo servicio web a ser monitoreado.
   */
  createEndpoint(payload: EndpointCreateRequest): Observable<EndpointResponse> {
    return this.http.post<EndpointResponse>(this.publicApiUrl, payload);
  }

  /**
   * Actualiza la configuracion de un endpoint existente (URL, cabeceras, codigo esperado).
   */
  updateEndpoint(id: number, payload: EndpointUpdateRequest): Observable<EndpointResponse> {
    return this.http.put<EndpointResponse>(`${this.publicApiUrl}/${id}`, payload);
  }

  /**
   * Elimina un endpoint y su historial asociado de incidentes y pings.
   */
  deleteEndpoint(id: number): Observable<void> {
    return this.http.delete<void>(`${this.publicApiUrl}/${id}`);
  }

  /**
   * Obtiene el listado de incidentes historicos y activos de un servicio.
   */
  getIncidents(serviceId: number): Observable<IncidentResponse[]> {
    return this.http.get<IncidentResponse[]>(`${this.publicApiUrl}/${serviceId}/incidents`);
  }

  /**
   * Consulta las metricas de latencia y disponibilidad para un rango temporal ('6h', '24h', '7d', '30d').
   */
  getMetricsHistory(serviceId: number, range: string = '24h'): Observable<ServiceMetricsHistory> {
    return this.http.get<ServiceMetricsHistory>(`${this.publicApiUrl}/${serviceId}/metrics?range=${range}`);
  }

  /**
   * Conmuta la visibilidad publica de un servicio para su inclusion en la Status Page.
   */
  toggleVisibility(serviceId: number, isPublic: boolean): Observable<void> {
    return this.http.patch<void>(`${this.publicApiUrl}/${serviceId}/visibility`, { isPublic });
  }

  /**
   * Obtiene los datos consolidados de una Status Page publica mediante su slug.
   */
  getStatusPage(slug: string): Observable<StatusPageData> {
    const baseUrl = this.publicApiUrl.replace(/\/endpoints$/, '/status');
    return this.http.get<StatusPageData>(`${baseUrl}/${slug}`);
  }

  updateUserSlug(newSlug: string): Observable<{ slug: string }> {
    const baseUrl = this.publicApiUrl.replace(/\/endpoints$/, '/status');
    return this.http.patch<{ slug: string }>(`${baseUrl}/slug`, { slug: newSlug });
  }

  triggerPing(serviceId: number): Observable<EndpointResponse> {
    return this.http.post<EndpointResponse>(`${this.publicApiUrl}/${serviceId}/ping`, {});
  }

  triggerTestAlert(serviceId: number): Observable<void> {
    return this.http.post<void>(`${this.publicApiUrl}/${serviceId}/test-alert`, {});
  }

  getMonthlyReport(serviceId: number, year?: number, month?: number): Observable<MonthlyReport> {
    const params: string[] = [];
    if (year) params.push(`year=${year}`);
    if (month) params.push(`month=${month}`);
    const query = params.length > 0 ? `?${params.join('&')}` : '';
    return this.http.get<MonthlyReport>(`${this.publicApiUrl}/${serviceId}/report${query}`);
  }

  downloadMonthlyReportCsv(serviceId: number, year?: number, month?: number): Observable<Blob> {
    const params: string[] = [];
    if (year) params.push(`year=${year}`);
    if (month) params.push(`month=${month}`);
    const query = params.length > 0 ? `?${params.join('&')}` : '';
    return this.http.get(`${this.publicApiUrl}/${serviceId}/report/csv${query}`, {
      responseType: 'blob'
    });
  }

  sendMonthlyReportEmail(serviceId: number, year?: number, month?: number): Observable<void> {
    const params: string[] = [];
    if (year) params.push(`year=${year}`);
    if (month) params.push(`month=${month}`);
    const query = params.length > 0 ? `?${params.join('&')}` : '';
    return this.http.post<void>(`${this.publicApiUrl}/${serviceId}/report/email${query}`, {});
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
