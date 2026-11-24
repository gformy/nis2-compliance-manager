import { describe, expect, it } from 'vitest'
import type { Voce } from '../../api/governance'
import { coloreStatoDocumento, dimensioneLeggibile, dividiPerCategoria } from './governance'

const voce = (codice: string, categoria: Voce['categoria']): Voce => ({
  codice,
  nome: codice,
  categoria,
  ambito: null,
  stato: 'MANCANTE',
  documento: null,
})

describe('dividiPerCategoria', () => {
  it('separa politiche e documenti senza cambiare l ordine', () => {
    const { politiche, documenti } = dividiPerCategoria([
      voce('GV.PO-01-a', 'POLITICA'),
      voce('GV.RR-02', 'DOCUMENTO'),
      voce('GV.PO-01-b', 'POLITICA'),
    ])
    expect(politiche.map((v) => v.codice)).toEqual(['GV.PO-01-a', 'GV.PO-01-b'])
    expect(documenti.map((v) => v.codice)).toEqual(['GV.RR-02'])
  })
})

describe('presentazione', () => {
  it('colora gli stati e formatta le dimensioni', () => {
    expect(coloreStatoDocumento('RIESAME_SCADUTO')).toBe('error')
    expect(coloreStatoDocumento('VALIDO')).toBe('success')
    expect(dimensioneLeggibile(2048)).toBe('2 KB')
    expect(dimensioneLeggibile(3 * 1024 * 1024)).toBe('3,0 MB')
  })
})
