import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EndpointService } from '../../core/services/endpoint.service';
import { StatusPageData } from '../../core/models/endpoint.model';

@Component({
  selector: 'app-status-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './status-page.component.html',
  styleUrls: ['./status-page.component.css']
})
export class StatusPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly endpointService = inject(EndpointService);

  readonly data = signal<StatusPageData | null>(null);
  readonly isLoading = signal<boolean>(true);
  readonly errorMessage = signal<string | null>(null);
  readonly slug = signal<string>('');

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const slugParam = params.get('slug') || 'demo';
      this.slug.set(slugParam);
      this.fetchStatusPage(slugParam);
    });
  }

  fetchStatusPage(slug: string): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.endpointService.getStatusPage(slug).subscribe({
      next: (res) => {
        this.data.set(res);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.isLoading.set(false);
        const msgs = this.endpointService.extractErrorMessages(err);
        this.errorMessage.set(msgs[0] || 'No se pudo cargar la pagina de estado publica.');
      }
    });
  }

  getOverallStatusText(status: string): string {
    switch (status) {
      case 'OPERATIONAL':
        return 'Todos los sistemas funcionando con normalidad';
      case 'DEGRADED':
        return 'Rendimiento degradado en algunos servicios';
      case 'MAJOR_OUTAGE':
        return 'Interrupcion critica de servicio detectada';
      default:
        return 'Monitoreo activo';
    }
  }

  getServiceStatusBadge(status: string): { label: string; css: string } {
    switch (status) {
      case 'HEALTHY':
        return { label: 'Operacional', css: 'badge-healthy' };
      case 'DEGRADED':
        return { label: 'Degradado', css: 'badge-degraded' };
      case 'DOWN':
        return { label: 'Caido', css: 'badge-down' };
      case 'RECOVERING':
        return { label: 'Recuperando', css: 'badge-recovering' };
      default:
        return { label: 'Pendiente', css: 'badge-pending' };
    }
  }

  formatDuration(seconds: number | null): string {
    if (!seconds || seconds <= 0 || seconds < 60) return 'Menos de 1 min';
    const mins = Math.floor(seconds / 60);
    if (mins < 60) return `${mins} min`;
    const hrs = Math.floor(mins / 60);
    const remMins = mins % 60;
    return `${hrs}h ${remMins}m`;
  }
}
