import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApi } from './client'

export type CriterioRilevanza = 'ICT' | 'NON_FUNGIBILE'
export type StatoDipendenza = 'ATTIVA' | 'CESSATA'

export const etichetteCriterio: Record<CriterioRilevanza, string> = {
  ICT: '1 · Fornitura ICT',
  NON_FUNGIBILE: '2 · Fornitura non fungibile',
}

export interface CodiceCpv {
  codice: string
  descrizione: string
}

export interface Fornitore {
  id: number
  denominazione: string
  codiceFiscale: string
  paeseSedeLegale: string
  cpv: CodiceCpv[]
  dataCreazione: string
}

export interface DatiFornitore {
  denominazione: string
  paeseSedeLegale: string
  cpv: string[]
}

export interface VersioneFornitore {
  versione: number
  denominazione: string
  paeseSedeLegale: string
  validoDa: string
  validoA: string | null
  modificataDa: string | null
}

export interface Dipendenza {
  id: number
  fornitoreId: number
  fornitoreDenominazione: string
  elementoCriticoId: number
  elementoNome: string
  elementoTipo: 'ASSET' | 'SERVIZIO'
  criterioRilevanza: CriterioRilevanza
  tipologiaFornitura: string | null
  referenteContatto: string | null
  stato: StatoDipendenza
}

export interface NuovaDipendenza {
  fornitoreId: number
  elementoCriticoId: number
  criterioRilevanza: CriterioRilevanza
  tipologiaFornitura: string | null
  referenteContatto: string | null
}

export interface DatiDipendenza {
  criterioRilevanza: CriterioRilevanza
  tipologiaFornitura: string | null
  referenteContatto: string | null
  stato: StatoDipendenza
}

export interface VersioneDipendenza {
  versione: number
  criterioRilevanza: CriterioRilevanza
  tipologiaFornitura: string | null
  stato: StatoDipendenza
  validoDa: string
  validoA: string | null
  modificataDa: string | null
}

/** Una riga del profilo ACN: i cinque campi dell'art. 18. */
export interface FornitoreRilevante {
  denominazione: string
  codiceFiscale: string
  paeseSedeLegale: string
  codiciCpv: string[]
  criteriRilevanza: number[]
}

// ------------------------------------------------------------ anagrafica fornitori

export function useCercaFornitori(testo: string) {
  const api = useApi()
  return useQuery({
    queryKey: ['fornitori', 'ricerca', testo],
    queryFn: () => api<Fornitore[]>(`/api/v1/fornitori?q=${encodeURIComponent(testo)}`),
    placeholderData: keepPreviousData,
  })
}

export function useFornitore(id: number) {
  const api = useApi()
  return useQuery({ queryKey: ['fornitori', id], queryFn: () => api<Fornitore>(`/api/v1/fornitori/${id}`) })
}

export function useVersioniFornitore(id: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['fornitori', id, 'versioni'],
    queryFn: () => api<VersioneFornitore[]>(`/api/v1/fornitori/${id}/versioni`),
  })
}

export function useCatalogoCpv() {
  const api = useApi()
  return useQuery({ queryKey: ['cpv'], queryFn: () => api<CodiceCpv[]>('/api/v1/cpv'), staleTime: Infinity })
}

export function useSalvaFornitore(id?: number) {
  const api = useApi()
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (dati: { codiceFiscale?: string; dati: DatiFornitore }) =>
      id === undefined
        ? api<Fornitore>('/api/v1/fornitori', { method: 'POST', body: JSON.stringify(dati) })
        : api<Fornitore>(`/api/v1/fornitori/${id}`, { method: 'PUT', body: JSON.stringify(dati.dati) }),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['fornitori'] }),
  })
}

// ------------------------------------------------------------ dipendenze del soggetto

export function useDipendenze(soggettoId: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['dipendenze', soggettoId],
    queryFn: () => api<Dipendenza[]>(`/api/v1/soggetti/${soggettoId}/dipendenze`),
  })
}

export function useFornitoriRilevanti(soggettoId: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['dipendenze', soggettoId, 'rilevanti'],
    queryFn: () => api<FornitoreRilevante[]>(`/api/v1/soggetti/${soggettoId}/fornitori-rilevanti`),
  })
}

export function useVersioniDipendenza(soggettoId: number, id: number | null) {
  const api = useApi()
  return useQuery({
    queryKey: ['dipendenze', soggettoId, id, 'versioni'],
    queryFn: () => api<VersioneDipendenza[]>(`/api/v1/soggetti/${soggettoId}/dipendenze/${id}/versioni`),
    enabled: id !== null,
  })
}

export function useCreaDipendenza(soggettoId: number) {
  const api = useApi()
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (nuova: NuovaDipendenza) =>
      api<Dipendenza>(`/api/v1/soggetti/${soggettoId}/dipendenze`, { method: 'POST', body: JSON.stringify(nuova) }),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['dipendenze', soggettoId] }),
  })
}

export function useAggiornaDipendenza(soggettoId: number) {
  const api = useApi()
  const cache = useQueryClient()
  return useMutation({
    mutationFn: ({ id, dati }: { id: number; dati: DatiDipendenza }) =>
      api<Dipendenza>(`/api/v1/soggetti/${soggettoId}/dipendenze/${id}`, {
        method: 'PUT',
        body: JSON.stringify(dati),
      }),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['dipendenze', soggettoId] }),
  })
}
