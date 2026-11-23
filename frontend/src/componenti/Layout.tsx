import { AppBar, Box, Button, Chip, Container, Toolbar, Typography } from '@mui/material'
import { useAuth } from 'react-oidc-context'
import { Link as RouterLink, Outlet } from 'react-router'
import { useProfilo } from '../api/profilo'

export function Layout() {
  const auth = useAuth()
  const utente = auth.user?.profile.preferred_username ?? auth.user?.profile.name
  const { data: profilo } = useProfilo()

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'grey.50' }}>
      <AppBar position="static" elevation={0}>
        <Toolbar>
          <Typography
            variant="h6"
            component={RouterLink}
            to="/"
            sx={{ flexGrow: 1, color: 'inherit', textDecoration: 'none' }}
          >
            NIS2 Compliance Manager
          </Typography>
          <Button color="inherit" component={RouterLink} to="/">
            Soggetti
          </Button>
          <Button color="inherit" component={RouterLink} to="/fornitori" sx={{ mr: 2 }}>
            Fornitori
          </Button>
          {profilo?.amministratore && (
            <Chip label="Amministratore" size="small" color="secondary" sx={{ mr: 2 }} />
          )}
          {utente && (
            <Typography variant="body2" sx={{ mr: 2 }}>
              {utente}
            </Typography>
          )}
          <Button color="inherit" onClick={() => void auth.signoutRedirect()}>
            Esci
          </Button>
        </Toolbar>
      </AppBar>
      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Outlet />
      </Container>
    </Box>
  )
}
