import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApi } from './client'

export type RuoloSoggetto = 'REFERENTE_NIS' | 'OPERATORE' | 'REVISORE'

export const etichetteRuolo: Record<RuoloSoggetto, string> = {
  REFERENTE_NIS: 'Referente NIS',
  OPERATORE: 'Operatore',
  REVISORE: 'Revisore (sola lettura)',
}

export interface Profilo {
  utente: string
  amministratore: boolean
  soggetti: { soggettoId: number; ruolo: RuoloSoggetto }[]
}

export interface Assegnazione {
  utente: string
  soggettoId: number
  ruolo: RuoloSoggetto
  dataAssegnazione: string
  assegnatoDa: string
}

/**
 * Serve solo a mostrare o nascondere i comandi: il backend ricontrolla
 * comunque i permessi a ogni richiesta.
 */
export function puoModificare(profilo: Profilo | undefined, soggettoId: number): boolean {
  if (!profilo) {
    return false
  }
  if (profilo.amministratore) {
    return true
  }
  const ruolo = profilo.soggetti.find((s) => s.soggettoId === soggettoId)?.ruolo
  return ruolo === 'REFERENTE_NIS' || ruolo === 'OPERATORE'
}

/** Registrare un invio ACN spetta al referente NIS (o all'amministratore). */
export function puoInviare(profilo: Profilo | undefined, soggettoId: number): boolean {
  if (!profilo) {
    return false
  }
  return profilo.amministratore || profilo.soggetti.find((s) => s.soggettoId === soggettoId)?.ruolo === 'REFERENTE_NIS'
}

export function useProfilo() {
  const api = useApi()
  return useQuery({
    queryKey: ['profilo'],
    queryFn: () => api<Profilo>('/api/v1/me'),
    staleTime: 60_000,
  })
}

export function useAssegnazioni(soggettoId: number, attivo: boolean) {
  const api = useApi()
  return useQuery({
    queryKey: ['assegnazioni', soggettoId],
    queryFn: () => api<Assegnazione[]>(`/api/v1/soggetti/${soggettoId}/utenti`),
    enabled: attivo,
  })
}

export function useAssegna(soggettoId: number) {
  const api = useApi()
  const cache = useQueryClient()
  return useMutation({
    mutationFn: ({ utente, ruolo }: { utente: string; ruolo: RuoloSoggetto }) =>
      api<Assegnazione[]>(`/api/v1/soggetti/${soggettoId}/utenti/${encodeURIComponent(utente)}`, {
        method: 'PUT',
        body: JSON.stringify({ ruolo }),
      }),
    onSuccess: (assegnazioni) => cache.setQueryData(['assegnazioni', soggettoId], assegnazioni),
  })
}

export function useRevoca(soggettoId: number) {
  const api = useApi()
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (utente: string) =>
      api<void>(`/api/v1/soggetti/${soggettoId}/utenti/${encodeURIComponent(utente)}`, { method: 'DELETE' }),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['assegnazioni', soggettoId] }),
  })
}
