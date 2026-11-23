import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApi } from './client'

export type StatoAdempimento = 'NON_ATTUATO' | 'PARZIALMENTE_ATTUATO' | 'ATTUATO' | 'NON_APPLICABILE'

export const etichetteStato: Record<StatoAdempimento, string> = {
  ATTUATO: 'Attuato',
  PARZIALMENTE_ATTUATO: 'Parzialmente attuato',
  NON_ATTUATO: 'Non attuato',
  NON_APPLICABILE: 'Non applicabile',
}

export interface Requisito {
  id: number
  codice: string
  numeroPunto: number
  testo: string
  misuraCodice: string
  misuraCategoria: string
  misuraDescrizione: string
  stato: StatoAdempimento | null
  dataValutazione: string | null
  note: string | null
}

export interface Conteggi {
  totale: number
  attuati: number
  parziali: number
  nonAttuati: number
  nonApplicabili: number
  nonValutati: number
}

export interface Riepilogo {
  complessivo: Conteggi
  percentualeAttuazione: number
  misure: {
    codice: string
    categoria: string
    descrizione: string
    conteggi: Conteggi
    percentualeAttuazione: number
  }[]
}

export interface VersioneAdempimento {
  versione: number
  stato: StatoAdempimento
  dataValutazione: string
  validoDa: string
  validoA: string | null
  modificataDa: string | null
}

const base = (soggettoId: number) => `/api/v1/soggetti/${soggettoId}/adempimenti`

export function useRequisiti(soggettoId: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['adempimenti', soggettoId],
    queryFn: () => api<Requisito[]>(base(soggettoId)),
  })
}

export function useRiepilogo(soggettoId: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['adempimenti', soggettoId, 'riepilogo'],
    queryFn: () => api<Riepilogo>(`${base(soggettoId)}/riepilogo`),
  })
}

export function useVersioniAdempimento(soggettoId: number, requisitoId: number | null) {
  const api = useApi()
  return useQuery({
    queryKey: ['adempimenti', soggettoId, requisitoId, 'versioni'],
    queryFn: () => api<VersioneAdempimento[]>(`${base(soggettoId)}/${requisitoId}/versioni`),
    enabled: requisitoId !== null,
  })
}

export function useValuta(soggettoId: number) {
  const api = useApi()
  const cache = useQueryClient()
  return useMutation({
    mutationFn: ({ requisitoId, stato, note }: { requisitoId: number; stato: StatoAdempimento; note: string | null }) =>
      api<Requisito>(`${base(soggettoId)}/${requisitoId}`, {
        method: 'PUT',
        body: JSON.stringify({ stato, note }),
      }),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['adempimenti', soggettoId] }),
  })
}
