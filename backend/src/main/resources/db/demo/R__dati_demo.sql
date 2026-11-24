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

-- Inventario dimostrativo dell'azienda Alfa, inserito dalle viste ISA come prevede il PW.
-- Le viste con trigger INSTEAD OF non accettano MERGE: si controlla l'esistenza a mano.
DECLARE
    v_soggetto SoggettoNIS.id%TYPE;

    FUNCTION elemento(p_nome VARCHAR2) RETURN NUMBER IS
        v_id NUMBER;
    BEGIN
        SELECT MAX(id) INTO v_id FROM ElementoCritico WHERE soggetto_nis_id = v_soggetto AND nome = p_nome;
        RETURN v_id;
    END;

    PROCEDURE asset(p_nome VARCHAR2, p_tipo VARCHAR2, p_rilevante NUMBER) IS
    BEGIN
        IF elemento(p_nome) IS NULL THEN
            INSERT INTO AssetCompleto (nome, soggetto_nis_id, tipo_asset, rilevante)
            VALUES (p_nome, v_soggetto, p_tipo, p_rilevante);
        END IF;
    END;

    PROCEDURE servizio(p_nome VARCHAR2, p_descrizione VARCHAR2) IS
        v_id    NUMBER;
        v_conta NUMBER;
    BEGIN
        IF elemento(p_nome) IS NULL THEN
            INSERT INTO ServizioCompleto (nome, soggetto_nis_id, descrizione, categoria_rilevanza)
            VALUES (p_nome, v_soggetto, p_descrizione, NULL);
        END IF;
        -- Le funzioni locali non si possono usare dentro le istruzioni SQL: prima in variabile
        v_id := elemento(p_nome);
        SELECT COUNT(*) INTO v_conta FROM ServizioStatoMembro WHERE servizio_id = v_id;
        IF v_conta = 0 THEN
            INSERT INTO ServizioStatoMembro (servizio_id, stato_membro) VALUES (v_id, 'IT');
        END IF;
    END;

    PROCEDURE collega(p_asset VARCHAR2, p_servizio VARCHAR2) IS
        v_asset    NUMBER := elemento(p_asset);
        v_servizio NUMBER := elemento(p_servizio);
        v_conta    NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_conta FROM AssetServizio
        WHERE asset_id = v_asset AND servizio_id = v_servizio;
        IF v_conta = 0 THEN
            INSERT INTO AssetServizio (asset_id, servizio_id) VALUES (v_asset, v_servizio);
        END IF;
    END;
BEGIN
    SELECT id INTO v_soggetto FROM SoggettoNIS WHERE codice_fiscale = '01234567890';

    asset('Server applicativo SCADA', 'HARDWARE', 1);
    asset('Sistema di telecontrollo della rete', 'SOFTWARE', 1);
    asset('Collegamento MPLS cabine primarie', 'FLUSSO_RETE', 1);
    asset('Posta elettronica in cloud', 'SERVIZIO_FORNITORE', 0);

    servizio('Distribuzione energia elettrica - area Nord',
             'Distribuzione di energia elettrica sulla rete di media e bassa tensione dell''area Nord');
    servizio('Area clienti online', 'Portale per bollette, letture e segnalazione guasti');

    collega('Server applicativo SCADA', 'Distribuzione energia elettrica - area Nord');
    collega('Sistema di telecontrollo della rete', 'Distribuzione energia elettrica - area Nord');
    collega('Collegamento MPLS cabine primarie', 'Distribuzione energia elettrica - area Nord');
    collega('Posta elettronica in cloud', 'Area clienti online');
END;
/

