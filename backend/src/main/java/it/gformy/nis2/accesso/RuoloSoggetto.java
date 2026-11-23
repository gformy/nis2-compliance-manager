package it.gformy.nis2.accesso;

/** Ruoli che un utente puo' avere su un singolo soggetto (vincolo CHECK di UtenteSoggetto). */
public enum RuoloSoggetto {

    /** Punto di contatto verso ACN: tutto sul proprio soggetto. */
    REFERENTE_NIS(true),
    /** Aggiorna inventario, fornitori, adempimenti e incidenti. */
    OPERATORE(true),
    /** Sola lettura, per audit e consulenti. */
    REVISORE(false);

    private final boolean puoModificare;

    RuoloSoggetto(boolean puoModificare) {
        this.puoModificare = puoModificare;
    }

    public boolean puoModificare() {
        return puoModificare;
    }
}
