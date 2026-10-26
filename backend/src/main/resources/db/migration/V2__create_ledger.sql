-- Grand livre en partie double, en ajout seul (ADR-0002, ADR-0004).

-- Compte comptable : le solde n'est qu'un cache de la somme des écritures.
-- min_balance est le plancher (0, ou négatif pour un découvert autorisé) ; NULL signifie sans limite.
CREATE TABLE ledger_account (
    id          UUID          PRIMARY KEY,
    sandbox_id  UUID          NOT NULL REFERENCES sandbox (id),
    label       VARCHAR(100)  NOT NULL,
    balance     NUMERIC(19,2) NOT NULL DEFAULT 0,
    min_balance NUMERIC(19,2),
    opened_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Cible des clés étrangères composites : une écriture ne peut viser qu'un compte de son bac à sable.
    UNIQUE (id, sandbox_id),
    CHECK (min_balance IS NULL OR balance >= min_balance)
);

CREATE TABLE ledger_transaction (
    id             UUID         PRIMARY KEY,
    sandbox_id     UUID         NOT NULL REFERENCES sandbox (id),
    description    VARCHAR(140) NOT NULL,
    reverses_id    UUID         UNIQUE,
    posted_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (id, sandbox_id),
    FOREIGN KEY (reverses_id, sandbox_id) REFERENCES ledger_transaction (id, sandbox_id)
);

-- Montant signé : positif pour un crédit, négatif pour un débit.
CREATE TABLE ledger_entry (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sandbox_id     UUID          NOT NULL,
    transaction_id UUID          NOT NULL,
    account_id     UUID          NOT NULL,
    amount         NUMERIC(19,2) NOT NULL CHECK (amount <> 0),
    FOREIGN KEY (transaction_id, sandbox_id) REFERENCES ledger_transaction (id, sandbox_id),
    FOREIGN KEY (account_id, sandbox_id) REFERENCES ledger_account (id, sandbox_id)
);

CREATE INDEX ledger_entry_transaction_idx ON ledger_entry (transaction_id);
CREATE INDEX ledger_entry_account_idx ON ledger_entry (account_id);

-- Ce qui est comptabilisé ne se modifie ni ne s'efface, même par un bug applicatif.
CREATE FUNCTION ledger_forbid_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Le grand livre est en ajout seul : % interdit sur %', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_transaction_append_only
    BEFORE UPDATE OR DELETE ON ledger_transaction
    FOR EACH ROW EXECUTE FUNCTION ledger_forbid_change();

CREATE TRIGGER ledger_entry_append_only
    BEFORE UPDATE OR DELETE ON ledger_entry
    FOR EACH ROW EXECUTE FUNCTION ledger_forbid_change();

-- TRUNCATE ne déclenche pas les triggers par ligne : il lui faut les siens.
CREATE TRIGGER ledger_transaction_no_truncate
    BEFORE TRUNCATE ON ledger_transaction
    FOR EACH STATEMENT EXECUTE FUNCTION ledger_forbid_change();

CREATE TRIGGER ledger_entry_no_truncate
    BEFORE TRUNCATE ON ledger_entry
    FOR EACH STATEMENT EXECUTE FUNCTION ledger_forbid_change();