-- Fornitori dimostrativi (anagrafica condivisa) e dipendenze dell'azienda Alfa.
-- Lo stesso fornitore puo' servire piu' elementi con criteri diversi.
MERGE INTO Fornitore t
USING (
    SELECT '09876543210' AS codice_fiscale, 'Cloud Italia S.p.A.' AS denominazione, 'IT' AS paese FROM dual UNION ALL
    SELECT '01122334455', 'Rete Telecomunicazioni S.r.l.', 'IT' FROM dual UNION ALL
    SELECT 'DE123456789', 'SCADA Systems GmbH', 'DE' FROM dual
) s
ON (t.codice_fiscale = s.codice_fiscale)
WHEN NOT MATCHED THEN INSERT (denominazione, codice_fiscale, paese_sede_legale)
VALUES (s.denominazione, s.codice_fiscale, s.paese);

MERGE INTO FornitoreCPV t
USING (
    SELECT f.id AS fornitore_id, c.id AS cpv_id
    FROM Fornitore f
    JOIN CodiceCPV c ON (f.codice_fiscale, c.codice) IN (
        ('09876543210', '72000000'), ('09876543210', '72317000'),
        ('01122334455', '64200000'), ('01122334455', '32400000'),
        ('DE123456789', '48000000'), ('DE123456789', '50312000'))
) s
ON (t.fornitore_id = s.fornitore_id AND t.cpv_id = s.cpv_id)
WHEN NOT MATCHED THEN INSERT (fornitore_id, cpv_id) VALUES (s.fornitore_id, s.cpv_id);

MERGE INTO Dipendenza t
USING (
    SELECT f.id AS fornitore_id, ec.id AS elemento_critico_id, d.criterio, d.tipologia, d.referente
    FROM (
        SELECT 'DE123456789' AS cf, 'Server applicativo SCADA' AS elemento, 'ICT' AS criterio,
               'Licenza e assistenza del software SCADA' AS tipologia, 'support@scada-systems.example' AS referente FROM dual UNION ALL
        SELECT 'DE123456789', 'Distribuzione energia elettrica - area Nord', 'NON_FUNGIBILE',
               'Unico fornitore certificato per il sistema di controllo della rete', 'support@scada-systems.example' FROM dual UNION ALL
        SELECT '01122334455', 'Collegamento MPLS cabine primarie', 'ICT',
               'Connettivita'' MPLS dedicata', 'noc@rete-tlc.example' FROM dual UNION ALL
        SELECT '09876543210', 'Posta elettronica in cloud', 'ICT',
               'Servizio di posta elettronica in SaaS', 'assistenza@cloud-italia.example' FROM dual
    ) d
    JOIN Fornitore f ON f.codice_fiscale = d.cf
    JOIN ElementoCritico ec ON ec.nome = d.elemento
    JOIN SoggettoNIS s ON s.id = ec.soggetto_nis_id AND s.codice_fiscale = '01234567890'
) s
ON (t.fornitore_id = s.fornitore_id AND t.elemento_critico_id = s.elemento_critico_id)
WHEN NOT MATCHED THEN INSERT (fornitore_id, elemento_critico_id, criterio_rilevanza, tipologia_fornitura, referente_contatto)
VALUES (s.fornitore_id, s.elemento_critico_id, s.criterio, s.tipologia, s.referente);

-- Valutazioni di conformita' dell'azienda Alfa: un quadro realistico, non tutto verde.
MERGE INTO StatoAdempimentoRequisito t
USING (
    SELECT rm.id AS requisito_id, s.id AS soggetto_nis_id, v.stato, v.note
    FROM (
        SELECT 'ID.AM-01.1' AS codice, 'ATTUATO' AS stato, 'Inventario hardware mantenuto in questa applicazione' AS note FROM dual UNION ALL
        SELECT 'GV.OC-04.1', 'ATTUATO', 'Asset rilevanti contrassegnati nell''inventario' FROM dual UNION ALL
        SELECT 'GV.SC-04.1', 'PARZIALMENTE_ATTUATO', 'Mancano i referenti di due fornitori minori' FROM dual UNION ALL
        SELECT 'RS.MA-01.1', 'NON_ATTUATO', 'Piano in bozza, da approvare in CdA' FROM dual
    ) v
    JOIN RequisitoMisura rm ON rm.codice = v.codice
    JOIN SoggettoNIS s ON s.codice_fiscale = '01234567890'
) s
ON (t.requisito_id = s.requisito_id AND t.soggetto_nis_id = s.soggetto_nis_id)
WHEN NOT MATCHED THEN INSERT (requisito_id, soggetto_nis_id, stato, note)
VALUES (s.requisito_id, s.soggetto_nis_id, s.stato, s.note);

