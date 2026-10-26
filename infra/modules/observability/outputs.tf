output "prometheus_workspace_arn" {
  value = aws_prometheus_workspace.this.arn
}

output "prometheus_remote_write_url" {
  value = "${aws_prometheus_workspace.this.prometheus_endpoint}api/v1/remote_write"
}

output "alerts_topic_arn" {
  value = aws_sns_topic.alerts.arn
}

output "grafana_url" {
  value = var.enable_managed_grafana ? "https://${aws_grafana_workspace.this[0].endpoint}" : null
}
