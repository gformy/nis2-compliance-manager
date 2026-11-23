import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('mostra il titolo e le aree del prodotto', () => {
    render(<App />)

    expect(
      screen.getByRole('heading', { level: 1, name: 'NIS2 Compliance Manager' }),
    ).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(6)
  })
})
