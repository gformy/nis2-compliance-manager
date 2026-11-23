#!/bin/bash
# ---------------------------------------------------------------------
# Eseguito una sola volta, al primo avvio del container Oracle
# (cartella /container-entrypoint-initdb.d dell'immagine gvenzl/oracle-free).
#
# Il proprietario dello schema (APP_USER, nis2_owner) lo crea l'immagine.
# Qui si crea l'utente con cui si collega il backend: solo CREATE SESSION.
# I permessi sulle tabelle li concede la migrazione Flyway
# R__permessi_utente_applicativo.sql, eseguita dal proprietario.
# ---------------------------------------------------------------------
set -euo pipefail

: "${NIS2_APP_USER:=nis2_app}"
: "${NIS2_APP_PASSWORD:?Variabile NIS2_APP_PASSWORD obbligatoria}"

sqlplus -s / as sysdba <<EOF
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = FREEPDB1;
CREATE USER ${NIS2_APP_USER} IDENTIFIED BY "${NIS2_APP_PASSWORD}";
GRANT CREATE SESSION TO ${NIS2_APP_USER};
EXIT;
EOF
