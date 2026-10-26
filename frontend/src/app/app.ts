import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';

type ApiStatus = 'checking' | 'up' | 'down';

@Component({
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly apiStatus = signal<ApiStatus>('checking');

  constructor() {
    inject(HttpClient)
      .get<{ status: string }>('/actuator/health')
      .subscribe({
        next: (health) => this.apiStatus.set(health.status === 'UP' ? 'up' : 'down'),
        error: () => this.apiStatus.set('down'),
      });
  }
}
