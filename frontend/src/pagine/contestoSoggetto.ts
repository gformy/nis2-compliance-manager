import { useOutletContext } from 'react-router'
import type { Soggetto } from '../api/soggetti'

/** Dati del soggetto condivisi con le schede (anagrafica, asset, servizi). */
export interface ContestoSoggetto {
  soggetto: Soggetto
  modificabile: boolean
  /** Registrare invii ACN: solo referente NIS e amministratore. */
  inviabile: boolean
  amministratore: boolean
}

export const useContestoSoggetto = () => useOutletContext<ContestoSoggetto>()
