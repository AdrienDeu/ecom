# Métriques applicatives dans Amazon Managed Prometheus (alimenté par le collecteur ADOT de la tâche), avec les
# mêmes règles d'alerte qu'en local ; alarmes CloudWatch pour l'infrastructure. Tout aboutit dans un topic SNS.

data "aws_region" "current" {}

resource "aws_sns_topic" "alerts" {
  name              = "${var.name}-alerts"
  kms_master_key_id = var.kms_key_arn
}

resource "aws_sns_topic_subscription" "email" {
  for_each = toset(var.alert_emails)

  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = each.value
}

data "aws_iam_policy_document" "alerts_topic" {
  statement {
    sid       = "PrometheusAlertmanager"
    actions   = ["sns:Publish", "sns:GetTopicAttributes"]
    resources = [aws_sns_topic.alerts.arn]

    principals {
      type        = "Service"
      identifiers = ["aps.amazonaws.com"]
    }

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_prometheus_workspace.this.arn]
    }
  }

  statement {
    sid       = "CloudWatchAlarms"
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.alerts.arn]

    principals {
      type        = "Service"
      identifiers = ["cloudwatch.amazonaws.com"]
    }
  }
}

resource "aws_sns_topic_policy" "alerts" {
  arn    = aws_sns_topic.alerts.arn
  policy = data.aws_iam_policy_document.alerts_topic.json
}

# --- Amazon Managed Prometheus ---

resource "aws_cloudwatch_log_group" "prometheus" {
  name              = "/ecom/${var.name}/prometheus"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
}

resource "aws_prometheus_workspace" "this" {
  alias       = var.name
  kms_key_arn = var.kms_key_arn

  logging_configuration {
    log_group_arn = "${aws_cloudwatch_log_group.prometheus.arn}:*"
  }
}

resource "aws_prometheus_rule_group_namespace" "ecom" {
  name         = "ecom"
  workspace_id = aws_prometheus_workspace.this.id
  data         = var.alert_rules
}

resource "aws_prometheus_alert_manager_definition" "this" {
  workspace_id = aws_prometheus_workspace.this.id
  definition = yamlencode({
    alertmanager_config = yamlencode({
      route = {
        receiver        = "sns"
        group_by        = ["alertname", "provider"]
        group_wait      = "30s"
        group_interval  = "5m"
        repeat_interval = "4h"
      }
      receivers = [{
        name = "sns"
        sns_configs = [{
          topic_arn = aws_sns_topic.alerts.arn
          sigv4     = { region = data.aws_region.current.region }
          subject   = "[ecom ${var.name}] {{ .CommonLabels.alertname }}"
          attributes = {
            severity = "{{ .CommonLabels.severity }}"
          }
        }]
      }]
    })
  })

  depends_on = [aws_sns_topic_policy.alerts]
}

# --- Alarmes d'infrastructure (CloudWatch) ---

locals {
  alarms = {
    alb-5xx = {
      description = "L'ALB renvoie des erreurs 5xx (aucune tâche saine, timeouts)"
      namespace   = "AWS/ApplicationELB"
      metric      = "HTTPCode_ELB_5XX_Count"
      statistic   = "Sum"
      comparison  = "GreaterThanThreshold"
      threshold   = 10
      dimensions  = { LoadBalancer = var.alb_arn_suffix }
    }
    unhealthy-targets = {
      description = "Des tâches échouent à la sonde de readiness"
      namespace   = "AWS/ApplicationELB"
      metric      = "UnHealthyHostCount"
      statistic   = "Maximum"
      comparison  = "GreaterThanThreshold"
      threshold   = 0
      dimensions  = { LoadBalancer = var.alb_arn_suffix, TargetGroup = var.target_group_arn_suffix }
    }
    running-tasks = {
      description = "Moins de tâches en service que le minimum prévu"
      namespace   = "ECS/ContainerInsights"
      metric      = "RunningTaskCount"
      statistic   = "Minimum"
      comparison  = "LessThanThreshold"
      threshold   = var.min_tasks
      dimensions  = { ClusterName = var.ecs_cluster_name, ServiceName = var.ecs_service_name }
    }
    rds-cpu = {
      description = "CPU de la base au-dessus de 80 %"
      namespace   = "AWS/RDS"
      metric      = "CPUUtilization"
      statistic   = "Average"
      comparison  = "GreaterThanThreshold"
      threshold   = 80
      dimensions  = { DBInstanceIdentifier = var.db_instance_id }
    }
    rds-storage = {
      description = "Moins de 2 Gio de stockage libre sur la base"
      namespace   = "AWS/RDS"
      metric      = "FreeStorageSpace"
      statistic   = "Minimum"
      comparison  = "LessThanThreshold"
      threshold   = 2 * 1024 * 1024 * 1024
      dimensions  = { DBInstanceIdentifier = var.db_instance_id }
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "this" {
  for_each = local.alarms

  alarm_name          = "${var.name}-${each.key}"
  alarm_description   = each.value.description
  namespace           = each.value.namespace
  metric_name         = each.value.metric
  statistic           = each.value.statistic
  comparison_operator = each.value.comparison
  threshold           = each.value.threshold
  dimensions          = each.value.dimensions
  period              = 300
  evaluation_periods  = 2
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# --- Amazon Managed Grafana (optionnel) ---

data "aws_iam_policy_document" "grafana_trust" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["grafana.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "grafana" {
  count = var.enable_managed_grafana ? 1 : 0

  name               = "${var.name}-grafana"
  assume_role_policy = data.aws_iam_policy_document.grafana_trust.json
}

resource "aws_iam_role_policy_attachment" "grafana" {
  for_each = var.enable_managed_grafana ? toset([
    "arn:aws:iam::aws:policy/AmazonPrometheusQueryAccess",
    "arn:aws:iam::aws:policy/CloudWatchReadOnlyAccess",
    "arn:aws:iam::aws:policy/AWSXrayReadOnlyAccess",
  ]) : toset([])

  role       = aws_iam_role.grafana[0].name
  policy_arn = each.value
}

resource "aws_grafana_workspace" "this" {
  count = var.enable_managed_grafana ? 1 : 0

  name                     = var.name
  account_access_type      = "CURRENT_ACCOUNT"
  authentication_providers = ["AWS_SSO"]
  permission_type          = "SERVICE_MANAGED"
  role_arn                 = aws_iam_role.grafana[0].arn
  data_sources             = ["PROMETHEUS", "CLOUDWATCH", "XRAY"]
}
