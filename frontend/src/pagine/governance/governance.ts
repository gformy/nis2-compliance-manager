import type { StatoDocumento, Voce } from '../../api/governance'

export const etichetteStatoDocumento: Record<StatoDocumento, string> = {
  MANCANTE: 'Mancante',
  DA_APPROVARE: 'Da approvare',
  VALIDO: 'Valido',
  RIESAME_IN_SCADENZA: 'Riesame in scadenza',
  RIESAME_SCADUTO: 'Riesame scaduto',
}

export function coloreStatoDocumento(stato: StatoDocumento): 'success' | 'warning' | 'error' | 'info' | 'default' {
  switch (stato) {
    case 'VALIDO':
      return 'success'
    case 'RIESAME_IN_SCADENZA':
      return 'warning'
    case 'RIESAME_SCADUTO':
      return 'error'
    case 'DA_APPROVARE':
      return 'info'
    default:
      return 'default'
  }
}

/** Separa le politiche (GV.PO-01) dagli altri documenti, mantenendo l'ordine del backend. */
export function dividiPerCategoria(voci: Voce[]) {
  return {
    politiche: voci.filter((v) => v.categoria === 'POLITICA'),
    documenti: voci.filter((v) => v.categoria === 'DOCUMENTO'),
  }
}

export function dimensioneLeggibile(byte: number): string {
  if (byte < 1024) return `${byte} B`
  if (byte < 1024 * 1024) return `${(byte / 1024).toFixed(0)} KB`
  return `${(byte / 1024 / 1024).toFixed(1).replace('.', ',')} MB`
}

export const oggiIso = () => {
  const d = new Date()
  const due = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${due(d.getMonth() + 1)}-${due(d.getDate())}`
}