-- Incidenti dell'azienda Alfa (orari in UTC, vedi V8):
--  * uno chiuso a inizio anno, con tutte le trasmissioni al CSIRT nei termini;
--  * uno aperto da poche ore, con la pre-notifica ancora da fare, cosi' la demo
--    mostra subito una scadenza in corso.
DECLARE
    v_soggetto  NUMBER;
    v_incidente NUMBER;
    v_ora       TIMESTAMP := SYS_EXTRACT_UTC(SYSTIMESTAMP);

    FUNCTION tipo(p_codice VARCHAR2) RETURN NUMBER IS
        v_id NUMBER;
    BEGIN
        SELECT id INTO v_id FROM TipoIncidente WHERE codice = p_codice;
        RETURN v_id;
    END;

    FUNCTION categoria(p_nome VARCHAR2) RETURN NUMBER IS
        v_id NUMBER;
    BEGIN
        SELECT id INTO v_id FROM CategoriaIncidente WHERE nome = p_nome;
        RETURN v_id;
    END;

    FUNCTION esiste(p_titolo VARCHAR2) RETURN BOOLEAN IS
        v_conta NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_conta FROM Incidente WHERE soggetto_nis_id = v_soggetto AND titolo = p_titolo;
        RETURN v_conta > 0;
    END;

    PROCEDURE coinvolgi(p_nome VARCHAR2) IS
    BEGIN
        INSERT INTO IncidenteElementoCritico (incidente_id, elemento_critico_id)
        SELECT v_incidente, id FROM ElementoCritico WHERE soggetto_nis_id = v_soggetto AND nome = p_nome;
    END;
