output "cluster_name" {
  description = "EKS cluster name"
  value       = aws_eks_cluster.main.name
}

output "cluster_endpoint" {
  description = "EKS API server endpoint"
  value       = aws_eks_cluster.main.endpoint
}

output "configure_kubectl" {
  description = "Run this command to configure kubectl after terraform apply"
  value       = "aws eks update-kubeconfig --name ${aws_eks_cluster.main.name} --region ${var.aws_region}"
}

output "github_actions_role_arn" {
  description = "IAM role ARN that GitHub Actions assumes for deployments"
  value       = aws_iam_role.github_actions.arn
}

output "aws_account_id" {
  description = "AWS account ID — add this as GitHub secret AWS_ACCOUNT_ID"
  value       = data.aws_caller_identity.current.account_id
}
