CREATE TABLE chozy.report_usage_entries (
    report_id bigint PRIMARY KEY CHECK (report_id > 0),
    member_id bigint NOT NULL REFERENCES chozy.members(id),
    period_start date NOT NULL,
    completed_at timestamptz NOT NULL,
    data_lab_quota_exhausted boolean NOT NULL,
    CONSTRAINT ck_report_usage_month CHECK (
        period_start = date_trunc('month', completed_at AT TIME ZONE 'Asia/Seoul')::date
    )
);
CREATE INDEX ix_report_usage_entries_charged
    ON chozy.report_usage_entries(member_id, period_start)
    WHERE NOT data_lab_quota_exhausted;

CREATE TABLE chozy.member_screening_runs (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id bigint NOT NULL REFERENCES chozy.members(id),
    executed_at timestamptz NOT NULL
);
CREATE INDEX ix_member_screening_runs_period
    ON chozy.member_screening_runs(member_id, executed_at);

CREATE TABLE chozy.member_favorites (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id bigint NOT NULL REFERENCES chozy.members(id),
    status varchar(10) NOT NULL CHECK (status IN ('INTERESTED', 'SELLING'))
);
CREATE INDEX ix_member_favorites_member ON chozy.member_favorites(member_id);
