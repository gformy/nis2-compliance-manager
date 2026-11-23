import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApi } from './client'

export type TipoAsset = 'HARDWARE' | 'SOFTWARE' | 'FLUSSO_RETE' | 'SERVIZIO_FORNITORE'
export type StatoElemento = 'ATTIVO' | 'DISMESSO'
export type StatoCollegamento = 'ATTIVA' | 'CESSATA'

export const etichetteTipoAsset: Record<TipoAsset, string> = {
  HARDWARE: 'Hardware',
  SOFTWARE: 'Software',
  FLUSSO_RETE: 'Flusso di rete',
  SERVIZIO_FORNITORE: 'Servizio di un fornitore',
}

/** Stati membri UE, codici ISO 3166-1 alpha-2 come nel backend. */
export const statiMembroUE = [
  'AT', 'BE', 'BG', 'CY', 'CZ', 'DE', 'DK', 'EE', 'ES', 'FI', 'FR', 'GR', 'HR', 'HU',
  'IE', 'IT', 'LT', 'LU', 'LV', 'MT', 'NL', 'PL', 'PT', 'RO', 'SE', 'SI', 'SK',
] as const
export type StatoMembro = (typeof statiMembroUE)[number]

const nomiPaesi = new Intl.DisplayNames(['it'], { type: 'region' })
export const nomePaese = (codice: string) => nomiPaesi.of(codice) ?? codice

export interface DatiAsset {
  nome: string
  tipoAsset: TipoAsset
  rilevante: boolean
}

export interface Asset extends DatiAsset {
  id: number
  stato: StatoElemento
  dataInizioValidita: string
  dataFineValidita: string | null
}

export interface DatiServizio {
  nome: string
  descrizione: string | null
  categoriaRilevanza: string | null
  statiMembro: StatoMembro[]
}

export interface Servizio extends DatiServizio {
  id: number
  stato: StatoElemento
  dataInizioValidita: string
  dataFineValidita: string | null
}

export interface Collegamento {
  assetId: number
  assetNome: string
  servizioId: number
  servizioNome: string
  stato: StatoCollegamento
}

export interface VersioneElemento {
  versione: number
  nome: string
  stato: StatoElemento
  validoDa: string
  validoA: string | null
  modificataDa: string | null
}

type Tipo = 'asset' | 'servizi'

const base = (soggettoId: number, tipo: Tipo) => `/api/v1/soggetti/${soggettoId}/${tipo}`

export function useElenco<T>(soggettoId: number, tipo: Tipo) {
  const api = useApi()
  return useQuery({
    queryKey: [tipo, soggettoId],
    queryFn: () => api<T[]>(base(soggettoId, tipo)),
  })
}

export function useElemento<T>(soggettoId: number, tipo: Tipo, id: number) {
  const api = useApi()
  return useQuery({
    queryKey: [tipo, soggettoId, id],
    queryFn: () => api<T>(`${base(soggettoId, tipo)}/${id}`),
  })
}

export function useVersioniElemento(soggettoId: number, tipo: Tipo, id: number) {
  const api = useApi()
  return useQuery({
    queryKey: [tipo, soggettoId, id, 'versioni'],
    queryFn: () => api<VersioneElemento[]>(`${base(soggettoId, tipo)}/${id}/versioni`),
  })
}

export function useCollegamenti(soggettoId: number, tipo: Tipo, id: number) {
  const api = useApi()
  const sottorisorsa = tipo === 'asset' ? 'servizi' : 'asset'
  return useQuery({
    queryKey: ['collegamenti', soggettoId, tipo, id],
    queryFn: () => api<Collegamento[]>(`${base(soggettoId, tipo)}/${id}/${sottorisorsa}`),
  })
}

/** Dopo ogni modifica si ricaricano le liste dell'inventario di quel soggetto. */
function useInvalida(soggettoId: number) {
  const cache = useQueryClient()
  return () =>
    Promise.all([
      cache.invalidateQueries({ queryKey: ['asset', soggettoId] }),
      cache.invalidateQueries({ queryKey: ['servizi', soggettoId] }),
      cache.invalidateQueries({ queryKey: ['collegamenti', soggettoId] }),
    ])
}

export function useCrea<D, T>(soggettoId: number, tipo: Tipo) {
  const api = useApi()
  const invalida = useInvalida(soggettoId)
  return useMutation({
    mutationFn: (dati: D) => api<T>(base(soggettoId, tipo), { method: 'POST', body: JSON.stringify(dati) }),
    onSuccess: invalida,
  })
}

export function useAggiorna<D, T>(soggettoId: number, tipo: Tipo, id: number) {
  const api = useApi()
  const invalida = useInvalida(soggettoId)
  return useMutation({
    mutationFn: (dati: D) =>
      api<T>(`${base(soggettoId, tipo)}/${id}`, { method: 'PUT', body: JSON.stringify(dati) }),
    onSuccess: invalida,
  })
}

export function useDismetti(soggettoId: number, tipo: Tipo, id: number) {
  const api = useApi()
  const invalida = useInvalida(soggettoId)
  return useMutation({
    mutationFn: () => api<unknown>(`${base(soggettoId, tipo)}/${id}/dismissione`, { method: 'POST' }),
    onSuccess: invalida,
  })
}

/** Collega o fa cessare il collegamento tra un servizio e un asset. */
export function useCollegamento(soggettoId: number, servizioId: number) {
  const api = useApi()
  const invalida = useInvalida(soggettoId)
  return useMutation({
    mutationFn: ({ assetId, attivo }: { assetId: number; attivo: boolean }) =>
      api<Collegamento[]>(`${base(soggettoId, 'servizi')}/${servizioId}/asset/${assetId}`, {
        method: attivo ? 'PUT' : 'DELETE',
      }),
    onSuccess: invalida,
  })
}
