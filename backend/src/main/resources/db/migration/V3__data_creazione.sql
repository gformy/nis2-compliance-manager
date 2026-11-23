-- =====================================================================
-- V3 - Data di creazione sulle tabelle storicizzate senza data di origine
--
-- Nel PW sette tabelle storicizzate non hanno una data di creazione:
-- la prima versione nello storico (e la versione corrente nelle viste
-- *_Versioni) parte da TIMESTAMP '0001-01-01'. Una ricostruzione AS OF
-- precedente all'inserimento mostrerebbe quindi righe che a quella data
-- non esistevano ancora (es. un fornitore aggiunto dopo l'invio ad ACN).
--
-- Qui si aggiunge data_creazione e la si usa nelle due viste che
-- alimentano le ricostruzioni AS OF. I trigger la usano da V4.
-- Le righe gia' presenti ricevono la data di esecuzione della migrazione.
-- =====================================================================

ALTER TABLE Fornitore                          ADD (data_creazione TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL);
ALTER TABLE DocumentoGovernance                ADD (data_creazione TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL);
ALTER TABLE StatoAdempimentoRequisito          ADD (data_creazione TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL);
ALTER TABLE AccordoCondivisioneInformazioni    ADD (data_creazione TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL);
ALTER TABLE Dipendenza                         ADD (data_creazione TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL);
ALTER TABLE AssetServizio                      ADD (data_creazione TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL);
ALTER TABLE DocumentoGovernanceElementoCritico ADD (data_creazione TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL);

CREATE OR REPLACE VIEW Dipendenza_Versioni AS
SELECT dipendenza_id, versione, criterio_rilevanza, tipologia_fornitura, stato, valido_da, valido_a
FROM DipendenzaStorico
UNION ALL
SELECT d.id, NVL((SELECT MAX(versione)+1 FROM DipendenzaStorico WHERE dipendenza_id = d.id), 1),
       d.criterio_rilevanza, d.tipologia_fornitura, d.stato,
       NVL((SELECT MAX(valido_a) FROM DipendenzaStorico WHERE dipendenza_id = d.id), d.data_creazione),
       NULL
FROM Dipendenza d;

CREATE OR REPLACE VIEW StatoAdempimentoRequisito_Versioni AS
SELECT stato_adempimento_id, versione, stato, data_valutazione, valido_da, valido_a
FROM StatoAdempimentoRequisitoStorico
UNION ALL
SELECT sar.id, NVL((SELECT MAX(versione)+1 FROM StatoAdempimentoRequisitoStorico WHERE stato_adempimento_id = sar.id), 1),
       sar.stato, sar.data_valutazione,
       NVL((SELECT MAX(valido_a) FROM StatoAdempimentoRequisitoStorico WHERE stato_adempimento_id = sar.id), sar.data_creazione),
       NULL
FROM StatoAdempimentoRequisito sar;
