DROP INDEX idx_trace_start_time;

CREATE INDEX idx_trace_start_time ON traces (start_time DESC, id DESC);
CREATE INDEX idx_trace_service_start_time ON traces (lower(service_name), start_time DESC, id DESC);
CREATE INDEX idx_trace_status_start_time ON traces (lower(status_code), start_time DESC, id DESC);
CREATE INDEX idx_trace_method_start_time ON traces (lower(http_method), start_time DESC, id DESC);
CREATE INDEX idx_trace_duration_start_time ON traces (duration_ms, start_time DESC, id DESC);
