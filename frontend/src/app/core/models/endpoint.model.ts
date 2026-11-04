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
  isPublicStatus?: boolean;
  httpMethod?: string;
  expectedStatusCode?: number | null;
  customHeaders?: string | null;
  sslDaysRemaining?: number | null;
  sslExpiryDate?: string | null;
  sslIssuer?: string | null;
}

export interface EndpointCreateRequest {
  name: string;
  url: string;
  httpMethod?: string;
  expectedStatusCode?: number | null;
  customHeaders?: string | null;
  isPublicStatus?: boolean;
}

export interface EndpointUpdateRequest {
  name: string;
  url: string;
  httpMethod?: string;
  expectedStatusCode?: number | null;
  customHeaders?: string | null;
  isPublicStatus?: boolean;
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

export interface PingPoint {
  timestampMs: number;
  latencyMs: number;
  statusCode: number;
  alive: boolean;
}

export interface ServiceMetricsHistory {
  serviceId: number;
  serviceName: string;
  range: string;
  minLatencyMs: number;
  maxLatencyMs: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  uptimePercentage: number;
  totalPings: number;
  points: PingPoint[];
}

export interface PublicService {
  id: number;
  name: string;
  urlMasked: string;
  status: ServiceStatus;
  lastResponseTimeMs: number | null;
  lastCheckedAt: string | null;
  uptimePercentage: number;
}

export interface PublicIncident {
  id: number;
  serviceName: string;
  startedAt: string;
  resolvedAt: string | null;
  status: IncidentStatus;
  durationSeconds: number | null;
}

export interface StatusPageData {
  slug: string;
  ownerName: string;
  overallStatus: 'OPERATIONAL' | 'DEGRADED' | 'MAJOR_OUTAGE';
  overallUptime: number;
  services: PublicService[];
  recentIncidents: PublicIncident[];
}

export interface MonthlyReportIncident {
  id: number;
  serviceName: string;
  startedAt: string;
  resolvedAt: string | null;
  status: IncidentStatus;
  durationSeconds: number;
}

export interface MonthlyReport {
  serviceId: number;
  serviceName: string;
  serviceUrl: string;
  year: number;
  month: number;
  periodLabel: string;
  uptimePercentage: number;
  totalMonitoredSeconds: number;
  totalDowntimeSeconds: number;
  totalIncidents: number;
  mttrMinutes: number;
  minLatencyMs: number;
  maxLatencyMs: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  incidents: MonthlyReportIncident[];
}
