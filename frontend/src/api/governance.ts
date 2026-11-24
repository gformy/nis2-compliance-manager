import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApi } from './client'

export type Categoria = 'POLITICA' | 'DOCUMENTO'
export type StatoDocumento = 'MANCANTE' | 'DA_APPROVARE' | 'VALIDO' | 'RIESAME_IN_SCADENZA' | 'RIESAME_SCADUTO'

export interface Organo {
  id: number
  nome: string
  ruolo: string
}

export interface FileDocumento {
  id: number
  nomeFile: string
  contentType: string
  dimensione: number
  sha256: string
  versioneDocumento: string | null
  caricatoIl: string
  caricatoDa: string
}

export interface Documento {
  id: number
  versione: string | null
  dataApprovazione: string | null
  approvatoDa: Organo | null
  dataUltimoRiesame: string | null
  periodicitaRiesameMesi: number
  note: string | null
  prossimoRiesame: string | null
  ultimoFile: FileDocumento | null
}

export interface Voce {
  codice: string
  nome: string
  categoria: Categoria
  ambito: string | null
  stato: StatoDocumento
  documento: Documento | null
}

export interface Riepilogo {
  totale: number
  validi: number
  inScadenza: number
  scaduti: number
  daApprovare: number
  mancanti: number
  percentualeInRegola: number
}

export interface VersioneDocumento {
  versione: number
  versioneDocumento: string | null
  dataApprovazione: string | null
  approvatoDa: string | null
  dataUltimoRiesame: string | null
  validoDa: string
  validoA: string | null
  modificataDa: string | null
}

const base = (soggettoId: number) => `/api/v1/soggetti/${soggettoId}`

export function useOrgani(soggettoId: number) {
  const api = useApi()
  return useQuery({ queryKey: ['organi', soggettoId], queryFn: () => api<Organo[]>(`${base(soggettoId)}/organi`) })
}

export function useGestioneOrgani(soggettoId: number) {
  const api = useApi()
  const cache = useQueryClient()
  const aggiorna = () => cache.invalidateQueries({ queryKey: ['organi', soggettoId] })
  return {
    aggiungi: useMutation({
      mutationFn: (dati: { nome: string; ruolo: string }) =>
        api<Organo>(`${base(soggettoId)}/organi`, { method: 'POST', body: JSON.stringify(dati) }),
      onSuccess: aggiorna,
    }),
    elimina: useMutation({
      mutationFn: (id: number) => api<void>(`${base(soggettoId)}/organi/${id}`, { method: 'DELETE' }),
      onSuccess: aggiorna,
    }),
  }
}

export function useDocumenti(soggettoId: number) {
  const api = useApi()
  return useQuery({
    queryKey: ['documenti', soggettoId],
    queryFn: () => api<{ riepilogo: Riepilogo; voci: Voce[] }>(`${base(soggettoId)}/documenti`),
  })
}

export function useVersioniDocumento(soggettoId: number, documentoId: number | null) {
  const api = useApi()
  return useQuery({
    queryKey: ['documenti', soggettoId, documentoId, 'versioni'],
    queryFn: () => api<VersioneDocumento[]>(`${base(soggettoId)}/documenti/${documentoId}/versioni`),
    enabled: documentoId !== null,
  })
}

export function useFileDocumento(soggettoId: number, documentoId: number | null) {
  const api = useApi()
  return useQuery({
    queryKey: ['documenti', soggettoId, documentoId, 'file'],
    queryFn: () => api<FileDocumento[]>(`${base(soggettoId)}/documenti/${documentoId}/file`),
    enabled: documentoId !== null,
  })
}

export const percorsoFile = (soggettoId: number, documentoId: number, fileId: number) =>
  `${base(soggettoId)}/documenti/${documentoId}/file/${fileId}`

/** Tutte le azioni su un documento restituiscono la voce aggiornata: si ricarica l'elenco. */
export function useAzioniDocumento(soggettoId: number) {
  const api = useApi()
  const cache = useQueryClient()
  const opzioni = { onSuccess: () => cache.invalidateQueries({ queryKey: ['documenti', soggettoId] }) }
  const doc = (id: number) => `${base(soggettoId)}/documenti/${id}`
  return {
    crea: useMutation({
      mutationFn: (dati: { tipo: string; versione: string | null; periodicitaRiesameMesi: number }) =>
        api<Voce>(`${base(soggettoId)}/documenti`, { method: 'POST', body: JSON.stringify(dati) }),
      ...opzioni,
    }),
    carica: useMutation({
      mutationFn: ({ id, file, versione }: { id: number; file: File; versione: string }) => {
        const dati = new FormData()
        dati.append('file', file)
        const query = versione.trim() ? `?versione=${encodeURIComponent(versione.trim())}` : ''
        return api<Voce>(`${doc(id)}/file${query}`, { method: 'POST', body: dati })
      },
      ...opzioni,
    }),
    approva: useMutation({
      mutationFn: ({ id, data, organoId }: { id: number; data: string; organoId: number }) =>
        api<Voce>(`${doc(id)}/approvazione`, { method: 'POST', body: JSON.stringify({ data, organoId }) }),
      ...opzioni,
    }),
    riesame: useMutation({
      mutationFn: ({ id, data }: { id: number; data: string }) =>
        api<Voce>(`${doc(id)}/riesame`, { method: 'POST', body: JSON.stringify({ data }) }),
      ...opzioni,
    }),
  }
}
