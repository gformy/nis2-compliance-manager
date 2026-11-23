package it.gformy.nis2.accesso;

/** Ruoli che un utente puo' avere su un singolo soggetto (vincolo CHECK di UtenteSoggetto). */
public enum RuoloSoggetto {

    /** Punto di contatto verso ACN: tutto sul proprio soggetto. */
    REFERENTE_NIS(true, true),
    /** Aggiorna inventario, fornitori, adempimenti e incidenti. */
    OPERATORE(true, false),
    /** Sola lettura, per audit e consulenti. */
    REVISORE(false, false);

    private final boolean puoModificare;
    private final boolean puoInviare;

    RuoloSoggetto(boolean puoModificare, boolean puoInviare) {
        this.puoModificare = puoModificare;
        this.puoInviare = puoInviare;
    }

    /** Registrare un invio alla piattaforma ACN spetta al punto di contatto. */
    public boolean puoInviare() {
        return puoInviare;
    }

    public boolean puoModificare() {
        return puoModificare;
    }
}
