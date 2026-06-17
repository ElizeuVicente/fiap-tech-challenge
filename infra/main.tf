resource "kubernetes_namespace" "oficina" {
  metadata {
    name = var.namespace
  }
}

resource "kubernetes_config_map" "app" {
  metadata {
    name      = "oficina-config"
    namespace = kubernetes_namespace.oficina.metadata[0].name
  }

  data = {
    SPRING_DATASOURCE_URL         = "jdbc:postgresql://oficina-postgres:5432/oficina"
    SPRING_DATASOURCE_USERNAME    = "user"
    SPRING_JPA_HIBERNATE_DDL_AUTO = "update"
    SERVER_PORT                   = "8080"
    JAVA_OPTS                     = "-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
  }
}

resource "kubernetes_secret" "app" {
  metadata {
    name      = "oficina-secret"
    namespace = kubernetes_namespace.oficina.metadata[0].name
  }

  data = {
    SPRING_DATASOURCE_PASSWORD = var.postgres_password
    SECURITY_JWT_SECRET        = var.jwt_secret
    EXTERNAL_WEBHOOK_SECRET    = var.webhook_secret
    POSTGRES_PASSWORD          = var.postgres_password
  }

  type = "Opaque"
}

resource "kubernetes_persistent_volume_claim" "postgres" {
  metadata {
    name      = "oficina-postgres-data"
    namespace = kubernetes_namespace.oficina.metadata[0].name
  }

  spec {
    access_modes = ["ReadWriteOnce"]
    resources {
      requests = {
        storage = "1Gi"
      }
    }
  }
}

resource "kubernetes_deployment" "postgres" {
  metadata {
    name      = "oficina-postgres"
    namespace = kubernetes_namespace.oficina.metadata[0].name
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "oficina-postgres"
      }
    }

    template {
      metadata {
        labels = {
          app = "oficina-postgres"
        }
      }

      spec {
        container {
          name  = "postgres"
          image = "postgres:15"

          port {
            container_port = 5432
          }

          env {
            name  = "POSTGRES_DB"
            value = "oficina"
          }

          env {
            name = "POSTGRES_USER"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.app.metadata[0].name
                key  = "SPRING_DATASOURCE_USERNAME"
              }
            }
          }

          env {
            name = "POSTGRES_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.app.metadata[0].name
                key  = "POSTGRES_PASSWORD"
              }
            }
          }

          resources {
            requests = {
              cpu    = "100m"
              memory = "256Mi"
            }
            limits = {
              cpu    = "500m"
              memory = "512Mi"
            }
          }

          volume_mount {
            name       = "postgres-data"
            mount_path = "/var/lib/postgresql/data"
          }
        }

        volume {
          name = "postgres-data"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim.postgres.metadata[0].name
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "postgres" {
  metadata {
    name      = "oficina-postgres"
    namespace = kubernetes_namespace.oficina.metadata[0].name
  }

  spec {
    selector = {
      app = "oficina-postgres"
    }

    port {
      name        = "postgres"
      port        = 5432
      target_port = 5432
    }
  }
}

resource "kubernetes_deployment" "api" {
  metadata {
    name      = "oficina-api"
    namespace = kubernetes_namespace.oficina.metadata[0].name
  }

  spec {
    replicas = 2

    selector {
      match_labels = {
        app = "oficina-api"
      }
    }

    template {
      metadata {
        labels = {
          app = "oficina-api"
        }
      }

      spec {
        container {
          name  = "oficina-api"
          image = var.app_image

          port {
            container_port = 8080
          }

          env_from {
            config_map_ref {
              name = kubernetes_config_map.app.metadata[0].name
            }
          }

          env_from {
            secret_ref {
              name = kubernetes_secret.app.metadata[0].name
            }
          }

          readiness_probe {
            http_get {
              path = "/actuator/health/readiness"
              port = 8080
            }
            initial_delay_seconds = 30
            period_seconds        = 10
          }

          liveness_probe {
            http_get {
              path = "/actuator/health/liveness"
              port = 8080
            }
            initial_delay_seconds = 60
            period_seconds        = 20
          }

          resources {
            requests = {
              cpu    = "250m"
              memory = "512Mi"
            }
            limits = {
              cpu    = "1000m"
              memory = "1Gi"
            }
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "api" {
  metadata {
    name      = "oficina-api"
    namespace = kubernetes_namespace.oficina.metadata[0].name
  }

  spec {
    selector = {
      app = "oficina-api"
    }

    port {
      name        = "http"
      port        = 8080
      target_port = 8080
    }
  }
}

resource "kubernetes_horizontal_pod_autoscaler_v2" "api" {
  metadata {
    name      = "oficina-api"
    namespace = kubernetes_namespace.oficina.metadata[0].name
  }

  spec {
    min_replicas = 2
    max_replicas = 5

    scale_target_ref {
      api_version = "apps/v1"
      kind        = "Deployment"
      name        = kubernetes_deployment.api.metadata[0].name
    }

    metric {
      type = "Resource"
      resource {
        name = "cpu"
        target {
          type                = "Utilization"
          average_utilization = 70
        }
      }
    }

    metric {
      type = "Resource"
      resource {
        name = "memory"
        target {
          type                = "Utilization"
          average_utilization = 75
        }
      }
    }
  }
}
