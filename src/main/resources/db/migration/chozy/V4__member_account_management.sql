ALTER TABLE chozy.members
    ADD COLUMN credential_version bigint NOT NULL DEFAULT 0 CHECK (credential_version >= 0),
    ADD COLUMN marketing_agreed boolean NOT NULL DEFAULT false,
    ADD COLUMN marketing_updated_at timestamptz,
    ADD CONSTRAINT ck_members_marketing_timestamp
        CHECK (NOT marketing_agreed OR marketing_updated_at IS NOT NULL);

CREATE TABLE chozy.marketing_consent_history (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id bigint NOT NULL REFERENCES chozy.members(id),
    agreed boolean NOT NULL,
    changed_at timestamptz NOT NULL
);
CREATE INDEX ix_marketing_consent_history_member
    ON chozy.marketing_consent_history(member_id, id);
