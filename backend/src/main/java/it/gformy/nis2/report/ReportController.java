package it.gformy.nis2.report;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import it.gformy.nis2.accesso.AccessoSoggetti;
import it.gformy.nis2.anagrafica.ModelloSoggetto.Soggetto;
import it.gformy.nis2.anagrafica.SoggettoRepository;
import it.gformy.nis2.audit.TransazioneAuditata;
import it.gformy.nis2.comune.Errori;
import it.gformy.nis2.conformita.ConformitaController;
import it.gformy.nis2.conformita.ModelloConformita.Riepilogo;
import it.gformy.nis2.formazione.FormazioneController;
import it.gformy.nis2.formazione.ModelloFormazione.Formazione;
import it.gformy.nis2.fornitori.DipendenzaController;
import it.gformy.nis2.fornitori.ModelloFornitori.FornitoreRilevante;
import it.gformy.nis2.governance.GovernanceController;
import it.gformy.nis2.governance.ModelloGovernance.Governance;
import it.gformy.nis2.incidenti.IncidenteController;
import it.gformy.nis2.incidenti.ModelloIncidenti.SintesiIncidente;
import it.gformy.nis2.inventario.AssetController;
import it.gformy.nis2.inventario.ModelloInventario.Asset;
import it.gformy.nis2.inventario.ModelloInventario.Servizio;
import it.gformy.nis2.inventario.ModelloInventario.StatoElemento;
import it.gformy.nis2.inventario.ServizioController;
import it.gformy.nis2.invii.InvioController;
import it.gformy.nis2.invii.ModelloInvii.Invio;
import it.gformy.nis2.organizzazioni.ModelloOrganizzazioni.Organizzazione;
import it.gformy.nis2.organizzazioni.OrganizzazioneRepository;

/**
 * Report PDF dello stato di conformita' di un soggetto, con il logo dell'organizzazione
 * che lo gestisce (per esempio lo studio che lo consegna al cliente).
 *
 * I dati arrivano dalle stesse letture delle API (stessi controlli di accesso e stessi
 * stati calcolati): il PDF dice esattamente quello che mostra l'applicazione.
 * Ogni report generato resta in LogEstrazione con la sua impronta SHA-256.
 */
@RestController
public class ReportController {

    private static final ZoneId ITALIA = ZoneId.of("Europe/Rome");
    private static final DateTimeFormatter GIORNO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter GIORNO_ORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SoggettoRepository soggetti;
    private final OrganizzazioneRepository organizzazioni;
    private final ConformitaController conformita;
    private final GovernanceController governance;
    private final FormazioneController formazione;
    private final IncidenteController incidenti;
    private final AssetController asset;
    private final ServizioController servizi;
    private final DipendenzaController dipendenze;
    private final InvioController invii;
    private final AccessoSoggetti accesso;
    private final TransazioneAuditata transazioneAuditata;
    private final JdbcClient jdbc;
    private final Clock orologio;

    public ReportController(SoggettoRepository soggetti, OrganizzazioneRepository organizzazioni,
                            ConformitaController conformita, GovernanceController governance,
                            FormazioneController formazione, IncidenteController incidenti, AssetController asset,
                            ServizioController servizi, DipendenzaController dipendenze, InvioController invii,
                            AccessoSoggetti accesso, TransazioneAuditata transazioneAuditata, JdbcClient jdbc,
                            Clock orologio) {
        this.soggetti = soggetti;
        this.organizzazioni = organizzazioni;
        this.conformita = conformita;
        this.governance = governance;
        this.formazione = formazione;
        this.incidenti = incidenti;
        this.asset = asset;
        this.servizi = servizi;
        this.dipendenze = dipendenze;
        this.invii = invii;
        this.accesso = accesso;
        this.transazioneAuditata = transazioneAuditata;
        this.jdbc = jdbc;
        this.orologio = orologio;
    }

