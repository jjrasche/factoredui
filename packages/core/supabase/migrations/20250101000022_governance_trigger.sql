-- auxi schema: event-driven governance evaluation
-- Supplements pg_cron sweep with per-experiment evaluation on new factor data.
-- Fires when factor_snapshots receives new data, finds running experiments
-- that match the snapshot's component_path, and evaluates governance for each.
-- Skips evaluation if the experiment was evaluated within the last 5 minutes
-- to avoid contention during bulk factor refreshes.

-- Step 1: Extract single-experiment governance logic from evaluate_governance()
CREATE OR REPLACE FUNCTION auxi.evaluate_governance_for_experiment(
  p_experiment_id uuid
) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  exp RECORD;
  thr RECORD;
  v_control_users uuid[];
  v_factor_names text[];
  v_control_delta double precision;
  v_treatment_delta double precision;
  v_improvement double precision;
  v_is_significant boolean;
  v_best_variant text;
  v_best_abs_delta double precision;
  v_best_treatment_delta double precision;
  v_has_worsening boolean;
  v_factor_verdicts jsonb;
  v_significant_winners text[];
  v_verdict text;
  v_winner text;
  v_variant RECORD;
BEGIN
  SELECT e.id, e.component_path, e.created_at INTO exp
  FROM auxi.experiments e
  WHERE e.id = p_experiment_id
    AND e.status = 'running'
    AND EXISTS (
      SELECT 1 FROM auxi.thresholds t
      WHERE t.component_path = e.component_path
         OR t.component_path IS NULL
    );

  IF NOT FOUND THEN
    RETURN;
  END IF;

  v_factor_verdicts := '[]'::jsonb;
  v_has_worsening := false;
  v_significant_winners := ARRAY[]::text[];

  SELECT array_agg(ea.user_id) INTO v_control_users
  FROM auxi.experiment_assignments ea
  WHERE ea.experiment_id = exp.id AND ea.variant_key = 'control';

  SELECT array_agg(DISTINCT t.factor_name) INTO v_factor_names
  FROM auxi.thresholds t
  WHERE t.component_path = exp.component_path OR t.component_path IS NULL;

  IF v_control_users IS NULL OR v_factor_names IS NULL THEN
    INSERT INTO auxi.governance_log (experiment_id, verdict, factor_verdicts)
    VALUES (exp.id, 'continue', '[]'::jsonb);
    RETURN;
  END IF;

  FOR thr IN
    SELECT t.factor_name, t.operator, t.value
    FROM auxi.thresholds t
    WHERE (t.component_path = exp.component_path OR t.component_path IS NULL)
      AND t.factor_name = ANY(v_factor_names)
  LOOP
    SELECT bfd.avg_delta INTO v_control_delta
    FROM auxi.bulk_factor_deltas(
      v_control_users, exp.component_path,
      ARRAY[thr.factor_name], exp.created_at, now()
    ) bfd;

    IF v_control_delta IS NULL THEN
      CONTINUE;
    END IF;

    v_best_variant := NULL;
    v_best_abs_delta := -1;

    FOR v_variant IN
      SELECT ea.variant_key, array_agg(ea.user_id) AS user_ids
      FROM auxi.experiment_assignments ea
      WHERE ea.experiment_id = exp.id AND ea.variant_key != 'control'
      GROUP BY ea.variant_key
    LOOP
      SELECT bfd.avg_delta INTO v_treatment_delta
      FROM auxi.bulk_factor_deltas(
        v_variant.user_ids, exp.component_path,
        ARRAY[thr.factor_name], exp.created_at, now()
      ) bfd;

      IF v_treatment_delta IS NOT NULL AND ABS(v_treatment_delta) > v_best_abs_delta THEN
        v_best_abs_delta := ABS(v_treatment_delta);
        v_best_treatment_delta := v_treatment_delta;
        v_best_variant := v_variant.variant_key;
      END IF;
    END LOOP;

    IF v_best_variant IS NULL THEN
      CONTINUE;
    END IF;

    v_improvement := ABS(v_best_treatment_delta) - ABS(v_control_delta);

    v_is_significant := CASE thr.operator
      WHEN 'gt'  THEN v_improvement > thr.value
      WHEN 'lt'  THEN v_improvement < thr.value
      WHEN 'gte' THEN v_improvement >= thr.value
      WHEN 'lte' THEN v_improvement <= thr.value
      WHEN 'eq'  THEN v_improvement = thr.value
      ELSE false
    END;

    IF ABS(v_best_treatment_delta) < ABS(v_control_delta) THEN
      v_has_worsening := true;
    END IF;

    IF v_is_significant THEN
      v_significant_winners := array_append(v_significant_winners, v_best_variant);
    END IF;

    v_factor_verdicts := v_factor_verdicts || jsonb_build_object(
      'factor_name', thr.factor_name,
      'best_variant', v_best_variant,
      'best_delta', v_best_treatment_delta,
      'control_delta', v_control_delta,
      'is_significant', v_is_significant
    );
  END LOOP;

  IF v_has_worsening THEN
    v_verdict := 'flag_review';
    v_winner := NULL;
  ELSIF array_length(v_significant_winners, 1) IS NULL THEN
    v_verdict := 'continue';
    v_winner := NULL;
  ELSIF (SELECT COUNT(DISTINCT w) FROM unnest(v_significant_winners) w) != 1 THEN
    v_verdict := 'flag_review';
    v_winner := NULL;
  ELSE
    v_verdict := 'conclude';
    v_winner := v_significant_winners[1];
  END IF;

  INSERT INTO auxi.governance_log (experiment_id, verdict, winning_variant, factor_verdicts)
  VALUES (exp.id, v_verdict, v_winner, v_factor_verdicts);

  IF v_verdict = 'conclude' THEN
    UPDATE auxi.experiments
    SET status = 'concluded',
        concluded_at = now(),
        winning_variant = v_winner
    WHERE id = exp.id
      AND status = 'running';
  END IF;
