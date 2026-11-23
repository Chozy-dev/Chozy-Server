ALTER TABLE chozy.manual_analysis_jobs
  ALTER COLUMN product_name DROP NOT NULL,
  ALTER COLUMN wholesale_price DROP NOT NULL,
  ALTER COLUMN shipping_fee DROP NOT NULL,
  ADD COLUMN source varchar(10) NOT NULL DEFAULT 'MANUAL',
  ADD COLUMN product_id varchar(100),
  ADD COLUMN original_url varchar(2048),
  ADD COLUMN resolved_url varchar(2048),
  ADD COLUMN selected_option_id varchar(200),
  ADD CONSTRAINT ck_analysis_job_source_input CHECK (
    (source = 'MANUAL' AND product_name IS NOT NULL AND wholesale_price IS NOT NULL AND shipping_fee IS NOT NULL
      AND product_id IS NULL AND original_url IS NULL AND resolved_url IS NULL AND selected_option_id IS NULL)
    OR
    (source IN ('DOMEME', 'OWNERCLAN') AND product_id IS NOT NULL AND original_url IS NOT NULL AND resolved_url IS NOT NULL
      AND wholesale_price IS NULL AND shipping_fee IS NULL AND NOT has_price_policy AND minimum_selling_price IS NULL)
  );
