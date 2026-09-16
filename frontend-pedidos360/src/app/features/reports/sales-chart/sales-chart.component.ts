import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SalesByHourPoint } from '../../../core/models/report.model';

/**
 * Barras CSS simples en vez de una libreria de charting: evita agregar una
 * dependencia pesada solo para un MVP con pocos puntos de datos.
 */
@Component({
  selector: 'app-sales-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sales-chart.component.html',
  styleUrl: './sales-chart.component.scss',
})
export class SalesChartComponent {
  @Input() points: SalesByHourPoint[] = [];

  get max(): number {
    return Math.max(1, ...this.points.map((p) => p.total));
  }

  heightPercent(total: number): number {
    return (total / this.max) * 100;
  }
}
