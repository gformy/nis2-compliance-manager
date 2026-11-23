import { useEffect, useState } from 'react'

/** Ora corrente aggiornata periodicamente, per i conti alla rovescia. */
export function useAdesso(intervalloMs = 30_000) {
  const [adesso, setAdesso] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setAdesso(Date.now()), intervalloMs)
    return () => clearInterval(timer)
  }, [intervalloMs])
  return adesso
}
