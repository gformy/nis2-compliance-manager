import { CssBaseline, ThemeProvider, createTheme } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'
import { RouterProvider, createBrowserRouter } from 'react-router'
import { RichiedeLogin } from './auth/RichiedeLogin'
import { configurazioneOidc } from './auth/configurazione'
import { Layout } from './componenti/Layout'
import { DettaglioSoggetto } from './pagine/DettaglioSoggetto'
import { ElencoSoggetti } from './pagine/ElencoSoggetti'

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <ElencoSoggetti /> },
      { path: '/soggetti/:id', element: <DettaglioSoggetto /> },
    ],
  },
])

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
})

const tema = createTheme({
  palette: { primary: { main: '#1d4e89' } },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider {...configurazioneOidc}>
      <ThemeProvider theme={tema}>
        <CssBaseline />
        <RichiedeLogin>
          <QueryClientProvider client={queryClient}>
            <RouterProvider router={router} />
          </QueryClientProvider>
        </RichiedeLogin>
      </ThemeProvider>
    </AuthProvider>
  </StrictMode>,
)
