-- =====================================================================
-- V6 - Catalogo dei codici CPV (Common Procurement Vocabulary)
--
-- Il portale ACN chiede, per ogni fornitore rilevante, i codici CPV delle
-- forniture. Qui c'e' un sottoinsieme del vocabolario CPV 2008 utile alle
-- forniture ICT e di rete, con il codice a 8 cifre come nei dati del PW
-- (senza la cifra di controllo). Il catalogo si estende con nuove migrazioni.
-- MERGE: i codici gia' presenti (es. caricati dai test del PW) non si duplicano.
-- =====================================================================

MERGE INTO CodiceCPV t
USING (
    SELECT '30200000' AS codice, 'Apparecchiature informatiche e forniture' AS descrizione FROM dual UNION ALL
    SELECT '32400000', 'Reti' FROM dual UNION ALL
    SELECT '32500000', 'Materiale per telecomunicazioni' FROM dual UNION ALL
    SELECT '48000000', 'Pacchetti software e sistemi di informazione' FROM dual UNION ALL
    SELECT '50312000', 'Manutenzione e riparazione di attrezzature informatiche' FROM dual UNION ALL
    SELECT '64200000', 'Servizi di telecomunicazione' FROM dual UNION ALL
    SELECT '65310000', 'Distribuzione di energia elettrica' FROM dual UNION ALL
    SELECT '72000000', 'Servizi IT: consulenza, sviluppo di software, Internet e supporto' FROM dual UNION ALL
    SELECT '72200000', 'Servizi di programmazione di software e di consulenza' FROM dual UNION ALL
    SELECT '72300000', 'Servizi di elaborazione dati' FROM dual UNION ALL
    SELECT '72317000', 'Servizi di immagazzinaggio dati' FROM dual UNION ALL
    SELECT '72400000', 'Servizi di Internet' FROM dual UNION ALL
    SELECT '72500000', 'Servizi informatici' FROM dual UNION ALL
    SELECT '72600000', 'Servizi di supporto informatico e di consulenza' FROM dual UNION ALL
    SELECT '72910000', 'Servizi di back-up informatico' FROM dual UNION ALL
    SELECT '79714000', 'Servizi di sorveglianza' FROM dual
) s
ON (t.codice = s.codice)
WHEN NOT MATCHED THEN INSERT (codice, descrizione) VALUES (s.codice, s.descrizione);