BEGIN
    SELECT id INTO v_soggetto FROM SoggettoNIS WHERE codice_fiscale = '01234567890';

    IF NOT esiste('Phishing mirato all''ufficio acquisti') THEN
        DECLARE
            v_tipo NUMBER := tipo('IS-4');
            v_cat  NUMBER := categoria('PHISHING');
        BEGIN
            INSERT INTO Incidente (tipo_incidente_id, soggetto_nis_id, categoria_id, data_evidenza, gravita,
                                   root_cause, titolo, descrizione, stato, data_chiusura, registrato_da)
            VALUES (v_tipo, v_soggetto, v_cat, TIMESTAMP '2026-02-03 07:40:00', 'MEDIA',
                    'Credenziali raccolte con una falsa pagina di accesso alla posta',
                    'Phishing mirato all''ufficio acquisti',
                    'Accesso alla casella di un addetto con credenziali sottratte; inoltri verso un indirizzo esterno.',
                    'CHIUSO', TIMESTAMP '2026-02-20 16:00:00', 'operatore.demo')
            RETURNING id INTO v_incidente;
        END;
        coinvolgi('Posta elettronica in cloud');

        INSERT INTO NotificaIncidente (incidente_id, tipo, data_trasmissione, contenuto, registrata_da)
        SELECT v_incidente, tipo, data_trasmissione, contenuto, 'operatore.demo' FROM (
            SELECT 'PRE_NOTIFICA' AS tipo, TIMESTAMP '2026-02-03 15:10:00' AS data_trasmissione,
                   'Accesso abusivo a una casella di posta, in corso di analisi' AS contenuto FROM dual UNION ALL
            SELECT 'NOTIFICA', TIMESTAMP '2026-02-05 11:30:00',
                   'Credenziali sottratte via phishing; reset password e MFA obbligatoria' FROM dual UNION ALL
            SELECT 'RELAZIONE_FINALE', TIMESTAMP '2026-02-27 09:00:00',
                   'Causa, impatto e misure adottate; nessun dato esfiltrato oltre la casella' FROM dual);

        INSERT INTO IndicatoreCompromissione (incidente_id, tipo, valore, data_rilevazione)
        SELECT v_incidente, tipo, valore, TIMESTAMP '2026-02-03 09:00:00' FROM (
            SELECT 'DOMINIO' AS tipo, 'login-posta-alfa.example' AS valore FROM dual UNION ALL
            SELECT 'IP', '198.51.100.23' FROM dual);

        INSERT INTO TimelineEvento (incidente_id, data_ora, fase, descrizione)
        SELECT v_incidente, data_ora, fase, descrizione FROM (
            SELECT TIMESTAMP '2026-02-03 07:40:00' AS data_ora, 'RILEVAMENTO' AS fase,
                   'Segnalazione dell''addetto: email inviate a sua insaputa' AS descrizione FROM dual UNION ALL
            SELECT TIMESTAMP '2026-02-03 08:30:00', 'CONTENIMENTO', 'Sessioni revocate e password reimpostata' FROM dual UNION ALL
            SELECT TIMESTAMP '2026-02-04 14:00:00', 'INVESTIGAZIONE', 'Analisi dei log di accesso: un solo account coinvolto' FROM dual UNION ALL
            SELECT TIMESTAMP '2026-02-10 10:00:00', 'RIPRISTINO', 'MFA attivata per tutto l''ufficio acquisti' FROM dual);

        INSERT INTO AttivitaRisposta (incidente_id, fase, descrizione, motivazione, esito, data_esecuzione)
        VALUES (v_incidente, 'CONTENIMENTO', 'Blocco del dominio malevolo sul proxy',
                'Impedire nuovi accessi alla pagina contraffatta', 'Completato', TIMESTAMP '2026-02-03 09:15:00');
    END IF;

    IF NOT esiste('Interruzione del sistema SCADA di telecontrollo') THEN
        DECLARE
            v_tipo NUMBER := tipo('IS-3');
            v_cat  NUMBER := categoria('MALWARE');
        BEGIN
            INSERT INTO Incidente (tipo_incidente_id, soggetto_nis_id, categoria_id, data_evidenza, gravita,
                                   titolo, descrizione, registrato_da)
            VALUES (v_tipo, v_soggetto, v_cat, v_ora - INTERVAL '6' HOUR, 'ALTA',
                    'Interruzione del sistema SCADA di telecontrollo',
                    'Il telecontrollo delle cabine non risponde; sospetto malware sulla workstation di supervisione.',
                    'operatore.demo')
            RETURNING id INTO v_incidente;
        END;
        coinvolgi('Server applicativo SCADA');
        coinvolgi('Sistema di telecontrollo della rete');
        INSERT INTO TimelineEvento (incidente_id, data_ora, fase, descrizione)
        VALUES (v_incidente, v_ora - INTERVAL '6' HOUR, 'RILEVAMENTO', 'Allarme del centro di controllo: cabine non raggiungibili');
    END IF;
END;
/

-- Primo invio dell'azienda Alfa: fotografa il profilo appena caricato. Dopo una
-- modifica, un nuovo invio (come admin.demo) mostra le differenze nel confronto.
INSERT INTO InvioACN (soggetto_nis_id, tipo_invio, periodo_riferimento, note, registrato_da)
SELECT s.id, 'REGISTRAZIONE', 2026, 'Prima registrazione sulla piattaforma ACN', 'admin.demo'
FROM SoggettoNIS s
WHERE s.codice_fiscale = '01234567890'
  AND NOT EXISTS (SELECT 1 FROM InvioACN i WHERE i.soggetto_nis_id = s.id);

