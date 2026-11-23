-- =====================================================================
-- V9 - Invii alla piattaforma ACN
--
-- Un InvioACN fotografa il profilo dichiarato: le viste ProfiloACN_*_AsOf
-- del PW lo ricostruiscono dagli storici all'istante data_invio. data_invio
-- resta sul default SYSTIMESTAMP, lo stesso orologio dei trigger di storico,
-- cosi' il confronto con valido_da / valido_a e' coerente (il container
-- Oracle gira in UTC, come i timestamp degli incidenti di V8).
-- =====================================================================
ALTER TABLE InvioACN ADD (
    registrato_da   VARCHAR2(64) DEFAULT NVL(SYS_CONTEXT('USERENV', 'CLIENT_IDENTIFIER'), USER) NOT NULL,
    note            VARCHAR2(500)
);

-- Le estrazioni CSV di un invio si registrano in LogEstrazione come le altre
-- operazioni tracciate del PW.
INSERT INTO RegistroTabelle (id, nome_tabella) VALUES (10, 'InvioACN');

-- Fornitori rilevanti AS OF: la vista del PW riportava denominazione e criterio,
-- ma non quale dipendenza e quale elemento. Senza una chiave stabile due invii
-- non si possono confrontare, e lo stesso fornitore su due asset sembrava un
-- duplicato. Stesse colonne iniziali, piu' identificativi in coda.
CREATE OR REPLACE VIEW ProfiloACN_FornitoriRilevanti_AsOf AS
SELECT i.id AS invio_id, i.soggetto_nis_id, i.data_invio,
       f.denominazione, dv.criterio_rilevanza, dv.tipologia_fornitura, dv.stato AS stato_al_momento_invio,
       d.id AS dipendenza_id, f.codice_fiscale AS fornitore_codice_fiscale,
       ec.id AS elemento_critico_id, ec.nome AS elemento_nome
FROM InvioACN i
JOIN Dipendenza_Versioni dv
    ON dv.valido_da <= i.data_invio
    AND (dv.valido_a IS NULL OR dv.valido_a > i.data_invio)
JOIN Dipendenza d ON d.id = dv.dipendenza_id
JOIN ElementoCritico ec ON ec.id = d.elemento_critico_id AND ec.soggetto_nis_id = i.soggetto_nis_id
JOIN Fornitore f ON f.id = d.fornitore_id;

-- Adempimento AS OF: si aggiunge l'id del requisito per il collegamento al catalogo.
CREATE OR REPLACE VIEW ProfiloACN_Adempimento_AsOf AS
SELECT i.id AS invio_id, i.soggetto_nis_id, i.data_invio,
       rm.codice AS requisito, sav.stato AS stato_al_momento_invio, sav.data_valutazione,
       rm.id AS requisito_id
FROM InvioACN i
JOIN StatoAdempimentoRequisito sar ON sar.soggetto_nis_id = i.soggetto_nis_id
JOIN StatoAdempimentoRequisito_Versioni sav
    ON sav.stato_adempimento_id = sar.id
    AND sav.valido_da <= i.data_invio
    AND (sav.valido_a IS NULL OR sav.valido_a > i.data_invio)
JOIN RequisitoMisura rm ON rm.id = sar.requisito_id;
