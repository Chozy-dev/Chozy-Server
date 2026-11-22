CREATE TABLE chozy.manual_analysis_jobs (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  member_id bigint NOT NULL REFERENCES chozy.members(id),
  product_name varchar(500) NOT NULL,
  wholesale_price numeric NOT NULL CHECK (wholesale_price > 0),
  shipping_fee numeric NOT NULL CHECK (shipping_fee >= 0),
  has_price_policy boolean NOT NULL,
  minimum_selling_price numeric,
  keyword varchar(500),
  requested_at timestamptz NOT NULL,
  status varchar(10) NOT NULL DEFAULT 'PENDING',
  lease_until timestamptz,
  completed_at timestamptz,
  error_message text,
  CHECK (
    (has_price_policy AND minimum_selling_price IS NOT NULL AND minimum_selling_price > 0)
    OR (NOT has_price_policy AND minimum_selling_price IS NULL)
  ),
  CHECK (
    (status = 'PENDING' AND lease_until IS NULL AND completed_at IS NULL AND error_message IS NULL)
    OR (status = 'PROCESSING' AND lease_until IS NOT NULL AND completed_at IS NULL AND error_message IS NULL)
    OR (status = 'COMPLETED' AND lease_until IS NULL AND completed_at IS NOT NULL AND error_message IS NULL)
    OR (status = 'FAILED' AND lease_until IS NULL AND completed_at IS NOT NULL AND error_message IS NOT NULL)
  )
);
CREATE INDEX ix_manual_analysis_pending ON chozy.manual_analysis_jobs (id) WHERE status = 'PENDING';
CREATE INDEX ix_manual_analysis_lease ON chozy.manual_analysis_jobs (lease_until) WHERE status = 'PROCESSING';
CREATE INDEX ix_manual_analysis_member_active ON chozy.manual_analysis_jobs (member_id)
  WHERE status IN ('PENDING', 'PROCESSING');

CREATE TABLE chozy.manual_analysis_reports (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  analysis_id bigint NOT NULL UNIQUE REFERENCES chozy.manual_analysis_jobs(id),
  snapshot jsonb NOT NULL CHECK (jsonb_typeof(snapshot) = 'object')
);

-- V5 permits usage entries without a report row. Advance the independent identity
-- so preexisting usage entries cannot collide with the first generated report IDs.
SELECT setval(pg_get_serial_sequence('chozy.manual_analysis_reports', 'id'),
              COALESCE((SELECT max(report_id) FROM chozy.report_usage_entries), 0) + 1, false);
