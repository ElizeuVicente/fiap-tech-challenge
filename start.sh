#!/bin/bash

echo "🚀 Iniciando o Sistema de Oficina (Banco de Dados + API)..."

# Verifica se o Docker está rodando
if ! docker info > /dev/null 2>&1; then
    echo "❌ Erro: O Docker não parece estar rodando. Por favor, inicie o Docker e tente novamente."
    exit 1
fi

# Detecta se deve usar 'docker compose' (V2) ou 'docker-compose' (V1)
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
elif docker-compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker-compose"
else
    echo "❌ Erro: docker compose não encontrado. Por favor, instale o Docker Compose."
    exit 1
fi

# Sobe os containers
echo "📦 Subindo containers via $DOCKER_COMPOSE..."
$DOCKER_COMPOSE up --build -d

echo "⏳ Aguardando a API ficar pronta..."
# Aguarda até que a API responda na porta 8080
until curl -s http://localhost:8080/ > /dev/null; do
    printf '.'
    sleep 5
done

echo -e "\n✅ Sistema iniciado com sucesso!"
echo "📖 Documentação Swagger disponível em: http://localhost:8080/swagger-ui.html"
echo "📊 Banco de Dados PostgreSQL rodando na porta 5432"
