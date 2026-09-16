import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { KpiSummary } from '../models/report.model';

@Injectable({ providedIn: 'root' })
export class ReportApi {
  private readonly baseUrl = environment.api.report;

  constructor(private http: HttpClient) {}

  kpis(): Observable<KpiSummary> {
    return this.http.get<KpiSummary>(`${this.baseUrl}/kpis`);
  }
}