    @GetMapping("/api/v1/soggetti/{soggettoId}/report.pdf")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public ResponseEntity<byte[]> report(@PathVariable long soggettoId) {
        Soggetto soggetto = soggetti.trova(soggettoId).orElseThrow(() -> Errori.nonTrovato("Soggetto " + soggettoId));
        Organizzazione organizzazione = organizzazioni.appartenenza(soggettoId)
                .flatMap(a -> organizzazioni.trova(a.organizzazioneId())).orElse(null);
        byte[] logo = organizzazione == null ? null
                : organizzazioni.logo(organizzazione.id()).map(l -> l.contenuto()).orElse(null);
        Instant adesso = orologio.instant();

        byte[] pdf = componi(soggetto, organizzazione, logo, adesso);
        String impronta = sha256(pdf);
        transazioneAuditata.esegui(() -> {
            jdbc.sql("""
                    INSERT INTO LogEstrazione (registro_tabella_id, record_id, utente, tipo_operazione, snapshot_dati)
                    VALUES ((SELECT id FROM RegistroTabelle WHERE nome_tabella = 'SoggettoNIS'), ?,
                            fn_utente_corrente, 'CONSULTAZIONE', ?)
                    """)
                    .params(soggettoId, "report_pdf;sha256=" + impronta)
                    .update();
            return null;
        });

        String nome = "report-nis2-" + soggetto.codiceFiscale() + "-" + LocalDate.ofInstant(adesso, ITALIA) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Report-SHA256", impronta)
                .cacheControl(CacheControl.noStore())
                .body(pdf);
    }

