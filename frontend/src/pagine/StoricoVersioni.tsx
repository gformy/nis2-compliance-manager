import {
  Chip,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useVersioniSoggetto } from '../api/soggetti'
import { StatoCaricamento } from '../componenti/StatoCaricamento'
import { campiModificati } from './anagrafica'

const formatoData = new Intl.DateTimeFormat('it-IT', { dateStyle: 'short', timeStyle: 'short' })
const data = (valore: string | null) => (valore ? formatoData.format(new Date(valore)) : 'in vigore')

/** Storico dalla vista SoggettoNIS_Versioni del PW: chi ha cambiato cosa, e quando. */
export function StoricoVersioni({ soggettoId }: { soggettoId: number }) {
  const { data: versioni, error } = useVersioniSoggetto(soggettoId)

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="h6" component="h2" gutterBottom>
        Storico delle versioni
      </Typography>
      {!versioni ? (
        <StatoCaricamento errore={error} />
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Versione</TableCell>
                <TableCell>Valida dal</TableCell>
                <TableCell>Valida fino al</TableCell>
                <TableCell>Modificata da</TableCell>
                <TableCell>Campi cambiati</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {versioni.map((versione, indice) => {
                const cambiati = campiModificati(versione, versioni[indice + 1])
                return (
                  <TableRow key={versione.versione}>
                    <TableCell>{versione.versione}</TableCell>
                    <TableCell>{data(versione.validoDa)}</TableCell>
                    <TableCell>{data(versione.validoA)}</TableCell>
                    <TableCell>{versione.modificataDa ?? 'creazione'}</TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
                        {cambiati.map((campo) => (
                          <Chip key={campo} label={campo} size="small" variant="outlined" />
                        ))}
                      </Stack>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Paper>
  )
}
