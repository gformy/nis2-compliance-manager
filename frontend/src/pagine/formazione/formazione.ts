import type { Attivita, Persona } from '../../api/formazione'

/** Persone che possono partecipare a un'attivita': attive e nella categoria dei destinatari. */
export function personeAmmesse(persone: Persona[], attivita: Attivita, data: string): Persona[] {
  return persone.filter(
    (p) =>
      (attivita.destinatari === 'TUTTI' || p.categoria === attivita.destinatari) &&
      (p.dataCessazione === null || p.dataCessazione >= data),
  )
}

/** Esiti ammessi: con una modalita' di verifica servono superata/non superata. */
export const esitiAmmessi = (attivita: Attivita) =>
  attivita.modalitaVerifica ? (['SUPERATA', 'NON_SUPERATA'] as const) : (['NON_PREVISTA'] as const)

export function colorePercentuale(percentuale: number, persone: number): 'success' | 'warning' | 'error' | 'inherit' {
  if (persone === 0) return 'inherit'
  if (percentuale >= 100) return 'success'
  return percentuale >= 50 ? 'warning' : 'error'
}
