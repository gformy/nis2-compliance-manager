package it.gformy.nis2.invii;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import it.gformy.nis2.invii.ModelloInvii.Anagrafica;
import it.gformy.nis2.invii.ModelloInvii.AdempimentoProfilo;
import it.gformy.nis2.invii.ModelloInvii.CampoModificato;
import it.gformy.nis2.invii.ModelloInvii.Confronto;
import it.gformy.nis2.invii.ModelloInvii.Differenze;
import it.gformy.nis2.invii.ModelloInvii.ElementoProfilo;
import it.gformy.nis2.invii.ModelloInvii.FornitoreProfilo;
import it.gformy.nis2.invii.ModelloInvii.Modifica;
import it.gformy.nis2.invii.ModelloInvii.Profilo;

/** Differenze tra due profili dichiarati: cosa e' cambiato da un invio all'altro. */
public final class Confronti {

    private Confronti() {
    }

    public static Confronto confronta(Profilo da, Profilo a) {
        return new Confronto(da.invio(), a.invio(),
                anagrafica(da.anagrafica(), a.anagrafica()),
                differenze(da.elementi(), a.elementi(), ElementoProfilo::id),
                differenze(da.fornitori(), a.fornitori(), FornitoreProfilo::dipendenzaId),
                differenze(da.adempimenti(), a.adempimenti(), AdempimentoProfilo::requisito));
    }

    /** Aggiunti, rimossi e modificati, riconoscendo gli elementi dalla chiave. */
    static <T, K> Differenze<T> differenze(List<T> prima, List<T> dopo, Function<T, K> chiave) {
        Map<K, T> precedenti = new LinkedHashMap<>();
        prima.forEach(p -> precedenti.put(chiave.apply(p), p));
        List<T> aggiunti = new ArrayList<>();
        List<Modifica<T>> modificati = new ArrayList<>();
        for (T d : dopo) {
            T p = precedenti.remove(chiave.apply(d));
            if (p == null) {
                aggiunti.add(d);
            } else if (!p.equals(d)) {
                modificati.add(new Modifica<>(p, d));
            }
        }
        return new Differenze<>(aggiunti, List.copyOf(precedenti.values()), modificati);
    }

    private static List<CampoModificato> anagrafica(Anagrafica da, Anagrafica a) {
        List<CampoModificato> campi = new ArrayList<>();
        confronta(campi, "denominazione", da.denominazione(), a.denominazione());
        confronta(campi, "indirizzoSedeLegale", da.indirizzoSedeLegale(), a.indirizzoSedeLegale());
        confronta(campi, "pec", da.pec(), a.pec());
        confronta(campi, "fatturato", da.fatturato(), a.fatturato());
        confronta(campi, "numeroDipendenti", da.numeroDipendenti(), a.numeroDipendenti());
        return campi;
    }

    private static void confronta(List<CampoModificato> campi, String nome, Object prima, Object dopo) {
        boolean uguali = prima instanceof java.math.BigDecimal p && dopo instanceof java.math.BigDecimal d
                ? p.compareTo(d) == 0
                : Objects.equals(prima, dopo);
        if (!uguali) {
            campi.add(new CampoModificato(nome, prima, dopo));
        }
    }
}
