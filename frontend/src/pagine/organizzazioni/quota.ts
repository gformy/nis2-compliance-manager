import type { Organizzazione } from '../../api/organizzazioni'

/** Percentuale della quota usata; null se il piano e' illimitato. */
export function quotaUsata(o: Pick<Organizzazione, 'soggetti' | 'piano'>): number | null {
  if (o.piano.maxSoggetti === null) return null
  return Math.min(100, Math.round((o.soggetti / o.piano.maxSoggetti) * 100))
}

export const quotaPiena = (o: Pick<Organizzazione, 'soggetti' | 'piano'>) =>
  o.piano.maxSoggetti !== null && o.soggetti >= o.piano.maxSoggetti

export const descriviQuota = (o: Pick<Organizzazione, 'soggetti' | 'piano'>) =>
  o.piano.maxSoggetti === null ? `${o.soggetti} soggetti` : `${o.soggetti} di ${o.piano.maxSoggetti} soggetti`
