import { describe, expect, it } from 'vitest'
import { schemaAsset, schemaServizio, servizioAForm, servizioDaForm } from './formInventario'

describe('form del servizio', () => {
  it('trasforma i campi vuoti in null e torna indietro senza perdite', () => {
    const dati = servizioDaForm({ nome: ' Portale ', descrizione: '', categoriaRilevanza: '  ', statiMembro: ['IT'] })
    expect(dati).toEqual({ nome: 'Portale', descrizione: null, categoriaRilevanza: null, statiMembro: ['IT'] })
    expect(servizioAForm(dati)).toEqual({ nome: 'Portale', descrizione: '', categoriaRilevanza: '', statiMembro: ['IT'] })
  })

  it('accetta solo stati membri UE', () => {
    const base = { nome: 'Servizio', descrizione: '', categoriaRilevanza: '' }
    expect(schemaServizio.safeParse({ ...base, statiMembro: ['IT', 'FR'] }).success).toBe(true)
    expect(schemaServizio.safeParse({ ...base, statiMembro: ['US'] }).success).toBe(false)
  })
})

describe('form dell asset', () => {
  it('richiede il nome e un tipo previsto dallo schema', () => {
    expect(schemaAsset.safeParse({ nome: 'Server', tipoAsset: 'HARDWARE', rilevante: true }).success).toBe(true)
    expect(schemaAsset.safeParse({ nome: '  ', tipoAsset: 'HARDWARE', rilevante: true }).success).toBe(false)
    expect(schemaAsset.safeParse({ nome: 'Server', tipoAsset: 'STAMPANTE', rilevante: true }).success).toBe(false)
  })
})
