package it.gformy.nis2.inventario;

import org.springframework.stereotype.Component;

import it.gformy.nis2.comune.Errori;
import it.gformy.nis2.inventario.ModelloInventario.StatoElemento;
import it.gformy.nis2.inventario.ModelloInventario.TipoElemento;

/**
 * Controlli comuni su asset e servizi.
 *
 * L'accesso al soggetto lo verifica gia' @PreAuthorize; qui si verifica che
 * l'elemento indicato nell'URL appartenga davvero a quel soggetto. Senza questo
 * controllo un utente potrebbe usare il proprio soggetto nell'URL e l'id di un
 * asset altrui (BOLA sulle risorse figlie).
 */
@Component
public class ControlloElementi {

    private final InventarioRepository repository;

    public ControlloElementi(InventarioRepository repository) {
        this.repository = repository;
    }

    public StatoElemento esistente(long soggettoId, long id, TipoElemento tipo) {
        return repository.statoElemento(soggettoId, id, tipo)
                .orElseThrow(() -> Errori.nonTrovato(nome(tipo) + " " + id));
    }

    public void attivo(long soggettoId, long id, TipoElemento tipo) {
        if (esistente(soggettoId, id, tipo) == StatoElemento.DISMESSO) {
            throw Errori.conflitto(nome(tipo) + " " + id + " e' dismesso e non si puo' modificare");
        }
    }

    private static String nome(TipoElemento tipo) {
        return tipo == TipoElemento.ASSET ? "Asset" : "Servizio";
    }
}