END;
$$;

-- Step 2: Trigger function that debounces and dispatches
CREATE OR REPLACE FUNCTION auxi.on_factor_snapshot_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  v_experiment RECORD;
  v_last_evaluated timestamptz;
BEGIN
  -- Find running experiments matching this snapshot's component_path
  FOR v_experiment IN
    SELECT e.id
    FROM auxi.experiments e
    WHERE e.status = 'running'
      AND e.component_path = NEW.component_path
  LOOP
    -- Debounce: skip if evaluated within the last 5 minutes
    SELECT MAX(gl.evaluated_at) INTO v_last_evaluated
    FROM auxi.governance_log gl
    WHERE gl.experiment_id = v_experiment.id;

    IF v_last_evaluated IS NOT NULL
       AND v_last_evaluated > now() - interval '5 minutes' THEN
      CONTINUE;
    END IF;

    PERFORM auxi.evaluate_governance_for_experiment(v_experiment.id);
  END LOOP;

  RETURN NEW;
END;
$$;

-- Step 3: Attach trigger to factor_snapshots
CREATE TRIGGER trg_factor_snapshot_governance
  AFTER INSERT ON auxi.factor_snapshots
  FOR EACH ROW
  EXECUTE FUNCTION auxi.on_factor_snapshot_insert();

-- Step 4: Refactor evaluate_governance() to delegate to the per-experiment function
CREATE OR REPLACE FUNCTION auxi.evaluate_governance()
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  v_experiment_id uuid;
BEGIN
  FOR v_experiment_id IN
    SELECT DISTINCT e.id
    FROM auxi.experiments e
    WHERE e.status = 'running'
      AND EXISTS (
        SELECT 1 FROM auxi.thresholds t
        WHERE t.component_path = e.component_path
           OR t.component_path IS NULL
      )
  LOOP
    PERFORM auxi.evaluate_governance_for_experiment(v_experiment_id);
  END LOOP;
END;
$$;
