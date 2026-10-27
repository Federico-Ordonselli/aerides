/** One page of results, as returned by the API (backend PageResponse). */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageRequest {
  page: number;
  size: number;
  /** e.g. "startsAt,asc" */
  sort?: string;
}
