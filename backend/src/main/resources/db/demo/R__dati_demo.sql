-- =====================================================================
-- Dati dimostrativi: caricati solo con il profilo "demo"
--   ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
-- MERGE rende lo script ripetibile: un soggetto gia' presente non viene toccato.
-- Il primo soggetto e' quello del dataset di test del PW 19.
-- =====================================================================

MERGE INTO SoggettoNIS t
USING (SELECT '01234567890' AS codice_fiscale FROM dual) s
ON (t.codice_fiscale = s.codice_fiscale)
WHEN NOT MATCHED THEN INSERT
    (codice_fiscale, denominazione, indirizzo_sede_legale, pec, email_funzionale, telefono,
     fatturato, bilancio, n_dipendenti, tipo_soggetto_id)
VALUES
    ('01234567890', 'Azienda Energetica Alfa S.p.A.', 'Via Roma 10, Milano', 'alfa@pec.it',
     'sicurezza@alfa.it', '0212345678', 50000000, 12000000, 250,
     (SELECT id FROM TipoSoggettoNIS WHERE codice = 'ESSENZIALE'));

-- Una pubblica amministrazione: fatturato e bilancio vuoti (PA esente)
MERGE INTO SoggettoNIS t
USING (SELECT '80012345678' AS codice_fiscale FROM dual) s
ON (t.codice_fiscale = s.codice_fiscale)
WHEN NOT MATCHED THEN INSERT
    (codice_fiscale, denominazione, indirizzo_sede_legale, pec, email_funzionale, telefono,
     fatturato, bilancio, n_dipendenti, tipo_soggetto_id)
VALUES
    ('80012345678', 'Comune di Esempio', 'Piazza del Municipio 1, Esempio', 'protocollo@pec.comune.esempio.it',
     NULL, '061234567', NULL, NULL, 480,
     (SELECT id FROM TipoSoggettoNIS WHERE codice = 'IMPORTANTE'));

-- Assegnazioni dimostrative: operatore.demo e revisore.demo lavorano solo sull'azienda Alfa.
-- Il Comune di Esempio lo vede solo l'amministratore: e' la prova dell'isolamento.
MERGE INTO UtenteSoggetto t
USING (SELECT 'operatore.demo' AS utente, id AS soggetto_nis_id FROM SoggettoNIS WHERE codice_fiscale = '01234567890') s
ON (t.utente = s.utente AND t.soggetto_nis_id = s.soggetto_nis_id)
WHEN NOT MATCHED THEN INSERT (utente, soggetto_nis_id, ruolo, assegnato_da)
VALUES (s.utente, s.soggetto_nis_id, 'OPERATORE', 'dati-demo');

MERGE INTO UtenteSoggetto t
USING (SELECT 'revisore.demo' AS utente, id AS soggetto_nis_id FROM SoggettoNIS WHERE codice_fiscale = '01234567890') s
ON (t.utente = s.utente AND t.soggetto_nis_id = s.soggetto_nis_id)
WHEN NOT MATCHED THEN INSERT (utente, soggetto_nis_id, ruolo, assegnato_da)
VALUES (s.utente, s.soggetto_nis_id, 'REVISORE', 'dati-demo');
