import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Chip,
  Grid,
  LinearProgress,
  Link,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router'
import {
  etichetteTipoOrganizzazione,
  euro,
  useAzioniOrganizzazione,
  useLogo,
  useOrganizzazione,
} from '../../api/organizzazioni'
import { useProfilo } from '../../api/profilo'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { data } from '../inventario/date'
import { FormOrganizzazione } from './FormOrganizzazione'
import { descriviQuota, quotaPiena, quotaUsata } from './quota'

export function DettaglioOrganizzazione() {
  const id = Number(useParams().organizzazioneId)
  const { data: profilo } = useProfilo()
  const { data: o, error } = useOrganizzazione(id)
  const logo = useLogo(id, o?.conLogo ?? false)
  const azioni = useAzioniOrganizzazione()
  const [modifica, setModifica] = useState(false)
  const [nuovoAdmin, setNuovoAdmin] = useState('')

  if (!o) return <StatoCaricamento errore={error} />
  const gestore = profilo?.amministratore ?? false
  const uso = quotaUsata(o)
  const errore = azioni.aggiungiAmministratore.error ?? azioni.rimuoviAmministratore.error ?? azioni.logo.error

  return (
    <Stack spacing={3}>
      {gestore && (
        <Breadcrumbs>
          <Link component={RouterLink} to="/organizzazioni">
            Organizzazioni
          </Link>
          <Typography color="text.primary">{o.nome}</Typography>
        </Breadcrumbs>
      )}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h4" component="h1">
            {o.nome}
          </Typography>
          <Typography color="text.secondary">
            {etichetteTipoOrganizzazione[o.tipo]} · P. IVA {o.codiceFiscale}
          </Typography>
        </Box>
        <Chip
          color={o.stato === 'ATTIVA' ? 'success' : 'warning'}
          label={o.stato === 'ATTIVA' ? 'Attiva' : 'Sospesa'}
        />
        {gestore && <Button onClick={() => setModifica(true)}>Modifica</Button>}
      </Box>
      {o.stato === 'SOSPESA' && (
        <Alert severity="warning">
          L’organizzazione è sospesa: i dati restano consultabili ma non si possono modificare.
        </Alert>
      )}
      {errore && <Alert severity="error">{errore.message}</Alert>}

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper variant="outlined" sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6" component="h2" gutterBottom>
              Piano {o.piano.nome}
            </Typography>
            <Typography>
              {euro.format(o.piano.prezzoMensileSoggetto)} per soggetto al mese · {descriviQuota(o)}
            </Typography>
            {uso !== null && (
              <LinearProgress
                variant="determinate"
                value={uso}
                color={quotaPiena(o) ? 'warning' : 'primary'}
                sx={{ height: 8, borderRadius: 4, my: 2 }}
                aria-label="Soggetti usati"
              />
            )}
            <Typography variant="h5" component="p">
              {euro.format(o.costoMensile)} al mese
            </Typography>
            {quotaPiena(o) && (
              <Typography variant="body2" color="warning.main">
                Quota raggiunta: per nuovi soggetti serve un piano superiore.
              </Typography>
            )}
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              Contratto {o.riferimentoContratto ?? '—'}
              {o.dataContratto ? ` del ${data(o.dataContratto)}` : ''}
            </Typography>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper variant="outlined" sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6" component="h2" gutterBottom>
              Logo nei report
            </Typography>
            <Box
              sx={{
                height: 80,
                display: 'flex',
                alignItems: 'center',
                border: 1,
                borderColor: 'divider',
                borderRadius: 1,
                p: 1,
                mb: 2,
              }}
            >
              {logo.data ? (
                <img src={logo.data} alt={`Logo di ${o.nome}`} style={{ maxHeight: '100%', maxWidth: '100%' }} />
              ) : (
                <Typography color="text.secondary">Nessun logo: i report usano il nome dell’organizzazione.</Typography>
              )}
            </Box>
            <Button variant="outlined" component="label" disabled={azioni.logo.isPending}>
              Carica logo (PNG o JPEG, max 512 KB)
              <input
                hidden
                type="file"
                accept="image/png,image/jpeg"
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  if (file) azioni.logo.mutate({ id, file })
                }}
              />
            </Button>
          </Paper>
        </Grid>
      </Grid>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          Amministratori dell’organizzazione
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Gestiscono tutti i soggetti dell’organizzazione come un referente NIS, ne creano di nuovi entro la quota e
          assegnano gli accessi ai clienti. Non vedono i soggetti di altre organizzazioni.
        </Typography>
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', mb: 2 }}>
          {o.amministratori.length === 0 && <Typography color="text.secondary">Nessuno.</Typography>}
          {o.amministratori.map((a) => (
            <Chip
              key={a.utente}
              label={a.utente}
              onDelete={
                a.utente === profilo?.utente && !gestore
                  ? undefined
                  : () => azioni.rimuoviAmministratore.mutate({ id, utente: a.utente })
              }
            />
          ))}
        </Stack>
        <Stack direction="row" spacing={1}>
          <TextField
            size="small"
            label="Nome utente"
            value={nuovoAdmin}
            onChange={(e) => setNuovoAdmin(e.target.value.trim())}
          />
          <Button
            disabled={!/^[a-zA-Z0-9._@-]{1,64}$/.test(nuovoAdmin) || azioni.aggiungiAmministratore.isPending}
            onClick={() =>
              azioni.aggiungiAmministratore.mutate({ id, utente: nuovoAdmin }, { onSuccess: () => setNuovoAdmin('') })
            }
          >
            Aggiungi
          </Button>
        </Stack>
      </Paper>

      {modifica && <FormOrganizzazione organizzazione={o} onChiudi={() => setModifica(false)} />}
    </Stack>
  )
}
