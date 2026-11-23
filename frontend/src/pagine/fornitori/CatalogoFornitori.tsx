import {
  Box,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Link,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router'
import { useCercaFornitori, useSalvaFornitore } from '../../api/fornitori'
import { nomePaese } from '../../api/inventario'
import { useProfilo } from '../../api/profilo'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { FormFornitore } from './FormFornitore'

/** Catalogo dei fornitori condiviso da tutti i soggetti; lo modifica solo l'amministratore. */
export function CatalogoFornitori() {
  const [testo, setTesto] = useState('')
  const fornitori = useCercaFornitori(testo)
  const { data: profilo } = useProfilo()
  const salva = useSalvaFornitore()
  const naviga = useNavigate()
  const [nuovo, setNuovo] = useState(false)

  return (
    <>
      <Typography variant="h4" component="h1" gutterBottom>
        Fornitori
      </Typography>
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', gap: 2, mb: 2, alignItems: 'center' }}>
          <TextField
            size="small"
            label="Cerca per nome o codice fiscale"
            value={testo}
            onChange={(e) => setTesto(e.target.value)}
            sx={{ flexGrow: 1 }}
          />
          {profilo?.amministratore && (
            <Button variant="contained" onClick={() => setNuovo(true)}>
              Nuovo fornitore
            </Button>
          )}
        </Box>
        {!fornitori.data ? (
          <StatoCaricamento errore={fornitori.error} />
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Denominazione</TableCell>
                  <TableCell>Codice fiscale</TableCell>
                  <TableCell>Paese</TableCell>
                  <TableCell>CPV</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {fornitori.data.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4}>Nessun fornitore trovato.</TableCell>
                  </TableRow>
                )}
                {fornitori.data.map((f) => (
                  <TableRow key={f.id} hover>
                    <TableCell>
                      <Link component={RouterLink} to={`/fornitori/${f.id}`}>
                        {f.denominazione}
                      </Link>
                    </TableCell>
                    <TableCell>{f.codiceFiscale}</TableCell>
                    <TableCell>{nomePaese(f.paeseSedeLegale)}</TableCell>
                    <TableCell>{f.cpv.map((c) => c.codice).join(', ') || '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      <Dialog open={nuovo} onClose={() => setNuovo(false)} fullWidth maxWidth="sm">
        <DialogTitle>Nuovo fornitore</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <FormFornitore
              nuovo
              solaLettura={false}
              iniziali={{ codiceFiscale: '', denominazione: '', paeseSedeLegale: 'IT', cpv: [] }}
              inCorso={salva.isPending}
              errore={salva.error}
              onSalva={({ codiceFiscale, ...dati }) =>
                salva.mutate(
                  { codiceFiscale, dati },
                  {
                    onSuccess: (creato) => {
                      setNuovo(false)
                      void naviga(`/fornitori/${creato.id}`)
                    },
                  },
                )
              }
            />
          </Box>
        </DialogContent>
      </Dialog>
    </>
  )
}
