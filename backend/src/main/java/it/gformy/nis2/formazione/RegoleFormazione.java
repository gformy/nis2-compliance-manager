package it.gformy.nis2.formazione;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import it.gformy.nis2.formazione.ModelloFormazione.Categoria;
import it.gformy.nis2.formazione.ModelloFormazione.Copertura;
import it.gformy.nis2.formazione.ModelloFormazione.Destinatari;
import it.gformy.nis2.formazione.ModelloFormazione.Esito;
import it.gformy.nis2.formazione.ModelloFormazione.Partecipazione;
import it.gformy.nis2.formazione.ModelloFormazione.StatoAttivita;

/**
 * Regole del piano di formazione.
 *
 * Una persona e' "in regola" se negli ultimi 12 mesi ha completato (verifica superata o
 * non prevista) un'attivita' rivolta alla sua categoria. Per i ruoli specializzati conta
 * solo la formazione dedicata (PR.AT-02). La finestra di 12 mesi e' una scelta
 * dell'applicazione, coerente con il riesame annuale del piano: la norma non fissa una
 * frequenza.
 */
public final class RegoleFormazione {

    static final int FINESTRA_MESI = 12;

    private RegoleFormazione() {
    }

    public static boolean destinatariCompatibili(Destinatari destinatari, Categoria categoria) {
        return switch (destinatari) {
            case TUTTI -> true;
            case ORGANO -> categoria == Categoria.ORGANO;
            case RUOLO_SPECIALIZZATO -> categoria == Categoria.RUOLO_SPECIALIZZATO;
        };
    }

    /** La formazione che conta per la copertura di una categoria. */
    static boolean contaPer(Destinatari destinatari, Categoria categoria) {
        return categoria == Categoria.RUOLO_SPECIALIZZATO
                ? destinatari == Destinatari.RUOLO_SPECIALIZZATO
                : destinatari == Destinatari.TUTTI || destinatari.name().equals(categoria.name());
    }

    /** Se l'attivita' prevede una verifica, l'esito e' superata o non superata; altrimenti "non prevista". */
    public static Optional<String> esitoNonCoerente(String modalitaVerifica, Esito esito) {
        boolean conVerifica = modalitaVerifica != null && !modalitaVerifica.isBlank();
        if (conVerifica && esito == Esito.NON_PREVISTA) {
            return Optional.of("l'attivita' prevede una verifica: indicare se e' stata superata");
        }
        if (!conVerifica && esito != Esito.NON_PREVISTA) {
            return Optional.of("l'attivita' non prevede verifiche: l'esito e' NON_PREVISTA");
        }
        return Optional.empty();
    }

    /** Ultima formazione valida per la categoria nella finestra di 12 mesi. */
    public static Optional<LocalDate> ultimaFormazioneValida(Categoria categoria, List<Partecipazione> partecipazioni,
                                                             LocalDate oggi) {
        LocalDate inizio = oggi.minusMonths(FINESTRA_MESI);
        return partecipazioni.stream()
                .filter(p -> p.esito() != Esito.NON_SUPERATA)
                .filter(p -> contaPer(p.destinatari(), categoria))
                .map(Partecipazione::dataSvolgimento)
                .filter(d -> d.isAfter(inizio) && !d.isAfter(oggi))
                .max(LocalDate::compareTo);
    }

    public static StatoAttivita statoAttivita(LocalDate dataPrevista, int partecipanti, LocalDate oggi) {
        if (partecipanti > 0) {
            return StatoAttivita.SVOLTA;
        }
        return dataPrevista.isBefore(oggi) ? StatoAttivita.DA_REGISTRARE : StatoAttivita.PIANIFICATA;
    }

    public record PersonaValutata(Categoria categoria, boolean attiva, boolean inRegola) {
    }

    public static List<Copertura> copertura(List<PersonaValutata> persone) {
        List<Copertura> copertura = new ArrayList<>();
        for (Categoria categoria : Categoria.values()) {
            List<PersonaValutata> attive = persone.stream()
                    .filter(p -> p.attiva() && p.categoria() == categoria).toList();
            int formate = (int) attive.stream().filter(PersonaValutata::inRegola).count();
            double percentuale = attive.isEmpty() ? 0 : Math.round(formate * 1000.0 / attive.size()) / 10.0;
            copertura.add(new Copertura(categoria, attive.size(), formate, percentuale));
        }
        return copertura;
    }
}
