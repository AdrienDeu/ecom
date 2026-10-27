# Service ECS Fargate : l'application et son collecteur ADOT dans la même tâche.

data "aws_region" "current" {}

resource "aws_ecs_cluster" "this" {
  name = var.name

  setting {
    name  = "containerInsights"
    value = "enhanced"
  }
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecom/${var.name}/app"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
}

resource "aws_cloudwatch_log_group" "adot" {
  name              = "/ecom/${var.name}/adot"
  retention_in_days = 7
  kms_key_id        = var.kms_key_arn
}

# --- IAM ---

data "aws_iam_policy_document" "ecs_tasks_trust" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

# Rôle d'exécution : utilisé par ECS pour démarrer la tâche (image, journaux, secrets)
resource "aws_iam_role" "execution" {
  name               = "${var.name}-task-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = var.secret_arns
  }

  statement {
    actions   = ["kms:Decrypt"]
    resources = [var.kms_key_arn]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "read-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

# Rôle de la tâche : ce que l'application et le collecteur appellent eux-mêmes
resource "aws_iam_role" "task" {
  name               = "${var.name}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_trust.json
}

data "aws_iam_policy_document" "task" {
  statement {
    sid       = "PrometheusRemoteWrite"
    actions   = ["aps:RemoteWrite"]
    resources = [var.prometheus_workspace_arn]
  }

  statement {
    sid = "XRayTraces"
    actions = [
      "xray:PutTraceSegments",
      "xray:PutTelemetryRecords",
      "xray:GetSamplingRules",
      "xray:GetSamplingTargets",
    ]
    resources = ["*"]
  }

  dynamic "statement" {
    for_each = length(var.ses_identity_arns) > 0 ? [1] : []

    content {
      sid       = "SesNotifications"
      actions   = ["ses:SendEmail"]
      resources = var.ses_identity_arns
    }
  }

  dynamic "statement" {
    for_each = var.enable_execute_command ? [1] : []

    content {
      sid = "EcsExec"
      actions = [
        "ssmmessages:CreateControlChannel",
        "ssmmessages:CreateDataChannel",
        "ssmmessages:OpenControlChannel",
        "ssmmessages:OpenDataChannel",
      ]
      resources = ["*"]
    }
  }
}

resource "aws_iam_role_policy" "task" {
  name   = "observability"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task.json
}

# --- Tâche ---

locals {
  adot_config = yamlencode({
    extensions = {
      health_check = {}
      sigv4auth    = { region = data.aws_region.current.region, service = "aps" }
    }
    receivers = {
      otlp = { protocols = { http = { endpoint = "0.0.0.0:4318" } } }
      prometheus = {
        config = {
          global = { scrape_interval = "30s" }
          scrape_configs = [{
            # Même nom de job qu'en local : les règles d'alerte s'appuient dessus (up{job="ecom"})
            job_name       = "ecom"
            metrics_path   = "/actuator/prometheus"
            static_configs = [{ targets = ["localhost:8081"] }]
          }]
        }
      }
    }
    processors = {
      batch             = {}
      resourcedetection = { detectors = ["env", "ecs"] }
    }
    exporters = {
      awsxray = { region = data.aws_region.current.region }
      prometheusremotewrite = {
        endpoint = var.prometheus_remote_write_url
        auth     = { authenticator = "sigv4auth" }
      }
    }
    service = {
      extensions = ["health_check", "sigv4auth"]
      pipelines = {
        traces  = { receivers = ["otlp"], processors = ["resourcedetection", "batch"], exporters = ["awsxray"] }
        metrics = { receivers = ["prometheus"], processors = ["batch"], exporters = ["prometheusremotewrite"] }
      }
    }
  })

  app_environment = merge(var.environment, {
    OTEL_EXPORTER_OTLP_TRACES_ENDPOINT = "http://localhost:4318/v1/traces"
  })
}

resource "aws_ecs_task_definition" "app" {
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  # Système de fichiers en lecture seule : seul /tmp est inscriptible (Tomcat y écrit)
  volume {
    name = "tmp"
  }

  container_definitions = jsonencode([
    {
      name                   = "app"
      image                  = var.image
      essential              = true
      readonlyRootFilesystem = true
      portMappings = [
        { containerPort = 8080, protocol = "tcp", name = "http" },
        { containerPort = 8081, protocol = "tcp", name = "management" },
      ]
      environment = [for key in sort(keys(local.app_environment)) : { name = key, value = local.app_environment[key] }]
      secrets     = [for key in sort(keys(var.secrets)) : { name = key, valueFrom = var.secrets[key] }]
      mountPoints = [{ sourceVolume = "tmp", containerPath = "/tmp" }]
      dependsOn   = [{ containerName = "adot", condition = "START" }]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.app.name
          awslogs-region        = data.aws_region.current.region
          awslogs-stream-prefix = "app"
        }
      }
    },
    {
      name      = "adot"
      image     = var.adot_image
      essential = false # une panne du collecteur ne doit pas arrêter la boutique
      command   = ["--config=env:AOT_CONFIG_CONTENT"]
      environment = [
        { name = "AOT_CONFIG_CONTENT", value = local.adot_config },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.adot.name
          awslogs-region        = data.aws_region.current.region
          awslogs-stream-prefix = "adot"
        }
      }
    },
  ])
}

