export type ServiceStatus = 'PENDING' | 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'RECOVERING';

/*
 * Contratos de datos que espejan exactamente los DTOs de nuestro backend en Spring Boot.
 * Cualquier cambio en EndpointResponseDTO o ErrorResponseDTO se refleja aca.
 */
export interface EndpointResponse {
  id: number;
  name: string;
  url: string;
  status: ServiceStatus;
  consecutiveFailures: number;
  lastResponseTimeMs: number | null;
  lastCheckedAt: string | null;
  createdAt: string;
  uptimePercentage: number;
}

export interface EndpointCreateRequest {
  name: string;
  url: string;
}

export interface ApiErrorResponse {
  status: number;
  error: string;
  messages: string[];
  timestamp: string;
}

export interface DashboardMetrics {
  total: number;
  healthy: number;
  degraded: number;
  down: number;
  pending: number;
  recovering: number;
  globalUptime: number;
  averageLatencyMs: number | null;
}
