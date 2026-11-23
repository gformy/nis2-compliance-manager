import type { DatiFornitore } from '../../api/fornitori'

export interface ValoriFornitore extends DatiFornitore {
  codiceFiscale: string
}

/** Controlli lato client uguali a quelli del backend (NuovoFornitore e DatiFornitore). */
export function erroriFornitore(valori: ValoriFornitore, nuovo: boolean): Partial<Record<keyof ValoriFornitore, string>> {
  const errori: Partial<Record<keyof ValoriFornitore, string>> = {}
  if (!valori.denominazione.trim()) errori.denominazione = 'Obbligatoria'
  if (!/^[A-Z]{2}$/.test(valori.paeseSedeLegale)) errori.paeseSedeLegale = 'Codice ISO di due lettere, es. IT'
  if (nuovo) {
    if (!/^[A-Z0-9]{2,16}$/.test(valori.codiceFiscale)) {
      errori.codiceFiscale = 'Da 2 a 16 lettere maiuscole o cifre'
    } else if (valori.paeseSedeLegale === 'IT' && !/^([0-9]{11}|[A-Z0-9]{16})$/.test(valori.codiceFiscale)) {
      errori.codiceFiscale = 'Per un fornitore italiano: partita IVA di 11 cifre o codice fiscale di 16 caratteri'
    }
  }
  return errori
}
