const formatoData = new Intl.DateTimeFormat('it-IT', { dateStyle: 'short', timeStyle: 'short' })

export const data = (valore: string | null, seVuota = '—') => (valore ? formatoData.format(new Date(valore)) : seVuota)
