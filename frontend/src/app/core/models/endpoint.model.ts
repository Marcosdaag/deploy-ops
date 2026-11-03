export type ServiceStatus = 'PENDING' | 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'RECOVERING';
export type IncidentStatus = 'ONGOING' | 'RESOLVED';

export interface IncidentResponse {
  id: number;
  serviceId: number;
  status: IncidentStatus;
  startedAt: string;
  resolvedAt: string | null;
  durationSeconds: number;
}

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
  isProtected?: boolean;
  isOwner?: boolean;
  ownerEmail?: string | null;
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
