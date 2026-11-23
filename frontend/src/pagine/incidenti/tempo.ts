import type { StatoScadenza } from '../../api/incidenti'

/** Valore per <input type="datetime-local"> nell'ora locale del browser. */
export function perInputLocale(iso: string | Date): string {
  const d = typeof iso === 'string' ? new Date(iso) : iso
  const due = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${due(d.getMonth() + 1)}-${due(d.getDate())}T${due(d.getHours())}:${due(d.getMinutes())}`
}

/** Da <input type="datetime-local"> (ora locale) a istante ISO in UTC per il backend. */
export const daInputLocale = (valore: string) => new Date(valore).toISOString()

/** "mancano 5 h 20 min", "scaduta da 2 g 3 h" */
export function tempoResiduo(scadenza: string, adesso: number): string {
  const differenza = new Date(scadenza).getTime() - adesso
  const minuti = Math.floor(Math.abs(differenza) / 60_000)
  const giorni = Math.floor(minuti / 1440)
  const ore = Math.floor((minuti % 1440) / 60)
  const min = minuti % 60
  const durata = giorni > 0 ? `${giorni} g ${ore} h` : ore > 0 ? `${ore} h ${min} min` : `${min} min`
  return differenza >= 0 ? `mancano ${durata}` : `scaduta da ${durata}`
}

export const etichetteScadenza: Record<StatoScadenza, string> = {
  IN_ATTESA: 'Non ancora decorrente',
  IN_TEMPO: 'In tempo',
  IN_SCADENZA: 'In scadenza',
  SCADUTA: 'Scaduta',
  TRASMESSA: 'Trasmessa',
  TRASMESSA_IN_RITARDO: 'Trasmessa in ritardo',
}

export function coloreScadenza(stato: StatoScadenza): 'success' | 'warning' | 'error' | 'info' | 'default' {
  switch (stato) {
    case 'TRASMESSA':
      return 'success'
    case 'IN_SCADENZA':
    case 'TRASMESSA_IN_RITARDO':
      return 'warning'
    case 'SCADUTA':
      return 'error'
    case 'IN_TEMPO':
      return 'info'
    default:
      return 'default'
  }
}