-- Governance dell'azienda Alfa: organi di amministrazione e alcuni documenti in
-- stati diversi. Le date sono relative a oggi, cosi' gli stati restano gli stessi
-- in qualunque giorno si avvii la demo:
--  * politica di gestione del rischio: approvata 2 mesi fa (valida);
--  * piano di formazione: approvato 11 mesi e 20 giorni fa (riesame in scadenza);
--  * piano di gestione degli incidenti: approvato 14 mesi fa (riesame scaduto);
--  * politica di risposta agli incidenti: caricata ma non ancora approvata.
DECLARE
    v_soggetto NUMBER;
    v_cda      NUMBER;

    FUNCTION pdf(p_titolo VARCHAR2) RETURN RAW IS
        v_testo VARCHAR2(2000);
    BEGIN
        v_testo := '%PDF-1.4' || CHR(10)
            || '1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj' || CHR(10)
            || '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj' || CHR(10)
            || '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]/Contents 4 0 R'
            || '/Resources<</Font<</F1 5 0 R>>>>>>endobj' || CHR(10)
            || '4 0 obj<</Length ' || (LENGTH(p_titolo) + 60) || '>>stream' || CHR(10)
            || 'BT /F1 16 Tf 60 780 Td (' || p_titolo || ') Tj 0 -24 Td (Documento dimostrativo) Tj ET'
            || CHR(10) || 'endstream endobj' || CHR(10)
            || '5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj' || CHR(10)
            || 'trailer<</Root 1 0 R>>' || CHR(10) || '%%EOF';
        RETURN UTL_RAW.CAST_TO_RAW(v_testo);
    END;

    PROCEDURE documento(p_codice VARCHAR2, p_titolo VARCHAR2, p_mesi_fa NUMBER, p_giorni_fa NUMBER) IS
        v_tipo NUMBER;
        v_doc  NUMBER;
        v_file RAW(2000);
        v_hash VARCHAR2(64);
        v_data DATE;
    BEGIN
        SELECT id INTO v_tipo FROM TipoDocumentoGovernance WHERE codice = p_codice;
        INSERT INTO DocumentoGovernance (soggetto_nis_id, tipo_documento_id, versione, periodicita_riesame_mesi)
        VALUES (v_soggetto, v_tipo, '1.0', 12)
        RETURNING id INTO v_doc;
        v_file := pdf(p_titolo);
        SELECT LOWER(RAWTOHEX(STANDARD_HASH(v_file, 'SHA256'))) INTO v_hash FROM dual;
        INSERT INTO DocumentoGovernanceFile (documento_id, versione_documento, nome_file, content_type,
                                             dimensione, sha256, contenuto, caricato_da)
        VALUES (v_doc, '1.0', LOWER(REPLACE(p_codice, '.', '-')) || '.pdf', 'application/pdf',
                UTL_RAW.LENGTH(v_file), v_hash, v_file, 'operatore.demo');
        IF p_mesi_fa IS NOT NULL THEN
            v_data := ADD_MONTHS(TRUNC(SYSDATE), -p_mesi_fa) - p_giorni_fa;
            UPDATE DocumentoGovernance
            SET data_approvazione = v_data, approvato_da = v_cda, data_ultimo_riesame = v_data
            WHERE id = v_doc;
        END IF;
    END;
BEGIN
    SELECT id INTO v_soggetto FROM SoggettoNIS WHERE codice_fiscale = '01234567890';

    SELECT MIN(id) INTO v_cda FROM OrganoAmministrazione WHERE soggetto_nis_id = v_soggetto;
    IF v_cda IS NULL THEN
        INSERT INTO OrganoAmministrazione (soggetto_nis_id, nome, ruolo)
        VALUES (v_soggetto, 'Consiglio di amministrazione', 'Organo di amministrazione')
        RETURNING id INTO v_cda;
        INSERT INTO OrganoAmministrazione (soggetto_nis_id, nome, ruolo)
        VALUES (v_soggetto, 'Comitato rischi e sicurezza', 'Organo direttivo');
    END IF;

    FOR d IN (SELECT 1 FROM dual
              WHERE NOT EXISTS (SELECT 1 FROM DocumentoGovernance WHERE soggetto_nis_id = v_soggetto)) LOOP
        documento('GV.PO-01-a', 'Politica di gestione del rischio', 2, 0);
        documento('PR.AT-01', 'Piano di formazione', 11, 20);
        documento('RS.MA-01', 'Piano di gestione degli incidenti', 14, 0);
        documento('GV.PO-01-p', 'Politica di risposta agli incidenti', NULL, NULL);
    END LOOP;
