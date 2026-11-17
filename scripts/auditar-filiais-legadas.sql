-- Somente leitura. A concordância histórica é uma pista, não prova de autorização.
SELECT o.id AS ordem_id, o.filial_id AS filial_atual,
       e.branch_id AS filial_execucao, b.branch_id AS filial_billing,
       CASE
         WHEN o.filial_id IS NOT NULL AND ((e.branch_id IS NOT NULL AND e.branch_id <> o.filial_id)
             OR (b.branch_id IS NOT NULL AND b.branch_id <> o.filial_id)) THEN 'CONFLITO_COM_FILIAL_ATUAL'
         WHEN o.filial_id IS NOT NULL THEN 'JA_IDENTIFICADA'
         WHEN e.branch_id IS NOT NULL AND b.branch_id IS NOT NULL AND e.branch_id = b.branch_id THEN 'CANDIDATA_CONCORDANTE'
         WHEN e.branch_id IS NOT NULL AND b.branch_id IS NOT NULL THEN 'CONFLITO'
         ELSE 'EVIDENCIA_INSUFICIENTE'
       END AS situacao
FROM ordem_servico o
LEFT JOIN execution_outbox e ON e.order_id = o.id
LEFT JOIN billing_quote_requests b ON b.order_id = o.id
ORDER BY o.id;
