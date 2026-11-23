import type { Requisito, StatoAdempimento } from '../../api/conformita'

export interface GruppoMisura {
  codice: string
  categoria: string
  descrizione: string
  requisiti: Requisito[]
}

/** Raggruppa i requisiti per misura mantenendo l'ordine ricevuto dal backend. */
export function raggruppaPerMisura(requisiti: Requisito[]): GruppoMisura[] {
  const gruppi = new Map<string, GruppoMisura>()
  for (const r of requisiti) {
    const gruppo = gruppi.get(r.misuraCodice) ?? {
      codice: r.misuraCodice,
      categoria: r.misuraCategoria,
      descrizione: r.misuraDescrizione,
      requisiti: [],
    }
    gruppo.requisiti.push(r)
    gruppi.set(r.misuraCodice, gruppo)
  }
  return [...gruppi.values()]
}

export function coloreStato(stato: StatoAdempimento | null): 'success' | 'warning' | 'error' | 'default' {
  switch (stato) {
    case 'ATTUATO':
      return 'success'
    case 'PARZIALMENTE_ATTUATO':
      return 'warning'
    case 'NON_ATTUATO':
      return 'error'
    default:
      return 'default'
  }
}
