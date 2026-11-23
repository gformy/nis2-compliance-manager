import { Alert, Box, Button, CircularProgress } from '@mui/material'
import { useEffect, type ReactNode } from 'react'
import { hasAuthParams, useAuth } from 'react-oidc-context'

/** Mostra il contenuto solo a un utente autenticato; altrimenti porta al login di Keycloak. */
export function RichiedeLogin({ children }: { children: ReactNode }) {
  const auth = useAuth()

  useEffect(() => {
    if (!hasAuthParams() && !auth.isAuthenticated && !auth.activeNavigator && !auth.isLoading && !auth.error) {
      void auth.signinRedirect()
    }
  }, [auth])

  if (auth.error) {
    return (
      <Box sx={{ p: 4 }}>
        <Alert
          severity="error"
          action={<Button onClick={() => void auth.signinRedirect()}>Riprova</Button>}
        >
          Accesso non riuscito: {auth.error.message}
        </Alert>
      </Box>
    )
  }

  if (!auth.isAuthenticated) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 8 }}>
        <CircularProgress aria-label="Accesso in corso" />
      </Box>
    )
  }

  return <>{children}</>
}
