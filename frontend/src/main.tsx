import { CssBaseline, ThemeProvider, createTheme } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'
import { RouterProvider, createBrowserRouter } from 'react-router'
import { RichiedeLogin } from './auth/RichiedeLogin'
import { configurazioneOidc } from './auth/configurazione'
import { Layout } from './componenti/Layout'
import { DettaglioSoggetto, SchedaAnagrafica } from './pagine/DettaglioSoggetto'
import { ElencoSoggetti } from './pagine/ElencoSoggetti'
import { SchedaConformita } from './pagine/conformita/SchedaConformita'
import { DettaglioIncidente } from './pagine/incidenti/DettaglioIncidente'
import { ElencoIncidenti } from './pagine/incidenti/ElencoIncidenti'
import { ConfrontoInvii } from './pagine/invii/ConfrontoInvii'
import { DettaglioInvio } from './pagine/invii/DettaglioInvio'
import { ElencoInvii } from './pagine/invii/ElencoInvii'
import { CatalogoFornitori } from './pagine/fornitori/CatalogoFornitori'
import { DettaglioFornitore } from './pagine/fornitori/DettaglioFornitore'
import { SchedaFornitori } from './pagine/fornitori/SchedaFornitori'
import { DettaglioAsset } from './pagine/inventario/DettaglioAsset'
import { DettaglioServizio } from './pagine/inventario/DettaglioServizio'
import { ElencoAsset } from './pagine/inventario/ElencoAsset'
import { ElencoServizi } from './pagine/inventario/ElencoServizi'

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <ElencoSoggetti /> },
      {
        path: '/soggetti/:id',
        element: <DettaglioSoggetto />,
        children: [
          { index: true, element: <SchedaAnagrafica /> },
          { path: 'asset', element: <ElencoAsset /> },
          { path: 'asset/:assetId', element: <DettaglioAsset /> },
          { path: 'servizi', element: <ElencoServizi /> },
          { path: 'servizi/:servizioId', element: <DettaglioServizio /> },
          { path: 'fornitori', element: <SchedaFornitori /> },
          { path: 'conformita', element: <SchedaConformita /> },
          { path: 'incidenti', element: <ElencoIncidenti /> },
          { path: 'incidenti/:incidenteId', element: <DettaglioIncidente /> },
          { path: 'invii', element: <ElencoInvii /> },
          { path: 'invii/confronto', element: <ConfrontoInvii /> },
          { path: 'invii/:invioId', element: <DettaglioInvio /> },
        ],
      },
      { path: '/fornitori', element: <CatalogoFornitori /> },
      { path: '/fornitori/:fornitoreId', element: <DettaglioFornitore /> },
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
