-- auxi schema: pgvector factor vectors + cluster assignments
-- Vectors: pivoted factor values per user (16 dimensions: 13 known + 3 reserved)
-- Clusters: behavioral grouping for governance-gated personalization

CREATE TABLE auxi.user_factor_vectors (
  user_id     uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  vector      vector(16) NOT NULL,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

-- IVFFlat cosine similarity index. lists=10 is appropriate up to ~10K users.
-- Rebuild with lists=sqrt(n) at scale.
CREATE INDEX idx_user_factor_vectors_ivfflat
  ON auxi.user_factor_vectors USING ivfflat (vector vector_cosine_ops)
  WITH (lists = 10);

ALTER TABLE auxi.user_factor_vectors ENABLE ROW LEVEL SECURITY;


CREATE TABLE auxi.user_clusters (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  cluster_id  smallint NOT NULL,
  assigned_at timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT uq_user_clusters_user UNIQUE (user_id)
);

CREATE INDEX idx_user_clusters_cluster
  ON auxi.user_clusters (cluster_id);

ALTER TABLE auxi.user_clusters ENABLE ROW LEVEL SECURITY;
