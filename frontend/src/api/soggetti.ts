import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApi } from './client'

/** Tipi uguali ai record Java di ModelloSoggetto. */
export type TipoSoggetto = 'ESSENZIALE' | 'IMPORTANTE'

export interface SoggettoSintesi {
  id: number
  codiceFiscale: string
  denominazione: string
  tipoSoggetto: TipoSoggetto
  organizzazioneId: number
  organizzazione: string
}

export interface Permessi {
  modificare: boolean
  inviare: boolean
  gestireAccessi: boolean
}

export interface DatiSoggetto {
  denominazione: string
  indirizzoSedeLegale: string
  pec: string
  emailFunzionale: string | null
  telefono: string | null
  fatturato: number | null
  bilancio: number | null
  numeroDipendenti: number | null
  tipoSoggetto: TipoSoggetto
}

export interface Soggetto extends DatiSoggetto {
  id: number
  codiceFiscale: string
  dataCreazione: string
}

export interface VersioneSoggetto extends Omit<DatiSoggetto, 'tipoSoggetto'> {
  versione: number
  validoDa: string
  validoA: string | null
  modificataDa: string | null
}

export function useSoggetti() {
  const api = useApi()
  return useQuery({
    queryKey: ['soggetti'],
    queryFn: () => api<SoggettoSintesi[]>('/api/v1/soggetti'),
  })
}

export function useSoggetto(id: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['soggetto', id],
    queryFn: () => api<Soggetto>(`/api/v1/soggetti/${id}`),
  })
}

export function useVersioniSoggetto(id: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['versioni', id],
    queryFn: () => api<VersioneSoggetto[]>(`/api/v1/soggetti/${id}/versioni`),
  })
}

export function useAggiornaSoggetto(id: number) {
  const api = useApi()
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (dati: DatiSoggetto) =>
      api<Soggetto>(`/api/v1/soggetti/${id}`, { method: 'PUT', body: JSON.stringify(dati) }),
    onSuccess: (soggetto) => {
      cache.setQueryData(['soggetto', id], soggetto)
      void cache.invalidateQueries({ queryKey: ['versioni', id] })
      void cache.invalidateQueries({ queryKey: ['soggetti'] })
    },
  })
}

/** Permessi calcolati dal backend (assegnazione, organizzazione, sospensione). */
export function usePermessi(id: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['soggetto', id, 'permessi'],
    queryFn: () => api<Permessi>(`/api/v1/soggetti/${id}/permessi`),
  })
}

export function useCreaSoggetto() {
  const api = useApi()
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (nuovo: { codiceFiscale: string; dati: DatiSoggetto; organizzazioneId: number | null }) =>
      api<Soggetto>('/api/v1/soggetti', { method: 'POST', body: JSON.stringify(nuovo) }),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ['soggetti'] })
      void cache.invalidateQueries({ queryKey: ['organizzazioni'] })
    },
  })
}
