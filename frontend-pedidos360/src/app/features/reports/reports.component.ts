import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReportApi } from '../../core/api/report.api';
import { KpiSummary } from '../../core/models/report.model';
import { SalesChartComponent } from './sales-chart/sales-chart.component';
import { LeadTimeChartComponent } from './lead-time-chart/lead-time-chart.component';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, SalesChartComponent, LeadTimeChartComponent],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.scss',
})
export class ReportsComponent implements OnInit {
  kpis: KpiSummary | null = null;
  loading = true;
  error = '';

  constructor(private reportApi: ReportApi) {}

  ngOnInit(): void {
    this.reportApi.kpis().subscribe({
      next: (kpis) => {
        this.kpis = kpis;
        this.loading = false;
      },
      error: () => {
        this.error = 'No se pudieron cargar los KPIs.';
        this.loading = false;
      },
    });
  }
}
