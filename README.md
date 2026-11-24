# NIS2 Compliance Manager

Applicazione web per i soggetti NIS2 italiani: registro di asset, servizi e fornitori con storico delle versioni, stato di adempimento delle misure di sicurezza ACN, gestione degli incidenti con le scadenze di 24 e 72 ore ed export del profilo ACN come era al momento di ogni invio.

Il database nasce dal Project Work 19 del corso di laurea in Informatica per le aziende digitali:
[gformy/pw_19_sicurezza_informatica](https://github.com/gformy/pw_19_sicurezza_informatica).

## Stack

| Livello | Tecnologia |
| --- | --- |
| Frontend | React, TypeScript, Vite |
| Backend | Java 25, Spring Boot 4 |
| Database | Oracle Database Free, Flyway |
| Identità | Keycloak (OpenID Connect) |
| CI | GitHub Actions |

## Avvio in locale

Servono Docker, Java 25 e Node.js 22. Maven non serve: lo scarica `./mvnw` (Maven Wrapper).

```bash
# 1. Database e Keycloak
cd infra
cp .env.example .env        # poi cambia le password
docker compose up -d

# 2. Backend: applica le migrazioni Flyway e avvia le API su :8080
#    (il profilo demo aggiunge due soggetti di esempio)
cd ../backend
set -a; source ../infra/.env; set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo

# 3. Frontend su :5173
cd ../frontend
npm install
npm run dev
```

Apri http://localhost:5173 e accedi con un utente dimostrativo. Al primo accesso Keycloak chiede di cambiare la password.

| Utente | Password iniziale | Ruolo |
| --- | --- | --- |
| `admin.demo` | `Demo-Nis2-2026` | Amministratore della piattaforma |
| `operatore.demo` | `Demo-Nis2-2026` | Operatore sull'azienda Alfa |
| `revisore.demo` | `Demo-Nis2-2026` | Revisore (sola lettura) sull'azienda Alfa |

Il profilo demo carica anche, per l'azienda Alfa:

- l'inventario: quattro asset, due servizi e i loro collegamenti;
- tre fornitori con le relative dipendenze;
- le valutazioni di quattro requisiti ACN;
- due incidenti: uno chiuso, con tutte le trasmissioni al CSIRT, e uno aperto da poche ore, con la pre-notifica in scadenza;
- un primo invio ACN.

Registrare un invio spetta al referente NIS. Tra gli utenti demo può farlo solo `admin.demo`: modifica qualcosa nell'inventario, registra un secondo invio e confronta i due dalla scheda *Invii ACN*.

Ogni utente vede solo i soggetti a cui è assegnato: `operatore.demo` e `revisore.demo` vedono l'azienda Alfa ma non il Comune di Esempio. L'amministratore vede tutto e assegna gli accessi dalla pagina del soggetto.

Gli utenti si trovano in `infra/keycloak/nis2-realm.json` e servono solo in locale.

I test del backend (`./mvnw verify`) avviano da soli un Oracle Database Free in container con Testcontainers.

## Struttura

| Cartella | Contenuto |
| --- | --- |
| `backend/` | API Spring Boot; migrazioni Flyway in `src/main/resources/db/migration` |
| `frontend/` | Applicazione React |
| `infra/` | Docker Compose, script di inizializzazione Oracle, realm Keycloak |

## Database

Lo schema è quello del PW 19, applicato da Flyway:

| Migrazione | Cosa fa |
| --- | --- |
| `V1__schema_pw19` | Tabelle, viste e trigger del PW, invariati; aggiunta solo la riga `/` dopo i blocchi PL/SQL |
| `V2__dati_normativi` | Dati di lookup del PW (`popolamento_dati.sql`) |
| `V3__data_creazione` | Data di creazione sulle tabelle storicizzate, così le ricostruzioni AS OF non mostrano righe nate dopo l'invio |
| `V4__audit_utente_applicativo` | I trigger di storico registrano la persona collegata al backend e non l'utente tecnico |
| `V5__utente_soggetto` | Assegnazione di utenti e ruoli ai soggetti: la base dell'isolamento |
| `V6__codici_cpv` | Catalogo dei codici CPV per i fornitori rilevanti |
| `V7__misure_sicurezza` | Misure di sicurezza e requisiti ACN delle specifiche di base (estratto del PW) |
| `V8__gestione_incidenti` | Titolo, stato e autore degli incidenti; una sola pre-notifica, notifica e relazione finale per incidente |
| `V9__invii_acn` | Autore e note degli invii; viste AS OF dei fornitori e degli adempimenti con chiavi stabili per il confronto |
| `V10__governance_documentale` | Catalogo delle 16 politiche e dei documenti da approvare (Appendice C ACN), periodicità di riesame, file dei documenti in Oracle |
| `R__permessi_utente_applicativo` | Permessi minimi dell'utente con cui si collega il backend |

Il backend usa due utenti Oracle: `nis2_owner` possiede lo schema e lo usa solo Flyway; `nis2_app` legge e scrive i dati, senza poter cambiare la struttura né cancellare asset e servizi.

## Stato

In sviluppo. La roadmap è divisa in tappe; ogni tappa arriva con una pull request.

## Licenza

[PolyForm Noncommercial 1.0.0](LICENSE.md): il codice si può leggere, studiare e usare per scopi non commerciali. Per l'uso commerciale serve un accordo con l'autore.
