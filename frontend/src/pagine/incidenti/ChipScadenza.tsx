import { Chip } from '@mui/material'
import { etichetteNotifica, type Scadenza } from '../../api/incidenti'
import { coloreScadenza, etichetteScadenza, tempoResiduo } from './tempo'

export function ChipScadenza({
  scadenza,
  adesso,
  conTipo = false,
}: {
  scadenza: Scadenza
  adesso: number
  conTipo?: boolean
}) {
  const aperta = scadenza.stato === 'IN_TEMPO' || scadenza.stato === 'IN_SCADENZA' || scadenza.stato === 'SCADUTA'
  const testo =
    aperta && scadenza.scadenza ? tempoResiduo(scadenza.scadenza, adesso) : etichetteScadenza[scadenza.stato]
  return (
    <Chip
      size="small"
      color={coloreScadenza(scadenza.stato)}
      label={conTipo ? `${etichetteNotifica[scadenza.tipo]}: ${testo}` : testo}
    />
  )
}
