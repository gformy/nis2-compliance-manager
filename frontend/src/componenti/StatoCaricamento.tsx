import { Alert, Box, CircularProgress } from '@mui/material'

/** Rotella di caricamento o messaggio d'errore per una query. */
export function StatoCaricamento({ errore }: { errore?: Error | null }) {
  if (errore) {
    return <Alert severity="error">{errore.message}</Alert>
  }
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
      <CircularProgress aria-label="Caricamento" />
    </Box>
  )
}
