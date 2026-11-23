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

Servono Docker, Java 25, Maven 3.9 e Node.js 22.

```bash
# 1. Database e Keycloak
cd infra
cp .env.example .env        # poi cambia le password
docker compose up -d

# 2. Backend: applica le migrazioni Flyway e avvia le API su :8080
cd ../backend
set -a; source ../infra/.env; set +a
mvn spring-boot:run

# 3. Frontend su :5173
cd ../frontend
npm install
npm run dev
```

I test del backend (`mvn verify`) avviano da soli un Oracle Database Free in container con Testcontainers.

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
| `R__permessi_utente_applicativo` | Permessi minimi dell'utente con cui si collega il backend |

Il backend usa due utenti Oracle: `nis2_owner` possiede lo schema e lo usa solo Flyway; `nis2_app` legge e scrive i dati, senza poter cambiare la struttura né cancellare asset e servizi.

## Stato

In sviluppo. La roadmap è divisa in tappe; ogni tappa arriva con una pull request.

## Licenza

[PolyForm Noncommercial 1.0.0](LICENSE.md): il codice si può leggere, studiare e usare per scopi non commerciali. Per l'uso commerciale serve un accordo con l'autore.