    private byte[] componi(Soggetto s, Organizzazione organizzazione, byte[] logo, Instant adesso) {
        long id = s.id();
        Riepilogo adempimenti = conformita.riepilogo(id);
        Governance documenti = governance.documenti(id);
        Formazione corsi = formazione.formazione(id);
        List<SintesiIncidente> elencoIncidenti = incidenti.elenco(id);
        List<Asset> elencoAsset = asset.elenco(id).stream().filter(a -> a.stato() == StatoElemento.ATTIVO).toList();
        List<Servizio> elencoServizi = servizi.elenco(id).stream()
                .filter(v -> v.stato() == StatoElemento.ATTIVO).toList();
        List<FornitoreRilevante> fornitori = dipendenze.fornitoriRilevanti(id);
        List<Invio> elencoInvii = invii.elenco(id);

        String quando = GIORNO_ORA.format(adesso.atZone(ITALIA));
        String gestore = organizzazione == null ? "" : organizzazione.nome();
        try (DocumentoPdf pdf = new DocumentoPdf("Report NIS2 - " + s.denominazione(), gestore,
                "Report NIS2 di " + s.denominazione() + " · generato il " + quando + " da " + accesso.utente())) {

            if (logo != null) {
                pdf.logo(logo, 40);
            } else if (!gestore.isEmpty()) {
                pdf.nota(gestore);
            }
            pdf.titolo("Stato di conformità NIS2");
            pdf.paragrafo(s.denominazione() + " · C.F. " + s.codiceFiscale() + " · soggetto "
                    + s.tipoSoggetto().name().toLowerCase(Locale.ROOT));
            pdf.nota("Dati al " + quando + ". Riferimenti: D.Lgs. 138/2024 e determinazione ACN sulle specifiche di base.");
            pdf.spazio(8);

            long incidentiAperti = elencoIncidenti.stream().filter(i -> i.stato().name().equals("APERTO")).count();
            long scadenzeAperte = elencoIncidenti.stream().filter(i -> i.prossimaScadenza() != null).count();
            pdf.indicatori(List.of(
                    new String[] {"Attuazione delle misure di sicurezza", percentuale(adempimenti.percentualeAttuazione())},
                    new String[] {"Politiche e piani in regola", percentuale(documenti.riepilogo().percentualeInRegola())},
                    new String[] {"Incidenti aperti / con scadenze da rispettare", incidentiAperti + " / " + scadenzeAperte},
                    new String[] {"Asset e servizi attivi", elencoAsset.size() + " asset · " + elencoServizi.size() + " servizi"},
                    new String[] {"Fornitori rilevanti", String.valueOf(fornitori.size())},
                    new String[] {"Ultimo invio ACN", elencoInvii.isEmpty() ? "nessuno"
                            : GIORNO.format(elencoInvii.get(0).dataInvio().atZone(ITALIA))}));

            pdf.sezione("Misure di sicurezza");
            pdf.tabella(List.of("Misura", "Categoria", "Requisiti", "Attuazione"), new float[] {1.2f, 3.5f, 2.4f, 1.1f},
                    adempimenti.misure().stream().map(m -> List.of(m.codice(), m.categoria(),
                            m.conteggi().attuati() + " attuati, " + m.conteggi().parziali() + " parziali, "
                                    + m.conteggi().nonAttuati() + " non attuati",
                            percentuale(m.percentualeAttuazione()))).toList());
            pdf.nota("Un requisito parzialmente attuato vale metà; i requisiti non applicabili non entrano nel calcolo.");

            pdf.sezione("Politiche e piani (GV.PO-01, GV.PO-02)");
            var r = documenti.riepilogo();
            pdf.paragrafo(r.validi() + " validi, " + r.inScadenza() + " con riesame in scadenza, " + r.scaduti()
                    + " con riesame scaduto, " + r.daApprovare() + " da approvare, " + r.mancanti() + " mancanti su "
                    + r.totale() + ".");
            pdf.tabella(List.of("Documento", "Stato", "Approvato", "Prossimo riesame"), new float[] {4.2f, 1.6f, 2.2f, 1.4f},
                    documenti.voci().stream().filter(v -> v.documento() != null).map(v -> List.of(
                            v.nome(), etichetta(v.stato().name()),
                            v.documento().dataApprovazione() == null ? "—"
                                    : GIORNO.format(v.documento().dataApprovazione())
                                    + (v.documento().approvatoDa() == null ? "" : " · " + v.documento().approvatoDa().nome()),
                            v.documento().prossimoRiesame() == null ? "—" : GIORNO.format(v.documento().prossimoRiesame())))
                            .toList());

            pdf.sezione("Formazione (PR.AT-01, PR.AT-02)");
            pdf.tabella(List.of("Categoria", "Persone", "Formate negli ultimi 12 mesi", "Copertura"),
                    new float[] {3, 1.2f, 2.5f, 1.3f},
                    corsi.riepilogo().copertura().stream().map(c -> List.of(etichetta(c.categoria().name()),
                            String.valueOf(c.persone()), String.valueOf(c.formate()),
                            c.persone() == 0 ? "—" : percentuale(c.percentuale()))).toList());
            pdf.nota("Per i ruoli specializzati conta solo la formazione dedicata. Registro: " + corsi.registro().size()
                    + " partecipazioni registrate.");

            pdf.sezione("Incidenti significativi");
            pdf.tabella(List.of("Incidente", "Tipo", "Gravità", "Evidenza", "Stato", "Prossima scadenza"),
                    new float[] {3.2f, 0.8f, 1, 1.5f, 1, 2.2f},
                    elencoIncidenti.stream().map(i -> List.of(i.titolo(), i.tipoIncidente(),
                            i.gravita() == null ? "—" : etichetta(i.gravita().name()),
                            GIORNO_ORA.format(i.dataEvidenza().atZone(ITALIA)), etichetta(i.stato().name()),
                            i.prossimaScadenza() == null ? "nessuna"
                                    : etichetta(i.prossimaScadenza().tipo().name()) + " entro il "
                                    + GIORNO_ORA.format(i.prossimaScadenza().scadenza().atZone(ITALIA)) + " ("
                                    + etichetta(i.prossimaScadenza().stato().name()) + ")")).toList());

            pdf.sezione("Inventario degli elementi critici");
            pdf.tabella(List.of("Asset", "Tipo", "Rilevante"), new float[] {5, 2, 1},
                    elencoAsset.stream().map(a -> List.of(a.nome(), etichetta(a.tipoAsset().name()),
                            a.rilevante() ? "sì" : "no")).toList());
            pdf.tabella(List.of("Servizio", "Categoria di rilevanza"), new float[] {5, 3},
                    elencoServizi.stream().map(v -> List.of(v.nome(), testoONulla(v.categoriaRilevanza()))).toList());

            pdf.sezione("Fornitori rilevanti per il profilo ACN");
            pdf.tabella(List.of("Fornitore", "Codice fiscale", "Paese", "CPV", "Criterio"),
                    new float[] {3, 1.6f, 1.2f, 2.4f, 0.8f},
                    fornitori.stream().map(f -> List.of(f.denominazione(), f.codiceFiscale(),
                            testoONulla(f.paeseSedeLegale()), String.join(", ", f.codiciCpv()),
                            String.join(", ", f.criteriRilevanza().stream().map(String::valueOf).toList()))).toList());

            pdf.sezione("Invii alla piattaforma ACN");
            pdf.tabella(List.of("Data", "Tipo", "Anno", "Registrato da"), new float[] {2, 3, 1, 2},
                    elencoInvii.stream().map(i -> List.of(GIORNO_ORA.format(i.dataInvio().atZone(ITALIA)),
                            etichetta(i.tipo().name()), String.valueOf(i.periodoRiferimento()), i.registratoDa()))
                            .toList());

            pdf.spazio(10);
            pdf.nota("Documento generato automaticamente da NIS2 Compliance Manager. L'impronta SHA-256 del file è "
                    + "registrata nel registro delle estrazioni e permette di verificare che non sia stato modificato.");
            return pdf.chiudiEOttieni();
        }
    }

