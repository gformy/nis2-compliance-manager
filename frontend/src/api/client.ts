import { useCallback } from 'react'
import { useAuth } from 'react-oidc-context'

/** Errore in formato Problem Details (RFC 9457), come lo restituisce il backend. */
export interface Problema {
  title?: string
  detail?: string
  status?: number
  errori?: Record<string, string>
}

export class ErroreApi extends Error {
  readonly stato: number
  readonly problema?: Problema

  constructor(stato: number, problema?: Problema) {
    super(problema?.detail ?? `Errore ${stato}`)
    this.stato = stato
    this.problema = problema
  }
}

/** Restituisce una funzione che chiama l'API con il token dell'utente. */
export function useApi() {
  const token = useAuth().user?.access_token

  return useCallback(
    async <T>(percorso: string, opzioni: RequestInit = {}): Promise<T> => {
      const risposta = await fetch(percorso, {
        ...opzioni,
        headers: {
          Accept: 'application/json',
          ...(typeof opzioni.body === 'string' ? { 'Content-Type': 'application/json' } : {}),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      })
      if (!risposta.ok) {
        const problema = (await risposta.json().catch(() => undefined)) as Problema | undefined
        throw new ErroreApi(risposta.status, problema)
      }
      if (risposta.status === 204) {
        return undefined as T
      }
      return (await risposta.json()) as T
    },
    [token],
  )
}
