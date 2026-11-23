import type { Anagrafica, Confronto, Differenze } from '../../api/invii'

export const etichetteCampi: Record<keyof Anagrafica, string> = {
  denominazione: 'Denominazione',
  indirizzoSedeLegale: 'Sede legale',
  pec: 'PEC',
  fatturato: 'Fatturato',
  numeroDipendenti: 'Numero di dipendenti',
}

export const totaleDifferenze = <T>(d: Differenze<T>) => d.aggiunti.length + d.rimossi.length + d.modificati.length

export function nessunaDifferenza(c: Confronto): boolean {
  return (
    c.anagrafica.length === 0 &&
    totaleDifferenze(c.elementi) === 0 &&
    totaleDifferenze(c.fornitori) === 0 &&
    totaleDifferenze(c.adempimenti) === 0
  )
}

/** Due id scelti nell'elenco, in ordine cronologico: il confronto va dal piu' vecchio al piu' recente. */
export function ordinePerConfronto(scelti: number[], dateInvio: Map<number, string>): [number, number] | null {
  if (scelti.length !== 2) return null
  const [x, y] = scelti as [number, number]
  return (dateInvio.get(x) ?? '') <= (dateInvio.get(y) ?? '') ? [x, y] : [y, x]
}
