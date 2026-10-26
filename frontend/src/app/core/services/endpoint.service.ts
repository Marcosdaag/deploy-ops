import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiErrorResponse,
  EndpointCreateRequest,
  EndpointResponse
} from '../models/endpoint.model';

/*
 * Servicio encargado de hablar con nuestra API publica en Render.
 * Centraliza las peticiones GET, POST y DELETE, y traduce los errores del
 * GlobalExceptionHandler para mostrarlos limpios en pantalla.
 */
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
