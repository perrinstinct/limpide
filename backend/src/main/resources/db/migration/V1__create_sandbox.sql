-- Racine de l'isolation entre visiteurs (ADR-0003) : chaque table métier référencera sandbox.id.
CREATE TABLE sandbox (
    id         UUID        PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- La purge cherche les bacs à sable expirés.
CREATE INDEX sandbox_expires_at_idx ON sandbox (expires_at);