# --- Alarmes de déploiement ---

# Surveillées par ECS pendant et juste après un déploiement : en alarme, retour à la révision précédente.
# Plus réactives que les alarmes de notification (module observability), sans action SNS.
locals {
  deployment_alarms = {
    target-5xx = {
      description        = "Erreurs 5xx renvoyées par l'application"
      metric             = "HTTPCode_Target_5XX_Count"
      statistic          = "Sum"
      extended_statistic = null
      threshold          = 20
    }
    target-latency-p95 = {
      description        = "p95 du temps de réponse de l'application au-dessus de 1 s"
      metric             = "TargetResponseTime"
      statistic          = null
      extended_statistic = "p95"
      threshold          = 1
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "deployment" {
  for_each = local.deployment_alarms

  alarm_name          = "${var.name}-deployment-${each.key}"
  alarm_description   = "Rollback de déploiement ECS : ${each.value.description}"
  namespace           = "AWS/ApplicationELB"
  metric_name         = each.value.metric
  statistic           = each.value.statistic
  extended_statistic  = each.value.extended_statistic
  comparison_operator = "GreaterThanThreshold"
  threshold           = each.value.threshold
  period              = 60
  evaluation_periods  = 3
  datapoints_to_alarm = 2
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.this.arn_suffix
    TargetGroup  = aws_lb_target_group.app.arn_suffix
  }
}

resource "aws_ecs_service" "app" {
  name                   = "app"
  cluster                = aws_ecs_cluster.this.id
  task_definition        = aws_ecs_task_definition.app.arn
  desired_count          = var.min_tasks
  launch_type            = "FARGATE"
  propagate_tags         = "SERVICE"
  enable_execute_command = var.enable_execute_command

  # Spring Boot démarre en une vingtaine de secondes ; marge pour les migrations Flyway
  health_check_grace_period_seconds  = 120
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  availability_zone_rebalancing      = "ENABLED"

  # Tâches qui ne deviennent pas saines : retour automatique à la version précédente
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # Tâches saines mais application dégradée (erreurs, lenteur) : retour à la révision précédente
  alarms {
    alarm_names = [for alarm in aws_cloudwatch_metric_alarm.deployment : alarm.alarm_name]
    enable      = true
    rollback    = true
  }

  network_configuration {
    subnets          = var.app_subnet_ids
    security_groups  = [var.app_security_group_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "app"
    container_port   = 8080
  }

  lifecycle {
    # Ajusté par l'autoscaling
    ignore_changes = [desired_count]
  }

  depends_on = [aws_lb_listener_rule.from_cloudfront]
}

# --- Autoscaling ---

resource "aws_appautoscaling_target" "app" {
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.app.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.min_tasks
  max_capacity       = var.max_tasks
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${var.name}-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.app.service_namespace
  resource_id        = aws_appautoscaling_target.app.resource_id
  scalable_dimension = aws_appautoscaling_target.app.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value       = 60
    scale_in_cooldown  = 300
    scale_out_cooldown = 60

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}

resource "aws_appautoscaling_policy" "requests" {
  name               = "${var.name}-requests"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.app.service_namespace
  resource_id        = aws_appautoscaling_target.app.resource_id
  scalable_dimension = aws_appautoscaling_target.app.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value       = 500
    scale_in_cooldown  = 300
    scale_out_cooldown = 60

    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${aws_lb.this.arn_suffix}/${aws_lb_target_group.app.arn_suffix}"
    }
  }
}
