package it.gformy.nis2.governance;

import java.time.LocalDate;
import java.util.List;

import it.gformy.nis2.governance.ModelloGovernance.Riepilogo;
import it.gformy.nis2.governance.ModelloGovernance.StatoDocumento;
import it.gformy.nis2.governance.ModelloGovernance.Voce;

/** Stato di un documento rispetto ad approvazione e riesame periodico. */
public final class StatoDocumenti {

    static final int PREAVVISO_GIORNI = 30;

    private StatoDocumenti() {
    }

    /** Data entro cui va fatto il prossimo riesame; null se il documento non e' approvato. */
    public static LocalDate prossimoRiesame(LocalDate approvazione, LocalDate ultimoRiesame, int periodicitaMesi) {
        if (approvazione == null) {
            return null;
        }
        LocalDate base = ultimoRiesame != null && ultimoRiesame.isAfter(approvazione) ? ultimoRiesame : approvazione;
        return base.plusMonths(periodicitaMesi);
    }

    public static StatoDocumento stato(boolean esiste, LocalDate prossimoRiesame, LocalDate oggi) {
        if (!esiste) {
            return StatoDocumento.MANCANTE;
        }
        if (prossimoRiesame == null) {
            return StatoDocumento.DA_APPROVARE;
        }
        if (oggi.isAfter(prossimoRiesame)) {
            return StatoDocumento.RIESAME_SCADUTO;
        }
        if (!prossimoRiesame.isAfter(oggi.plusDays(PREAVVISO_GIORNI))) {
            return StatoDocumento.RIESAME_IN_SCADENZA;
        }
        return StatoDocumento.VALIDO;
    }

    /** "In regola" = approvato e con riesame non scaduto (anche se in scadenza). */
    public static Riepilogo riepiloga(List<Voce> voci) {
        int validi = conta(voci, StatoDocumento.VALIDO);
        int inScadenza = conta(voci, StatoDocumento.RIESAME_IN_SCADENZA);
        int totale = voci.size();
        double percentuale = totale == 0 ? 0 : Math.round((validi + inScadenza) * 1000.0 / totale) / 10.0;
        return new Riepilogo(totale, validi, inScadenza, conta(voci, StatoDocumento.RIESAME_SCADUTO),
                conta(voci, StatoDocumento.DA_APPROVARE), conta(voci, StatoDocumento.MANCANTE), percentuale);
    }

    private static int conta(List<Voce> voci, StatoDocumento stato) {
        return (int) voci.stream().filter(v -> v.stato() == stato).count();
    }
}