END;
/

-- Formazione dell'azienda Alfa: sei persone, tre attivita' e un registro che
-- lascia volutamente un buco (l'amministratore di sistema senza formazione
-- dedicata), cosi' la copertura dei ruoli specializzati non e' al 100%.
DECLARE
    v_soggetto NUMBER;
    v_base     NUMBER;
    v_cda      NUMBER;

    FUNCTION persona(p_nome VARCHAR2, p_categoria VARCHAR2, p_ruolo VARCHAR2) RETURN NUMBER IS
        v_id NUMBER;
    BEGIN
        INSERT INTO PersonaFormazione (soggetto_nis_id, nome, categoria, ruolo)
        VALUES (v_soggetto, p_nome, p_categoria, p_ruolo) RETURNING id INTO v_id;
        RETURN v_id;
    END;

    PROCEDURE partecipa(p_attivita NUMBER, p_persona NUMBER, p_data DATE, p_esito VARCHAR2) IS
    BEGIN
        INSERT INTO PartecipazioneFormazione (attivita_id, persona_id, data_svolgimento, esito_verifica, registrata_da)
        VALUES (p_attivita, p_persona, p_data, p_esito, 'operatore.demo');
    END;
BEGIN
    SELECT id INTO v_soggetto FROM SoggettoNIS WHERE codice_fiscale = '01234567890';

    FOR x IN (SELECT 1 FROM dual
              WHERE NOT EXISTS (SELECT 1 FROM PersonaFormazione WHERE soggetto_nis_id = v_soggetto)) LOOP
        DECLARE
            v_presidente NUMBER := persona('Laura Verdi', 'ORGANO', 'Presidente del CdA');
            v_ad         NUMBER := persona('Mario Bianchi', 'ORGANO', 'Amministratore delegato');
            v_sistemista NUMBER := persona('Paolo Neri', 'RUOLO_SPECIALIZZATO', 'Amministratore di sistema');
            v_soc        NUMBER := persona('Sara Gallo', 'RUOLO_SPECIALIZZATO', 'Analista SOC');
            v_acquisti   NUMBER := persona('Giulia Russo', 'PERSONALE', 'Ufficio acquisti');
            v_tecnico    NUMBER := persona('Luca Ferri', 'PERSONALE', 'Tecnico di cabina');
            v_spec       NUMBER;
        BEGIN
            INSERT INTO AttivitaFormativa (soggetto_nis_id, titolo, contenuti, destinatari, data_prevista,
                                           durata_ore, modalita_verifica)
            VALUES (v_soggetto, 'Consapevolezza sulla sicurezza informatica',
                    'Phishing e ingegneria sociale, gestione delle password, segnalazione degli eventi sospetti',
                    'TUTTI', ADD_MONTHS(TRUNC(SYSDATE), -3), 2, 'Questionario di 10 domande, soglia 70%')
            RETURNING id INTO v_base;
            INSERT INTO AttivitaFormativa (soggetto_nis_id, titolo, contenuti, destinatari, data_prevista, durata_ore)
            VALUES (v_soggetto, 'NIS2 per gli organi di amministrazione',
                    'Responsabilita'' degli organi (art. 23 D.Lgs. 138/2024), approvazione delle politiche, gestione del rischio',
                    'ORGANO', ADD_MONTHS(TRUNC(SYSDATE), -1), 3)
            RETURNING id INTO v_cda;
            INSERT INTO AttivitaFormativa (soggetto_nis_id, titolo, contenuti, destinatari, data_prevista,
                                           durata_ore, modalita_verifica)
            VALUES (v_soggetto, 'Hardening e risposta agli eventi per amministratori di sistema',
                    'Configurazione sicura dei sistemi, minacce note al settore energetico, comportamento in caso di eventi rilevanti',
                    'RUOLO_SPECIALIZZATO', TRUNC(SYSDATE) + 20, 8, 'Esercitazione pratica valutata dal CISO')
            RETURNING id INTO v_spec;

            partecipa(v_base, v_presidente, ADD_MONTHS(TRUNC(SYSDATE), -3), 'SUPERATA');
            partecipa(v_base, v_ad, ADD_MONTHS(TRUNC(SYSDATE), -3), 'SUPERATA');
            partecipa(v_base, v_acquisti, ADD_MONTHS(TRUNC(SYSDATE), -3), 'NON_SUPERATA');
            partecipa(v_base, v_acquisti, ADD_MONTHS(TRUNC(SYSDATE), -2), 'SUPERATA');
            partecipa(v_base, v_tecnico, ADD_MONTHS(TRUNC(SYSDATE), -3), 'SUPERATA');
            partecipa(v_base, v_sistemista, ADD_MONTHS(TRUNC(SYSDATE), -3), 'SUPERATA');
            partecipa(v_cda, v_presidente, ADD_MONTHS(TRUNC(SYSDATE), -1), 'NON_PREVISTA');
            partecipa(v_cda, v_ad, ADD_MONTHS(TRUNC(SYSDATE), -1), 'NON_PREVISTA');
        END;
    END LOOP;
