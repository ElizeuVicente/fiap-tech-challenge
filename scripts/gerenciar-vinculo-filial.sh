#!/usr/bin/env bash
set -euo pipefail

# Operação administrativa deliberada; use uma credencial de banco restrita e conexões seguras.
# Credenciais PostgreSQL devem vir do ambiente/arquivo .pgpass, nunca dos argumentos.
if [[ $# -ne 5 || ( "$1" != 'grant' && "$1" != 'revoke' ) ]]; then
  echo 'Uso: gerenciar-vinculo-filial.sh grant|revoke usuario filial-uuid operador motivo' >&2; exit 2
fi
action=$1; username=$2; branch=$3; operator=$4; reason=$5
[[ "$branch" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] || { echo 'Filial inválida' >&2; exit 2; }
[[ -n "$username" && -n "$operator" && -n "$reason" && ${#username} -le 255 && ${#operator} -le 255 && ${#reason} -le 500 ]] || { echo 'Usuário, operador e motivo são obrigatórios' >&2; exit 2; }
command -v psql >/dev/null || { echo 'psql não está disponível' >&2; exit 2; }

psql -X -v ON_ERROR_STOP=1 -v action="$action" -v username="$username" -v branch="$branch" -v operator="$operator" -v reason="$reason" <<'SQL'
BEGIN;
SELECT set_config('oficina.ator_declarado', :'operator', true);
SELECT set_config('oficina.motivo_vinculo', :'reason', true);
SELECT :'action' = 'grant' AS is_grant \gset
\if :is_grant
INSERT INTO usuario_filial(username, filial_id) VALUES (:'username', :'branch'::uuid) ON CONFLICT DO NOTHING;
\else
DELETE FROM usuario_filial WHERE username=:'username' AND filial_id=:'branch'::uuid;
\endif
COMMIT;
SQL
