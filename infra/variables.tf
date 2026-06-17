variable "kubeconfig_path" {
  description = "Caminho do kubeconfig usado pelo provider Kubernetes."
  type        = string
  default     = "~/.kube/config"
}

variable "kubeconfig_context" {
  description = "Contexto Kubernetes. Use null para o contexto atual."
  type        = string
  default     = null
}

variable "namespace" {
  description = "Namespace da aplicação."
  type        = string
  default     = "oficina"
}

variable "app_image" {
  description = "Imagem Docker da API."
  type        = string
  default     = "oficina-tech-challenge:latest"
}

variable "jwt_secret" {
  description = "Secret JWT com pelo menos 256 bits."
  type        = string
  sensitive   = true
}

variable "webhook_secret" {
  description = "Secret do webhook externo de orçamento."
  type        = string
  sensitive   = true
}

variable "postgres_password" {
  description = "Senha do PostgreSQL."
  type        = string
  sensitive   = true
}
