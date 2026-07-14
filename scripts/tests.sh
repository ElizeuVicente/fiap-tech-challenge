#!/usr/bin/env bash

set -Eeuo pipefail

echo "== Oficina Tech Challenge | Testes automatizados =="
echo
echo "1/3 - Versao Java"
java -version
echo

echo "2/3 - Versao Maven Wrapper"
./mvnw --version
echo

echo "3/3 - Executando suite com cobertura"
./mvnw verify
echo

echo "Testes finalizados com sucesso."
