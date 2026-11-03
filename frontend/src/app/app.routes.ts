import { Routes } from '@angular/router';
import { DashboardComponent } from './features/dashboard/dashboard.component';

export const routes: Routes = [
  {
    path: '',
    component: DashboardComponent
  },
  {
    path: 'status/:slug',
    loadComponent: () =>
      import('./features/status-page/status-page.component').then((m) => m.StatusPageComponent)
  },
  {
    path: '**',
    redirectTo: ''
  }
];
