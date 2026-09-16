export interface SalesByHourPoint {
  hour: string;
  total: number;
}

export interface ActiveStatusCount {
  status: string;
  count: number;
}

export interface KpiSummary {
  salesByHour: SalesByHourPoint[];
  averageLeadTimeSeconds: number | null;
  activeStatusCounts: ActiveStatusCount[];
  statusBreakdown: Record<string, number>;
}
