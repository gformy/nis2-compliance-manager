import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { useApi } from './client'

export type TipoOrganizzazione = 'CLIENTE_DIRETTO' | 'RIVENDITORE'
export type StatoOrganizzazione = 'ATTIVA' | 'SOSPESA'

export const etichetteTipoOrganizzazione: Record<TipoOrganizzazione, string> = {
  CLIENTE_DIRETTO: 'Cliente diretto',
  RIVENDITORE: 'Rivenditore (MSP, consulente)',
}

export interface Piano {
  codice: string
  nome: string
  maxSoggetti: number | null
  prezzoMensileSoggetto: number
}

export interface Organizzazione {
  id: number
  nome: string
  codiceFiscale: string
  tipo: TipoOrganizzazione
  stato: StatoOrganizzazione
  piano: Piano
  soggetti: number
  costoMensile: number
  riferimentoContratto: string | null
  dataContratto: string | null
  conLogo: boolean
  amministratori: { utente: string; dataAssegnazione: string; assegnatoDa: string }[]
}

export interface DatiOrganizzazione {
  nome: string
  codiceFiscale: string
  tipo: TipoOrganizzazione
  piano: string
  stato: StatoOrganizzazione
  riferimentoContratto: string | null
  dataContratto: string | null
}

export const euro = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' })

export function usePiani() {
  const api = useApi()
  return useQuery({ queryKey: ['piani'], queryFn: () => api<Piano[]>('/api/v1/piani'), staleTime: Infinity })
}

export function useOrganizzazioni() {
  const api = useApi()
  return useQuery({ queryKey: ['organizzazioni'], queryFn: () => api<Organizzazione[]>('/api/v1/organizzazioni') })
}

export function useOrganizzazione(id: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['organizzazioni', id],
    queryFn: () => api<Organizzazione>(`/api/v1/organizzazioni/${id}`),
  })
}

export function useAzioniOrganizzazione() {
  const api = useApi()
  const cache = useQueryClient()
  const aggiorna = (o: Organizzazione) => {
    cache.setQueryData(['organizzazioni', o.id], o)
    void cache.invalidateQueries({ queryKey: ['organizzazioni'], exact: true })
  }
  return {
    salva: useMutation({
      mutationFn: ({ id, dati }: { id?: number; dati: DatiOrganizzazione }) =>
        api<Organizzazione>(id ? `/api/v1/organizzazioni/${id}` : '/api/v1/organizzazioni', {
          method: id ? 'PUT' : 'POST',
          body: JSON.stringify(dati),
        }),
      onSuccess: aggiorna,
    }),
    aggiungiAmministratore: useMutation({
      mutationFn: ({ id, utente }: { id: number; utente: string }) =>
        api<Organizzazione>(`/api/v1/organizzazioni/${id}/amministratori/${encodeURIComponent(utente)}`, {
          method: 'PUT',
        }),
      onSuccess: aggiorna,
    }),
    rimuoviAmministratore: useMutation({
      mutationFn: ({ id, utente }: { id: number; utente: string }) =>
        api<Organizzazione>(`/api/v1/organizzazioni/${id}/amministratori/${encodeURIComponent(utente)}`, {
          method: 'DELETE',
        }),
      onSuccess: aggiorna,
    }),
    logo: useMutation({
      mutationFn: ({ id, file }: { id: number; file: File }) => {
        const dati = new FormData()
        dati.append('file', file)
        return api<Organizzazione>(`/api/v1/organizzazioni/${id}/logo`, { method: 'PUT', body: dati })
      },
      onSuccess: (o) => {
        aggiorna(o)
        void cache.invalidateQueries({ queryKey: ['logo', o.id] })
      },
    }),
  }
}

/** Il logo richiede il token: si scarica come blob e si mostra con un URL locale. */
export function useLogo(id: number, presente: boolean) {
  const auth = useAuth()
  const token = auth.user?.access_token
  return useQuery({
    queryKey: ['logo', id],
    enabled: presente,
    queryFn: async () => {
      const risposta = await fetch(`/api/v1/organizzazioni/${id}/logo`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      if (!risposta.ok) return null
      return URL.createObjectURL(await risposta.blob())
    },
  })
}
