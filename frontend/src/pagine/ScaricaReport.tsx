import { Alert, Button, Stack } from '@mui/material'
import { useState } from 'react'
import { useScarica } from '../api/scarica'

/** Report PDF di conformita' del soggetto, con il logo dell'organizzazione. */
export function ScaricaReport({ soggettoId, codiceFiscale }: { soggettoId: number; codiceFiscale: string }) {
  const scarica = useScarica()
  const [inCorso, setInCorso] = useState(false)
  const [errore, setErrore] = useState<string | null>(null)

  const genera = () => {
    setInCorso(true)
    setErrore(null)
    scarica(`/api/v1/soggetti/${soggettoId}/report.pdf`, `report-nis2-${codiceFiscale}.pdf`)
      .catch((e: Error) => setErrore(e.message))
      .finally(() => setInCorso(false))
  }

  return (
    <Stack spacing={1} sx={{ alignItems: 'flex-end' }}>
      <Button variant="outlined" onClick={genera} disabled={inCorso}>
        {inCorso ? 'Generazione…' : 'Scarica report PDF'}
      </Button>
      {errore && <Alert severity="error">{errore}</Alert>}
    </Stack>
  )
}
