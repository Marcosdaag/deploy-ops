import { Component, OnInit, OnDestroy, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormControl, Validators } from '@angular/forms';
import { EndpointService } from './core/services/endpoint';
import { EndpointResponse } from './core/models/endpoint.interface';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit, OnDestroy {
  private endpointService = inject(EndpointService);

  // Signals de estado
  services = signal<EndpointResponse[]>([]);
  loading = signal<boolean>(true);
  submitting = signal<boolean>(false);
  error = signal<string | null>(null);
  successMessage = signal<string | null>(null);
  isModalOpen = signal<boolean>(false);
  lastUpdated = signal<Date>(new Date());
  serviceToDelete = signal<EndpointResponse | null>(null);

  // Signals computados para KPIs y métricas
  totalServices = computed(() => this.services().length);
  upServices = computed(() => this.services().filter(s => s.status === 'UP').length);
  downServices = computed(() => this.services().filter(s => s.status === 'DOWN').length);
  
  avgUptime = computed(() => {
    const list = this.services();
    if (list.length === 0) return 100;
    const sum = list.reduce((acc, curr) => acc + curr.uptimePercentage, 0);
    return Math.round((sum / list.length) * 100) / 100;
  });

  // Formulario reactivo para crear nuevos endpoints
  endpointForm = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(2), Validators.maxLength(50)]
    }),
    url: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.pattern('^https?:\\/\\/.+')
      ]
    })
  });

  private pollingTimer: any = null;

  ngOnInit(): void {
    this.loadEndpoints();

    // Polling ligero cada 20 segundos para refrescar automáticamente el estado
    this.pollingTimer = setInterval(() => {
      this.loadEndpoints(true);
    }, 20000);
  }

  ngOnDestroy(): void {
    if (this.pollingTimer) {
      clearInterval(this.pollingTimer);
    }
  }

  // Carga o refresca los endpoints desde la API Spring Boot
  loadEndpoints(isBackground: boolean = false): void {
    if (!isBackground) {
      this.loading.set(true);
    }
    this.error.set(null);

    this.endpointService.getEndpoints().subscribe({
      next: (data) => {
        this.services.set(data);
        this.loading.set(false);
        this.lastUpdated.set(new Date());
      },
      error: (err) => {
        console.error('Error al consultar la API de DeployOps:', err);
        this.loading.set(false);
        this.error.set(
          'No se pudo conectar con el backend (http://localhost:8080). Verificá que la aplicación Spring Boot esté en ejecución.'
        );
      }
    });
  }

  // Apertura y cierre de modal
  openAddModal(): void {
    this.endpointForm.reset();
    this.isModalOpen.set(true);
  }

  closeAddModal(): void {
    this.isModalOpen.set(false);
    this.endpointForm.reset();
  }

  // Crear un nuevo endpoint
  onSubmit(): void {
    if (this.endpointForm.invalid) {
      this.endpointForm.markAllAsTouched();
      return;
    }

    const { name, url } = this.endpointForm.getRawValue();
    this.submitting.set(true);

    this.endpointService.createEndpoint(name, url).subscribe({
      next: () => {
        this.submitting.set(false);
        this.closeAddModal();
        this.showToast(`Servicio "${name}" registrado exitosamente.`);
        this.loadEndpoints();
      },
      error: (err) => {
        this.submitting.set(false);
        console.error('Error al registrar endpoint:', err);
        alert('Error al registrar el servicio. Verificá que la URL sea válida.');
      }
    });
  }

  // Confirmación de eliminación
  confirmDelete(service: EndpointResponse): void {
    this.serviceToDelete.set(service);
  }

  cancelDelete(): void {
    this.serviceToDelete.set(null);
  }

  executeDelete(): void {
    const service = this.serviceToDelete();
    if (!service) return;

    this.endpointService.deleteEndpoint(service.id).subscribe({
      next: () => {
        this.showToast(`Servicio "${service.name}" eliminado correctamente.`);
        this.serviceToDelete.set(null);
        this.loadEndpoints();
      },
      error: (err) => {
        console.error('Error al eliminar endpoint:', err);
        alert('No se pudo eliminar el servicio.');
        this.serviceToDelete.set(null);
      }
    });
  }

  // Notificación tipo toast
  private showToast(msg: string): void {
    this.successMessage.set(msg);
    setTimeout(() => {
      this.successMessage.set(null);
    }, 4000);
  }

  // Helper para saber la clase de color según el porcentaje de Uptime
  getUptimeClass(percentage: number): string {
    if (percentage >= 99.0) return 'sla-excellent';
    if (percentage >= 95.0) return 'sla-good';
    return 'sla-critical';
  }
}
