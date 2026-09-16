import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-lead-time-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './lead-time-chart.component.html',
  styleUrl: './lead-time-chart.component.scss',
})
export class LeadTimeChartComponent {
  @Input() averageSeconds: number | null = null;

  get minutos(): number | null {
    return this.averageSeconds !== null ? Math.round(this.averageSeconds / 60) : null;
  }
}
