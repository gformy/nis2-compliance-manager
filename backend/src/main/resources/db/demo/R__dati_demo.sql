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