END;
/

-- Organizzazioni: lo studio di consulenza (rivenditore, piano Partner) gestisce
-- l'azienda Alfa ed e' amministrato da consulente.demo; il Comune e' un cliente
-- diretto con il piano Base. consulente.demo non vede il Comune.
MERGE INTO Organizzazione o
USING (SELECT 'Studio Rinaldi & Partner' AS nome, '12345678901' AS cf, 'RIVENDITORE' AS tipo, 4 AS piano,
              'RIV-2026-001' AS contratto FROM dual UNION ALL
       SELECT 'Comune di Esempio', '80012345678', 'CLIENTE_DIRETTO', 2, 'CLI-2026-014' FROM dual) s
ON (o.codice_fiscale = s.cf)
WHEN NOT MATCHED THEN
    INSERT (nome, codice_fiscale, tipo, piano_id, riferimento_contratto, data_contratto)
    VALUES (s.nome, s.cf, s.tipo, s.piano, s.contratto, DATE '2026-09-01');

UPDATE SoggettoNIS SET organizzazione_id = (SELECT id FROM Organizzazione WHERE codice_fiscale = '12345678901')
WHERE codice_fiscale = '01234567890';
UPDATE SoggettoNIS SET organizzazione_id = (SELECT id FROM Organizzazione WHERE codice_fiscale = '80012345678')
WHERE codice_fiscale = '80012345678';

-- Utenti demo dello studio: non devono avere accessi a soggetti di altre organizzazioni.
DELETE FROM UtenteSoggetto
WHERE utente IN ('operatore.demo', 'revisore.demo')
  AND soggetto_nis_id IN (SELECT id FROM SoggettoNIS WHERE codice_fiscale = '80012345678');

MERGE INTO UtenteOrganizzazione u
USING (SELECT 'consulente.demo' AS utente, id AS organizzazione_id FROM Organizzazione
       WHERE codice_fiscale = '12345678901') s
ON (u.utente = s.utente)
WHEN NOT MATCHED THEN INSERT (utente, organizzazione_id, assegnato_da)
    VALUES (s.utente, s.organizzazione_id, 'admin.demo');
