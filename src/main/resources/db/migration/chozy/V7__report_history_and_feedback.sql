CREATE INDEX ix_manual_analysis_member_recent
  ON chozy.manual_analysis_jobs (member_id, (COALESCE(completed_at, requested_at)) DESC, id DESC);

CREATE TABLE chozy.report_feedback (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  report_id bigint NOT NULL UNIQUE REFERENCES chozy.manual_analysis_reports(id),
  product_id text,
  score numeric NOT NULL CHECK (score BETWEEN 0 AND 100),
  supply_condition_score numeric CHECK (supply_condition_score BETWEEN 0 AND 100),
  market_condition_score numeric CHECK (market_condition_score BETWEEN 0 AND 100),
  feedback varchar(11) NOT NULL CHECK (feedback IN ('APPROPRIATE', 'TOO_HIGH', 'TOO_LOW')),
  created_at timestamptz NOT NULL
);
