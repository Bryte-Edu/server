-- supabase/migrations/fsrs_tables.sql

-- Creates the core table for managing the FSRS memory state per flashcard
CREATE TABLE IF NOT EXISTS public.fsrs_states
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    user_id UUID NOT NULL REFERENCES auth.users
(
    id
) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES sessions
(
    id
)
  ON DELETE CASCADE,
    question_id UUID NOT NULL,
    topic_id UUID NOT NULL,
    state SMALLINT NOT NULL DEFAULT 0, -- 0 = New, 1 = Learning, 2 = Review, 3 = Relearning
    difficulty FLOAT8 NOT NULL DEFAULT 0.0,
    stability FLOAT8 NOT NULL DEFAULT 0.0,
    reps INT NOT NULL DEFAULT 0,
    lapses INT NOT NULL DEFAULT 0,
    last_review TEXT, -- Can be tracked as TIMESTAMPTZ, but kept as TEXT for serialization
    next_review TEXT, -- Same here
    CONSTRAINT unique_question_user UNIQUE
(
    question_id,
    user_id
)
    );

-- Enables RLS securely
ALTER TABLE public.fsrs_states ENABLE ROW LEVEL SECURITY;

-- Grants full access but only to rows owned by the user
CREATE
POLICY "Users can manage their own fsrs states"
    ON public.fsrs_states
    FOR ALL
    USING (auth.uid() = user_id);

-- ---------------------------------------------------------------------------------------------------------

-- Creates the analytics table rolled up by document chunk (topic_id)
CREATE TABLE IF NOT EXISTS public.topic_analytics
(
    id
    UUID
    PRIMARY
    KEY
    DEFAULT
    gen_random_uuid
(
),
    user_id UUID NOT NULL REFERENCES auth.users
(
    id
) ON DELETE CASCADE,
    session_id UUID NOT NULL,
    topic_id UUID NOT NULL,
    readiness_score FLOAT8 NOT NULL DEFAULT 0.0,
    total_reviews INT NOT NULL DEFAULT 0,
    correct_first_try INT NOT NULL DEFAULT 0,
    avg_time_spent FLOAT8 NOT NULL DEFAULT 0.0,
    last_updated TEXT,
    CONSTRAINT unique_topic_user UNIQUE
(
    topic_id,
    user_id
)
    );

-- Enables RLS securely
ALTER TABLE public.topic_analytics ENABLE ROW LEVEL SECURITY;

-- Grants full access but only to rows owned by the user
CREATE
POLICY "Users can manage their own topic analytics"
    ON public.topic_analytics
    FOR ALL
    USING (auth.uid() = user_id);
