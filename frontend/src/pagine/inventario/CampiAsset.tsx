import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Box, Button, Checkbox, FormControlLabel, MenuItem, Stack, TextField } from '@mui/material'
import { Controller, useForm } from 'react-hook-form'
import { etichetteTipoAsset, type DatiAsset, type TipoAsset } from '../../api/inventario'
import { schemaAsset } from './formInventario'

/** Form dell'asset, usato sia per la creazione sia per la modifica. */
export function CampiAsset({
  valori,
  solaLettura,
  etichettaSalva,
  errore,
  onSalva,
  onAnnulla,
}: {
  valori: DatiAsset
  solaLettura: boolean
  etichettaSalva: string
  errore?: Error | null
  onSalva: (dati: DatiAsset) => Promise<unknown>
  onAnnulla?: () => void
}) {
  const {
    register,
    control,
    handleSubmit,
    reset,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<DatiAsset>({ resolver: zodResolver(schemaAsset), values: valori })

  const salva = handleSubmit(async (dati) => {
    await onSalva({ ...dati, nome: dati.nome.trim() }).catch(() => undefined)
  })

  return (
    <Box component="form" noValidate onSubmit={salva}>
      <Stack spacing={2}>
        <TextField
          label="Nome"
          fullWidth
          slotProps={{ input: { readOnly: solaLettura } }}
          {...register('nome')}
          error={Boolean(errors.nome)}
          helperText={errors.nome?.message}
        />
        <Controller
          name="tipoAsset"
          control={control}
          render={({ field }) => (
            <TextField select label="Tipo" fullWidth slotProps={{ input: { readOnly: solaLettura } }} {...field}>
              {(Object.keys(etichetteTipoAsset) as TipoAsset[]).map((tipo) => (
                <MenuItem key={tipo} value={tipo}>
                  {etichetteTipoAsset[tipo]}
                </MenuItem>
              ))}
            </TextField>
          )}
        />
        <Controller
          name="rilevante"
          control={control}
          render={({ field }) => (
            <FormControlLabel
              label="Sistema informativo e di rete rilevante (specifiche ACN)"
              control={
                <Checkbox
                  checked={field.value}
                  disabled={solaLettura}
                  onChange={(_, valore) => field.onChange(valore)}
                />
              }
            />
          )}
        />
        {errore && <Alert severity="error">{errore.message}</Alert>}
        {!solaLettura && (
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button type="submit" variant="contained" disabled={!isDirty || isSubmitting}>
              {etichettaSalva}
            </Button>
            <Button
              disabled={isSubmitting}
              onClick={() => {
                reset()
                onAnnulla?.()
              }}
            >
              Annulla
            </Button>
          </Box>
        )}
      </Stack>
    </Box>
  )
}
