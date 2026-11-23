-- =====================================================================
-- V8 - Dati di gestione degli incidenti
--
-- La tabella Incidente del PW descrive l'incidente ai fini ACN (tipo IS-x,
-- categoria, gravita', data di evidenza). Per gestirlo nel tempo servono
-- anche un titolo, uno stato e chi lo ha registrato.
--
-- Convenzione: tutti i TIMESTAMP di incidenti e notifiche sono in UTC.
-- Il backend converte da e verso Instant, cosi' le scadenze di 24 e 72 ore
-- non dipendono dal fuso del server ne' dall'ora legale.
-- =====================================================================
ALTER TABLE Incidente ADD (
    titolo              VARCHAR2(200),
    descrizione         VARCHAR2(2000),
    stato               VARCHAR2(10) DEFAULT 'APERTO' NOT NULL
                        CONSTRAINT ck_incidente_stato CHECK (stato IN ('APERTO', 'CHIUSO')),
    data_chiusura       TIMESTAMP,
    data_registrazione  TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
    registrato_da       VARCHAR2(64) DEFAULT NVL(SYS_CONTEXT('USERENV', 'CLIENT_IDENTIFIER'), USER) NOT NULL
);

ALTER TABLE Incidente MODIFY titolo NOT NULL;

ALTER TABLE Incidente ADD CONSTRAINT ck_incidente_chiusura CHECK (
    (stato = 'APERTO' AND data_chiusura IS NULL) OR (stato = 'CHIUSO' AND data_chiusura IS NOT NULL));

ALTER TABLE NotificaIncidente ADD (
    registrata_da       VARCHAR2(64) DEFAULT NVL(SYS_CONTEXT('USERENV', 'CLIENT_IDENTIFIER'), USER) NOT NULL
);

-- Pre-notifica, notifica e relazione finale si trasmettono una sola volta per
-- incidente; le relazioni intermedie e mensili possono ripetersi.
CREATE UNIQUE INDEX uq_notifica_unica ON NotificaIncidente (
    CASE WHEN tipo IN ('PRE_NOTIFICA', 'NOTIFICA', 'RELAZIONE_FINALE') THEN incidente_id END,
    CASE WHEN tipo IN ('PRE_NOTIFICA', 'NOTIFICA', 'RELAZIONE_FINALE') THEN tipo END);

CREATE INDEX idx_incidente_elemento ON IncidenteElementoCritico (elemento_critico_id);
