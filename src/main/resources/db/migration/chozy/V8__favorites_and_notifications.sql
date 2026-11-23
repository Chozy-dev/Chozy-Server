ALTER TABLE chozy.member_favorites
  ADD COLUMN report_id bigint REFERENCES chozy.manual_analysis_reports(id),
  ADD COLUMN product_name text,
  ADD COLUMN keyword text,
  ADD COLUMN wholesale_price numeric,
  ADD COLUMN shipping_fee numeric,
  ADD COLUMN expected_selling_price numeric,
  ADD COLUMN profit_per_unit numeric,
  ADD COLUMN margin_rate numeric,
  ADD COLUMN saved_score numeric CHECK (saved_score BETWEEN 0 AND 100),
  ADD COLUMN saved_supply_score numeric CHECK (saved_supply_score BETWEEN 0 AND 100),
  ADD COLUMN saved_market_score numeric CHECK (saved_market_score BETWEEN 0 AND 100),
  ADD COLUMN created_at timestamptz,
  ADD COLUMN updated_at timestamptz,
  ADD COLUMN status_updated_at timestamptz,
  ADD COLUMN deleted_at timestamptz,
  ADD CONSTRAINT ck_member_favorite_snapshot CHECK (
    report_id IS NULL OR (product_name IS NOT NULL AND wholesale_price IS NOT NULL
      AND shipping_fee IS NOT NULL AND created_at IS NOT NULL AND updated_at IS NOT NULL
      AND status_updated_at IS NOT NULL)
  );

CREATE UNIQUE INDEX ux_member_favorite_active_report
  ON chozy.member_favorites(member_id, report_id)
  WHERE report_id IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE chozy.favorite_sales_results (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  favorite_id bigint NOT NULL REFERENCES chozy.member_favorites(id),
  result varchar(10) NOT NULL CHECK (result IN ('GOOD', 'AVERAGE', 'POOR', 'NOT_LISTED')),
  score numeric CHECK (score BETWEEN 0 AND 100),
  supply_score numeric CHECK (supply_score BETWEEN 0 AND 100),
  market_score numeric CHECK (market_score BETWEEN 0 AND 100),
  recorded_at timestamptz NOT NULL
);
CREATE INDEX ix_favorite_sales_results_latest
  ON chozy.favorite_sales_results(favorite_id, recorded_at DESC, id DESC);

CREATE TABLE chozy.member_notification_settings (
  member_id bigint PRIMARY KEY REFERENCES chozy.members(id),
  email_enabled boolean NOT NULL,
  supply_price_enabled boolean NOT NULL,
  score_change_enabled boolean NOT NULL,
  stock_out_enabled boolean NOT NULL,
  restock_enabled boolean NOT NULL,
  search_volume_enabled boolean NOT NULL,
  supply_price_change_percent integer NOT NULL CHECK (supply_price_change_percent BETWEEN 1 AND 100),
  score_change_points integer NOT NULL CHECK (score_change_points IN (5, 10, 15)),
  search_volume_increase_percent integer NOT NULL CHECK (search_volume_increase_percent IN (30, 50, 100)),
  updated_at timestamptz NOT NULL
);

CREATE TABLE chozy.member_notifications (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  member_id bigint NOT NULL REFERENCES chozy.members(id),
  type varchar(20) NOT NULL CHECK (type IN (
    'SCORE_CHANGE', 'SUPPLY_PRICE', 'STOCK_OUT', 'STOCK_RESTOCK', 'SEARCH_VOLUME', 'SYSTEM', 'NOTICE'
  )),
  title text NOT NULL,
  message text NOT NULL,
  favorite_id bigint REFERENCES chozy.member_favorites(id),
  created_at timestamptz NOT NULL,
  read_at timestamptz
);
CREATE INDEX ix_member_notifications_list
  ON chozy.member_notifications(member_id, (read_at IS NOT NULL), created_at DESC, id DESC);
