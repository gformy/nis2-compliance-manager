-- =====================================================================
-- V7 - Catalogo delle misure di sicurezza (Allegati 1 e 2, det. ACN 164179/2025)
--
-- Stesso sottoinsieme rappresentativo del PW (query test/test_popolamento_dati_misure.sql):
-- 5 misure con un requisito ciascuno, applicabili a soggetti essenziali e importanti.
-- Unica correzione: il codice GV.OC-4 diventa GV.OC-04, nel formato XX.YY-NN
-- usato dalle specifiche ACN. Il catalogo completo (116 requisiti per gli
-- essenziali, 87 per gli importanti) si aggiunge con migrazioni successive.
-- MERGE: i dati gia' caricati dai test del PW non si duplicano.
-- =====================================================================

MERGE INTO MisuraSicurezza t
USING (
    SELECT 'GV.SC-04' AS codice, 'Gestione del rischio della catena di approvvigionamento' AS categoria,
           'È mantenuto un inventario aggiornato dei fornitori con potenziale impatto sulla sicurezza' AS descrizione FROM dual UNION ALL
    SELECT 'ID.AM-01', 'Gestione degli asset',
           'È mantenuto un inventario aggiornato degli apparati fisici (hardware)' FROM dual UNION ALL
    SELECT 'ID.AM-04', 'Gestione degli asset',
           'È mantenuto un inventario aggiornato dei servizi informatici erogati dai fornitori' FROM dual UNION ALL
    SELECT 'GV.OC-04', 'Contesto organizzativo',
           'È mantenuto un elenco aggiornato dei sistemi informativi e di rete rilevanti' FROM dual UNION ALL
    SELECT 'RS.MA-01', 'Gestione della risposta agli incidenti',
           'È definito, documentato e approvato un piano di risposta agli incidenti' FROM dual
) s
ON (t.codice = s.codice)
WHEN NOT MATCHED THEN INSERT (codice, categoria, descrizione) VALUES (s.codice, s.categoria, s.descrizione);

MERGE INTO RequisitoMisura t
USING (
    SELECT r.codice, m.id AS misura_id, r.numero_punto, r.testo
    FROM (
        SELECT 'GV.SC-04.1' AS codice, 'GV.SC-04' AS misura, 1 AS numero_punto,
               'È mantenuto un inventario aggiornato dei fornitori, comprensivo di estremi di contatto del referente e tipologia di fornitura' AS testo FROM dual UNION ALL
        SELECT 'ID.AM-01.1', 'ID.AM-01', 1,
               'È mantenuto un inventario aggiornato degli apparati fisici che compongono i sistemi informativi e di rete' FROM dual UNION ALL
        SELECT 'ID.AM-04.1', 'ID.AM-04', 1,
               'È mantenuto un inventario aggiornato dei servizi informatici erogati dai fornitori, ivi inclusi i servizi cloud' FROM dual UNION ALL
        SELECT 'GV.OC-04.1', 'GV.OC-04', 1,
               'È mantenuto un elenco aggiornato dei sistemi informativi e di rete rilevanti' FROM dual UNION ALL
        SELECT 'RS.MA-01.1', 'RS.MA-01', 1,
               'È definito, documentato e approvato dagli organi di amministrazione un piano di risposta agli incidenti' FROM dual
    ) r
    JOIN MisuraSicurezza m ON m.codice = r.misura
) s
ON (t.codice = s.codice AND t.versione_determinazione = '164179/2025')
WHEN NOT MATCHED THEN INSERT (codice, misura_id, numero_punto, testo)
VALUES (s.codice, s.misura_id, s.numero_punto, s.testo);

MERGE INTO MisuraApplicabilita t
USING (
    SELECT rm.id AS requisito_id, ts.id AS tipo_soggetto_id
    FROM RequisitoMisura rm CROSS JOIN TipoSoggettoNIS ts
    WHERE rm.codice IN ('GV.SC-04.1', 'ID.AM-01.1', 'ID.AM-04.1', 'GV.OC-04.1', 'RS.MA-01.1')
) s
ON (t.requisito_id = s.requisito_id AND t.tipo_soggetto_id = s.tipo_soggetto_id)
WHEN NOT MATCHED THEN INSERT (requisito_id, tipo_soggetto_id) VALUES (s.requisito_id, s.tipo_soggetto_id);
