import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuditApi, AuditFilters } from '../../core/api/audit.api';
import { AuditEvent } from '../../core/models/audit.model';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.scss',
})
export class AuditComponent implements OnInit {
  events: AuditEvent[] = [];
  loading = true;
  error = '';

  filtros: AuditFilters = {};

  constructor(private auditApi: AuditApi) {}

  ngOnInit(): void {
    this.buscar();
  }

  buscar(): void {
    this.loading = true;
    this.error = '';
    this.auditApi.list(this.filtros).subscribe({
      next: (events) => {
        this.events = events;
        this.loading = false;
      },
      error: () => {
        this.error = 'No se pudo cargar la auditoria.';
        this.loading = false;
      },
    });
  }

  limpiarFiltros(): void {
    this.filtros = {};
    this.buscar();
  }
}
