package it.gformy.nis2.incidenti;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import it.gformy.nis2.incidenti.ModelloIncidenti.Notifica;
import it.gformy.nis2.incidenti.ModelloIncidenti.Scadenza;
import it.gformy.nis2.incidenti.ModelloIncidenti.StatoScadenza;
import it.gformy.nis2.incidenti.ModelloIncidenti.TipoNotifica;

/**
 * Termini di notifica degli incidenti significativi (D.Lgs. 138/2024, art. 25, comma 5).
 *
 * <ul>
 *   <li>pre-notifica: entro 24 ore dalla data di evidenza;</li>
 *   <li>notifica: entro 72 ore dalla data di evidenza;</li>
 *   <li>relazione finale: entro un mese dalla trasmissione della notifica.</li>
 * </ul>
 * Il mese si conta sul calendario italiano (stesso giorno del mese successivo, ora locale).
 * Le relazioni intermedie (su richiesta del CSIRT) e mensili non hanno un termine fisso.
 */
public final class Scadenze {

    static final Duration PRE_NOTIFICA = Duration.ofHours(24);
    static final Duration NOTIFICA = Duration.ofHours(72);
    private static final ZoneId ITALIA = ZoneId.of("Europe/Rome");
    private static final EnumSet<StatoScadenza> APERTE =
            EnumSet.of(StatoScadenza.IN_TEMPO, StatoScadenza.IN_SCADENZA, StatoScadenza.SCADUTA);

    private Scadenze() {
    }

    public static List<Scadenza> calcola(Instant dataEvidenza, List<Notifica> notifiche, Instant adesso) {
        Optional<Instant> notifica = primaTrasmissione(notifiche, TipoNotifica.NOTIFICA);
        Instant terminePreNotifica = dataEvidenza.plus(PRE_NOTIFICA);
        Instant termineNotifica = dataEvidenza.plus(NOTIFICA);
        Instant termineFinale = notifica.map(n -> n.atZone(ITALIA).plusMonths(1).toInstant()).orElse(null);
        return List.of(
                scadenza(TipoNotifica.PRE_NOTIFICA, dataEvidenza, terminePreNotifica, notifiche, adesso),
                scadenza(TipoNotifica.NOTIFICA, dataEvidenza, termineNotifica, notifiche, adesso),
                scadenza(TipoNotifica.RELAZIONE_FINALE, notifica.orElse(null), termineFinale, notifiche, adesso));
    }

    /** La scadenza non rispettata piu' vicina (o gia' passata), se c'e'. */
    public static Optional<Scadenza> prossima(List<Scadenza> scadenze) {
        return scadenze.stream()
                .filter(s -> APERTE.contains(s.stato()))
                .min(Comparator.comparing(Scadenza::scadenza));
    }

    private static Scadenza scadenza(TipoNotifica tipo, Instant decorrenza, Instant termine,
                                     List<Notifica> notifiche, Instant adesso) {
        Optional<Instant> trasmessa = primaTrasmissione(notifiche, tipo);
        if (trasmessa.isPresent()) {
            boolean inRitardo = termine != null && trasmessa.get().isAfter(termine);
            return new Scadenza(tipo, termine,
                    inRitardo ? StatoScadenza.TRASMESSA_IN_RITARDO : StatoScadenza.TRASMESSA, trasmessa.get());
        }
        if (termine == null) {
            return new Scadenza(tipo, null, StatoScadenza.IN_ATTESA, null);
        }
        if (adesso.isAfter(termine)) {
            return new Scadenza(tipo, termine, StatoScadenza.SCADUTA, null);
        }
        Duration finestra = Duration.between(decorrenza, termine);
        Duration residuo = Duration.between(adesso, termine);
        boolean inScadenza = residuo.multipliedBy(4).compareTo(finestra) < 0;
        return new Scadenza(tipo, termine, inScadenza ? StatoScadenza.IN_SCADENZA : StatoScadenza.IN_TEMPO, null);
    }

    private static Optional<Instant> primaTrasmissione(List<Notifica> notifiche, TipoNotifica tipo) {
        return notifiche.stream()
                .filter(n -> n.tipo() == tipo)
                .map(Notifica::dataTrasmissione)
                .min(Comparator.naturalOrder());
    }
}
