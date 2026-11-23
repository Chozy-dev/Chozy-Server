CREATE TABLE chozy.screening_results (
  public_id uuid PRIMARY KEY,
  member_run_id bigint UNIQUE REFERENCES chozy.member_screening_runs(id),
  analyzed_at timestamptz NOT NULL,
  trend_end_date date NOT NULL,
  expires_at timestamptz,
  keywords jsonb NOT NULL CHECK (jsonb_typeof(keywords) = 'array'),
  CHECK ((member_run_id IS NULL) = (expires_at IS NOT NULL)),
  CHECK (expires_at IS NULL OR expires_at > analyzed_at)
);

CREATE INDEX ix_screening_results_guest_expiry
  ON chozy.screening_results(expires_at) WHERE member_run_id IS NULL;
