import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApi } from './client'

export type Categoria = 'ORGANO' | 'RUOLO_SPECIALIZZATO' | 'PERSONALE'
export type Destinatari = 'TUTTI' | 'ORGANO' | 'RUOLO_SPECIALIZZATO'
export type Esito = 'SUPERATA' | 'NON_SUPERATA' | 'NON_PREVISTA'
export type StatoAttivita = 'PIANIFICATA' | 'SVOLTA' | 'DA_REGISTRARE'

export const etichetteCategoria: Record<Categoria, string> = {
  ORGANO: 'Organi di amministrazione',
  RUOLO_SPECIALIZZATO: 'Ruoli specializzati',
  PERSONALE: 'Personale',
}

export const etichetteDestinatari: Record<Destinatari, string> = {
  TUTTI: 'Tutto il personale',
  ORGANO: 'Organi di amministrazione',
  RUOLO_SPECIALIZZATO: 'Ruoli specializzati (PR.AT-02)',
}

export const etichetteEsito: Record<Esito, string> = {
  SUPERATA: 'Verifica superata',
  NON_SUPERATA: 'Verifica non superata',
  NON_PREVISTA: 'Senza verifica',
}

export interface Persona {
  id: number
  nome: string
  categoria: Categoria
  ruolo: string | null
  dataCessazione: string | null
  ultimaFormazione: string | null
  inRegola: boolean
}

export interface Attivita {
  id: number
  titolo: string
  contenuti: string
  destinatari: Destinatari
  dataPrevista: string
  durataOre: number | null
  modalitaVerifica: string | null
  partecipanti: number
  stato: StatoAttivita
}

export interface Partecipazione {
  id: number
  attivitaId: number
  attivita: string
  destinatari: Destinatari
  personaId: number
  persona: string
  dataSvolgimento: string
  esito: Esito
  note: string | null
  registrataIl: string
  registrataDa: string
}

export interface Formazione {
  riepilogo: {
    copertura: { categoria: Categoria; persone: number; formate: number; percentuale: number }[]
    attivitaPianificate: number
    attivitaDaRegistrare: number
  }
  persone: Persona[]
  attivita: Attivita[]
  registro: Partecipazione[]
}

export interface DatiAttivita {
  titolo: string
  contenuti: string
  destinatari: Destinatari
  dataPrevista: string
  durataOre: number | null
  modalitaVerifica: string | null
}

const base = (soggettoId: number) => `/api/v1/soggetti/${soggettoId}/formazione`

export const percorsoRegistroCsv = (soggettoId: number) => `${base(soggettoId)}/registro.csv`

export function useFormazione(soggettoId: number) {
  const api = useApi()
  return useQuery({ queryKey: ['formazione', soggettoId], queryFn: () => api<Formazione>(base(soggettoId)) })
}

/** Ogni azione restituisce lo stato completo: lo si mette direttamente in cache. */
export function useAzioniFormazione(soggettoId: number) {
  const api = useApi()
  const cache = useQueryClient()
  const invia = (percorso: string, metodo: string, corpo: unknown) =>
    api<Formazione>(`${base(soggettoId)}${percorso}`, { method: metodo, body: JSON.stringify(corpo) })
  const opzioni = { onSuccess: (f: Formazione) => cache.setQueryData(['formazione', soggettoId], f) }
  return {
    persona: useMutation({
      mutationFn: (dati: { nome: string; categoria: Categoria; ruolo: string | null }) =>
        invia('/persone', 'POST', dati),
      ...opzioni,
    }),
    cessazione: useMutation({
      mutationFn: ({ id, data }: { id: number; data: string }) => invia(`/persone/${id}/cessazione`, 'POST', { data }),
      ...opzioni,
    }),
    attivita: useMutation({
      mutationFn: ({ id, dati }: { id?: number; dati: DatiAttivita }) =>
        id ? invia(`/attivita/${id}`, 'PUT', dati) : invia('/attivita', 'POST', dati),
      ...opzioni,
    }),
    partecipazioni: useMutation({
      mutationFn: ({
        id,
        ...dati
      }: {
        id: number
        persone: number[]
        data: string
        esito: Esito
        note: string | null
      }) => invia(`/attivita/${id}/partecipazioni`, 'POST', dati),
      ...opzioni,
    }),
  }
}
