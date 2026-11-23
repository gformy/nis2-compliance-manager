-- =====================================================================
-- Permessi dell'utente applicativo (${app_user}) - principio del minimo privilegio
--
-- Il backend si collega con ${app_user}, che non possiede lo schema e non puo'
-- modificarne la struttura. Le tabelle *Storico e LogEstrazione le scrivono
-- i trigger, che girano con i diritti del proprietario: all'applicazione
-- basta leggerle. I cataloghi normativi sono in sola lettura.
-- ElementoCritico, Asset e Servizio non ricevono DELETE: oltre ai trigger
-- di blocco del PW, il permesso proprio non esiste.
-- UtenteSoggetto (V5) rientra nelle tabelle con lettura e scrittura: le
-- assegnazioni le modifica solo l'amministratore, controllo fatto dal backend.
--
-- Migrazione ripetibile: Flyway la riesegue quando il file cambia.
-- =====================================================================
DECLARE
    PROCEDURE concedi(p_oggetto VARCHAR2, p_privilegi VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'GRANT ' || p_privilegi || ' ON ' || p_oggetto || ' TO ${app_user}';
    END;
BEGIN
    FOR t IN (SELECT table_name FROM user_tables
              WHERE dropped = 'NO'
                AND LOWER(table_name) <> LOWER('${flyway:table}')) LOOP
        IF t.table_name LIKE '%STORICO'
           OR t.table_name IN ('LOGESTRAZIONE', 'REGISTROTABELLE', 'TIPOSOGGETTONIS',
                               'TIPOINCIDENTE', 'INCIDENTEAPPLICABILITA', 'CATEGORIAINCIDENTE',
                               'TIPODOCUMENTOGOVERNANCE', 'DOCUMENTOGOVERNANCEAPPLICABILITA',
                               'MISURASICUREZZA', 'REQUISITOMISURA', 'MISURAAPPLICABILITA',
                               'CODICECPV') THEN
            concedi(t.table_name, 'SELECT');
        ELSIF t.table_name IN ('ELEMENTOCRITICO', 'ASSET', 'SERVIZIO') THEN
            concedi(t.table_name, 'SELECT, INSERT, UPDATE');
        ELSE
            concedi(t.table_name, 'SELECT, INSERT, UPDATE, DELETE');
        END IF;
    END LOOP;

    FOR v IN (SELECT view_name FROM user_views) LOOP
        IF v.view_name IN ('ASSETCOMPLETO', 'SERVIZIOCOMPLETO') THEN
            concedi(v.view_name, 'SELECT, INSERT');
        ELSE
            concedi(v.view_name, 'SELECT');
        END IF;
    END LOOP;

    -- Sequenze in sola lettura: dopo un inserimento dalle viste ISA (AssetCompleto,
    -- ServizioCompleto) il backend legge seq_elemento_critico.CURRVAL per conoscere
    -- l'id appena creato, perche' una vista con trigger INSTEAD OF non supporta RETURNING.
    FOR s IN (SELECT sequence_name FROM user_sequences WHERE sequence_name NOT LIKE 'ISEQ$$%') LOOP
        concedi(s.sequence_name, 'SELECT');
    END LOOP;

    concedi('fn_utente_corrente', 'EXECUTE');
END;
/
