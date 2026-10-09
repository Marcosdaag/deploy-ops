import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EndpointResponse } from '../models/endpoint.interface';

// Con el injectable y root definimos que este servicio va a ser un singleton
@Injectable({
  providedIn: 'root'
})
export class EndpointService {

  private http = inject(HttpClient);

  // Traemos la URL de las varaibles de entorno (http://localhost:8080/api) y concatenamos /endpoints
  private apiUrl = environment.apiUrl + '/endpoints';

  // Hacemos la peticion en la que la API devuelve una lista de SERVICIOS y tambien usamos una interface para cumplir con los datos necesarios
  getEndpoints(): Observable<EndpointResponse[]> {
    return this.http.get<EndpointResponse[]>(this.apiUrl);
  }

  // Funcion para poder crear nuevos SERVICIOS
  createEndpoint(name: string, url: string): Observable<EndpointResponse> {
    const body = { name, url };
    return this.http.post<EndpointResponse>(this.apiUrl, body);
  }

  // Funcion para poder eliminar SERVICIOS
  deleteEndpoint(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
