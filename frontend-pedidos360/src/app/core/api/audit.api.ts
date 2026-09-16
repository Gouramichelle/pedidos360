import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuditEvent } from '../models/audit.model';

export interface AuditFilters {
  entityId?: number;
  actor?: string;
  from?: string;
  to?: string;
}

@Injectable({ providedIn: 'root' })
export class AuditApi {
  private readonly baseUrl = `${environment.api.audit}/events`;

  constructor(private http: HttpClient) {}

  list(filters: AuditFilters = {}): Observable<AuditEvent[]> {
    let params = new HttpParams();
    if (filters.entityId) params = params.set('entityId', filters.entityId);
    if (filters.actor) params = params.set('actor', filters.actor);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    return this.http.get<AuditEvent[]>(this.baseUrl, { params });
  }
}
