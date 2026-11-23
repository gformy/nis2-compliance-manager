const aree = [
  'Anagrafica del soggetto',
  'Inventario di asset e servizi',
  'Fornitori e dipendenze',
  'Conformità alle misure ACN',
  'Incidenti e scadenze',
  'Invii ACN e profilo AS OF',
]

export default function App() {
  return (
    <main className="pagina">
      <h1>NIS2 Compliance Manager</h1>
      <p>
        Gestione della conformità NIS2 per i soggetti essenziali e importanti: un
        registro unico con lo storico di ogni modifica.
      </p>
      <h2>Aree in sviluppo</h2>
      <ul>
        {aree.map((area) => (
          <li key={area}>{area}</li>
        ))}
      </ul>
    </main>
  )
}
