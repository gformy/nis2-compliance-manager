import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApi } from './client'

export type Gravita = 'BASSA' | 'MEDIA' | 'ALTA' | 'CRITICA'
export type StatoIncidente = 'APERTO' | 'CHIUSO'
export type TipoNotifica =
  'PRE_NOTIFICA' | 'NOTIFICA' | 'RELAZIONE_INTERMEDIA' | 'RELAZIONE_FINALE' | 'RELAZIONE_MENSILE'
export type TipoIoc = 'IP' | 'HASH' | 'DOMINIO' | 'URL' | 'EMAIL'
export type FaseTimeline = 'RILEVAMENTO' | 'INVESTIGAZIONE' | 'CONTENIMENTO' | 'ERADICAZIONE' | 'RIPRISTINO'
export type FaseRisposta = 'CONTENIMENTO' | 'ERADICAZIONE'
export type StatoScadenza = 'IN_ATTESA' | 'IN_TEMPO' | 'IN_SCADENZA' | 'SCADUTA' | 'TRASMESSA' | 'TRASMESSA_IN_RITARDO'

export const etichetteGravita: Record<Gravita, string> = {
  BASSA: 'Bassa',
  MEDIA: 'Media',
  ALTA: 'Alta',
  CRITICA: 'Critica',
}

export const etichetteNotifica: Record<TipoNotifica, string> = {
  PRE_NOTIFICA: 'Pre-notifica',
  NOTIFICA: 'Notifica',
  RELAZIONE_INTERMEDIA: 'Relazione intermedia',
  RELAZIONE_FINALE: 'Relazione finale',
  RELAZIONE_MENSILE: 'Relazione mensile',
}

export const etichetteFase: Record<FaseTimeline, string> = {
  RILEVAMENTO: 'Rilevamento',
  INVESTIGAZIONE: 'Investigazione',
  CONTENIMENTO: 'Contenimento',
  ERADICAZIONE: 'Eradicazione',
  RIPRISTINO: 'Ripristino',
}

export const tipiIoc: TipoIoc[] = ['IP', 'HASH', 'DOMINIO', 'URL', 'EMAIL']

export interface TipoIncidente {
  id: number
  codice: string
  descrizione: string
}

export interface Catalogo {
  tipi: TipoIncidente[]
  categorie: string[]
}

export interface Scadenza {
  tipo: TipoNotifica
  scadenza: string | null
  stato: StatoScadenza
  trasmessaIl: string | null
}

export interface SintesiIncidente {
  id: number
  titolo: string
  tipoIncidente: string
  categoria: string | null
  gravita: Gravita | null
  stato: StatoIncidente
  dataEvidenza: string
  prossimaScadenza: Scadenza | null
}

export interface Incidente {
  id: number
  titolo: string
  descrizione: string | null
  tipo: TipoIncidente
  categoria: string | null
  gravita: Gravita | null
  rootCause: string | null
  stato: StatoIncidente
  dataEvidenza: string
  dataChiusura: string | null
  dataRegistrazione: string
  registratoDa: string
  elementi: { id: number; nome: string; tipo: 'ASSET' | 'SERVIZIO' }[]
  notifiche: {
    id: number
    tipo: TipoNotifica
    dataTrasmissione: string
    contenuto: string | null
    registrataDa: string
  }[]
  scadenze: Scadenza[]
  ioc: { id: number; tipo: TipoIoc; valore: string; dataRilevazione: string }[]
  timeline: {
    id: number
    dataOra: string
    fase: FaseTimeline
    descrizione: string
  }[]
  attivita: {
    id: number
    fase: FaseRisposta
    descrizione: string
    motivazione: string | null
    esito: string | null
    dataEsecuzione: string
  }[]
}

export interface DatiIncidente {
  titolo: string
  descrizione: string | null
  tipoIncidente: string
  categoria: string | null
  gravita: Gravita | null
  dataEvidenza: string
  rootCause: string | null
  elementiCritici: number[]
}

const base = (soggettoId: number) => `/api/v1/soggetti/${soggettoId}/incidenti`

export function useCatalogoIncidenti(soggettoId: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['incidenti', soggettoId, 'catalogo'],
    queryFn: () => api<Catalogo>(`${base(soggettoId)}/catalogo`),
    staleTime: Infinity,
  })
}

export function useIncidenti(soggettoId: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['incidenti', soggettoId],
    queryFn: () => api<SintesiIncidente[]>(base(soggettoId)),
  })
}

export function useIncidente(soggettoId: number, id: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['incidenti', soggettoId, id],
    queryFn: () => api<Incidente>(`${base(soggettoId)}/${id}`),
  })
}

export function useCreaIncidente(soggettoId: number) {
  const api = useApi()
  const cache = useQueryClient()
  return useMutation({
    mutationFn: (dati: DatiIncidente) =>
      api<Incidente>(base(soggettoId), {
        method: 'POST',
        body: JSON.stringify(dati),
      }),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['incidenti', soggettoId] }),
  })
}

/**
 * Tutte le modifiche a un incidente rispondono con l'incidente aggiornato:
 * lo si mette direttamente in cache e si invalida solo l'elenco.
 */
export function useAzioneIncidente(soggettoId: number, id: number) {
  const api = useApi()
  const cache = useQueryClient()
  return useMutation({
    mutationFn: ({ percorso = '', metodo = 'POST', corpo }: { percorso?: string; metodo?: string; corpo?: unknown }) =>
      api<Incidente>(`${base(soggettoId)}/${id}${percorso}`, {
        method: metodo,
        body: corpo === undefined ? undefined : JSON.stringify(corpo),
      }),
    onSuccess: (incidente) => {
      cache.setQueryData(['incidenti', soggettoId, id], incidente)
      void cache.invalidateQueries({
        queryKey: ['incidenti', soggettoId],
        exact: true,
      })
    },
  })
}
