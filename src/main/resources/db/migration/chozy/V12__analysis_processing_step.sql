ALTER TABLE chozy.manual_analysis_jobs
  ADD COLUMN processing_step smallint
  CHECK (processing_step IS NULL OR (processing_step BETWEEN 2 AND 6 AND status <> 'PENDING'));
