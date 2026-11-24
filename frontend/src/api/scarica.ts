import { useCallback } from 'react'
import { useAuth } from 'react-oidc-context'
import { ErroreApi, type Problema } from './client'

/**
 * Scarica un file protetto. Serve il token, quindi un semplice link non basta:
 * si scarica con fetch, si crea un blob e si salva con il nome indicato.
 */
export function useScarica() {
  const token = useAuth().user?.access_token
  return useCallback(
    async (percorso: string, nomeFile: string) => {
      const risposta = await fetch(percorso, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
      if (!risposta.ok) {
        const problema = (await risposta.json().catch(() => undefined)) as Problema | undefined
        throw new ErroreApi(risposta.status, problema)
      }
      const url = URL.createObjectURL(await risposta.blob())
      const collegamento = document.createElement('a')
      collegamento.href = url
      collegamento.download = nomeFile
      collegamento.click()
      URL.revokeObjectURL(url)
    },
    [token],
  )
}
