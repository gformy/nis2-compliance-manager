import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'
import { useApi } from './client'
import { useScarica } from './scarica'

export type TipoInvio = 'REGISTRAZIONE' | 'AGGIORNAMENTO_ANNUALE' | 'AGGIORNAMENTO_CONTINUO' | 'CATEGORIZZAZIONE'
export type Sezione = 'elementi' | 'fornitori' | 'adempimenti'

export const etichetteTipoInvio: Record<TipoInvio, string> = {
  REGISTRAZIONE: 'Registrazione',
  AGGIORNAMENTO_ANNUALE: 'Aggiornamento annuale',
  AGGIORNAMENTO_CONTINUO: 'Aggiornamento continuo',
  CATEGORIZZAZIONE: 'Categorizzazione',
}

export interface Invio {
  id: number
  tipo: TipoInvio
  dataInvio: string
  periodoRiferimento: number
  registratoDa: string
  note: string | null
}

export interface Anagrafica {
  denominazione: string | null
  indirizzoSedeLegale: string | null
  pec: string | null
  fatturato: number | null
  numeroDipendenti: number | null
}

export interface ElementoProfilo {
  id: number
  nome: string
  tipo: 'ASSET' | 'SERVIZIO'
}

export interface FornitoreProfilo {
  dipendenzaId: number
  fornitore: string
  codiceFiscale: string
  elemento: string
  criterioRilevanza: string
  tipologiaFornitura: string | null
}

export interface AdempimentoProfilo {
  requisito: string
  stato: string
  dataValutazione: string | null
}

export interface Profilo {
  invio: Invio
  anagrafica: Anagrafica
  elementi: ElementoProfilo[]
  fornitori: FornitoreProfilo[]
  adempimenti: AdempimentoProfilo[]
}

export interface Differenze<T> {
  aggiunti: T[]
  rimossi: T[]
  modificati: { prima: T; dopo: T }[]
}

export interface Confronto {
  da: Invio
  a: Invio
  anagrafica: { campo: keyof Anagrafica; prima: unknown; dopo: unknown }[]
  elementi: Differenze<ElementoProfilo>
  fornitori: Differenze<FornitoreProfilo>
  adempimenti: Differenze<AdempimentoProfilo>
}

export interface Estrazione {
  dataOra: string
  utente: string
  dettaglio: string
}

const base = (soggettoId: number) => `/api/v1/soggetti/${soggettoId}/invii`

export function useInvii(soggettoId: number) {
  const api = useApi()
  return useQuery({ queryKey: ['invii', soggettoId], queryFn: () => api<Invio[]>(base(soggettoId)) })
}

export function useProfiloInvio(soggettoId: number, id: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['invii', soggettoId, id, 'profilo'],
    queryFn: () => api<Profilo>(`${base(soggettoId)}/${id}/profilo`),
    staleTime: Infinity, // un profilo inviato non cambia piu'
  })
}

export function useConfronto(soggettoId: number, da: number, a: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['invii', soggettoId, 'confronto', da, a],
    queryFn: () => api<Confronto>(`${base(soggettoId)}/confronto?da=${da}&a=${a}`),
    staleTime: Infinity,
  })
}

export function useEstrazioni(soggettoId: number, id: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['invii', soggettoId, id, 'estrazioni'],
    queryFn: () => api<Estrazione[]>(`${base(soggettoId)}/${id}/estrazioni`),
  })
}

export function useRegistraInvio(soggettoId: number) {
  const api = useApi()
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (dati: { tipo: TipoInvio; periodoRiferimento: number; note: string | null }) =>
      api<Invio>(base(soggettoId), { method: 'POST', body: JSON.stringify(dati) }),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['invii', soggettoId], exact: true }),
  })
}

/** Scarica il CSV di una sezione; il backend traccia l'estrazione, quindi si aggiorna il registro. */
export function useScaricaCsv(soggettoId: number, id: number) {
  const scarica = useScarica()
  const cache = useQueryClient()
  return useCallback(
    async (sezione: Sezione) => {
      await scarica(`${base(soggettoId)}/${id}/csv/${sezione}`, `invio-${id}-${sezione}.csv`)
      void cache.invalidateQueries({ queryKey: ['invii', soggettoId, id, 'estrazioni'] })
    },
    [soggettoId, id, scarica, cache],
  )
}
