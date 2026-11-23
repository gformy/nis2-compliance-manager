import { Alert, Autocomplete, Box, Button, Chip, Stack, TextField } from '@mui/material'
import { useState } from 'react'
import { useCatalogoCpv, type CodiceCpv } from '../../api/fornitori'
import { ErroreApi } from '../../api/client'
import { erroriFornitore, type ValoriFornitore } from './validazioneFornitore'

export function FormFornitore({
  iniziali,
  nuovo,
  solaLettura,
  onSalva,
  inCorso,
  errore,
}: {
  iniziali: ValoriFornitore
  nuovo: boolean
  solaLettura: boolean
  onSalva: (valori: ValoriFornitore) => void
  inCorso: boolean
  errore: Error | null
}) {
  const catalogo = useCatalogoCpv()
  const [valori, setValori] = useState(iniziali)
  const [tentato, setTentato] = useState(false)
  const errori = tentato ? erroriFornitore(valori, nuovo) : {}
  const erroriServer = errore instanceof ErroreApi ? errore.problema?.errori : undefined

  const cambia = <K extends keyof ValoriFornitore>(campo: K, valore: ValoriFornitore[K]) =>
    setValori((v) => ({ ...v, [campo]: valore }))

  const selezionati = (catalogo.data ?? []).filter((c) => valori.cpv.includes(c.codice))
  const solaLetturaInput = { input: { readOnly: solaLettura } }

  return (
    <Box
      component="form"
      noValidate
      onSubmit={(e) => {
        e.preventDefault()
        setTentato(true)
        if (Object.keys(erroriFornitore(valori, nuovo)).length === 0) {
          onSalva({ ...valori, denominazione: valori.denominazione.trim() })
        }
      }}
    >
      <Stack spacing={2}>
        <TextField
          label="Codice fiscale o partita IVA"
          value={valori.codiceFiscale}
          onChange={(e) => cambia('codiceFiscale', e.target.value.toUpperCase().trim())}
          slotProps={{ input: { readOnly: !nuovo || solaLettura } }}
          error={Boolean(errori.codiceFiscale ?? erroriServer?.['codiceFiscale'])}
          helperText={errori.codiceFiscale ?? erroriServer?.['codiceFiscale'] ?? (nuovo ? 'Non si potrà cambiare' : ' ')}
        />
        <TextField
          label="Denominazione"
          value={valori.denominazione}
          onChange={(e) => cambia('denominazione', e.target.value)}
          slotProps={solaLetturaInput}
          error={Boolean(errori.denominazione)}
          helperText={errori.denominazione}
        />
        <TextField
          label="Paese della sede legale"
          value={valori.paeseSedeLegale}
          onChange={(e) => cambia('paeseSedeLegale', e.target.value.toUpperCase().slice(0, 2))}
          slotProps={solaLetturaInput}
          error={Boolean(errori.paeseSedeLegale)}
          helperText={errori.paeseSedeLegale ?? 'Codice ISO di due lettere'}
        />
        <Autocomplete
          multiple
          readOnly={solaLettura}
          options={catalogo.data ?? []}
          value={selezionati}
          onChange={(_, scelti: CodiceCpv[]) => cambia('cpv', scelti.map((c) => c.codice))}
          getOptionLabel={(c) => `${c.codice} · ${c.descrizione}`}
          isOptionEqualToValue={(a, b) => a.codice === b.codice}
          renderValue={(scelti, getItemProps) =>
            scelti.map((c, indice) => {
              const { key, ...props } = getItemProps({ index: indice })
              return <Chip key={key} size="small" label={c.codice} title={c.descrizione} {...props} />
            })
          }
          renderInput={(params) => <TextField {...params} label="Codici CPV delle forniture" />}
        />
        {errore && <Alert severity="error">{errore.message}</Alert>}
        {!solaLettura && (
          <Box>
            <Button type="submit" variant="contained" disabled={inCorso}>
              Salva
            </Button>
          </Box>
        )}
      </Stack>
    </Box>
  )
}