    private static final Map<String, String> ETICHETTE = Map.ofEntries(
            Map.entry("VALIDO", "Valido"), Map.entry("DA_APPROVARE", "Da approvare"),
            Map.entry("RIESAME_IN_SCADENZA", "Riesame in scadenza"), Map.entry("RIESAME_SCADUTO", "Riesame scaduto"),
            Map.entry("ORGANO", "Organi di amministrazione"), Map.entry("RUOLO_SPECIALIZZATO", "Ruoli specializzati"),
            Map.entry("PERSONALE", "Personale"), Map.entry("APERTO", "Aperto"), Map.entry("CHIUSO", "Chiuso"),
            Map.entry("PRE_NOTIFICA", "Pre-notifica"), Map.entry("NOTIFICA", "Notifica"),
            Map.entry("RELAZIONE_FINALE", "Relazione finale"), Map.entry("IN_TEMPO", "in tempo"),
            Map.entry("IN_SCADENZA", "in scadenza"), Map.entry("SCADUTA", "scaduta"),
            Map.entry("FLUSSO_RETE", "Flusso di rete"), Map.entry("SERVIZIO_FORNITORE", "Servizio di fornitore"),
            Map.entry("AGGIORNAMENTO_ANNUALE", "Aggiornamento annuale"),
            Map.entry("AGGIORNAMENTO_CONTINUO", "Aggiornamento continuo"));

    /** Etichetta leggibile; per i valori senza traduzione: "VALORE_ENUM" -> "Valore enum". */
    static String etichetta(String valore) {
        String nota = ETICHETTE.get(valore);
        if (nota != null) {
            return nota;
        }
        String minuscolo = valore.replace('_', ' ').toLowerCase(Locale.ITALIAN);
        return Character.toUpperCase(minuscolo.charAt(0)) + minuscolo.substring(1);
    }

    private static String percentuale(double valore) {
        return String.format(Locale.ITALIAN, "%.1f%%", valore);
    }

    private static String testoONulla(String valore) {
        return valore == null || valore.isBlank() ? "—" : valore;
    }

    private static String sha256(byte[] contenuto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenuto));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
