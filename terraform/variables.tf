variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "us-east-1"
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
  default     = "teletrack360"
}

variable "node_instance_type" {
  description = "EC2 instance type for worker nodes"
  type        = string
  default     = "t3.xlarge"
}

variable "github_repo" {
  description = "GitHub repo that is allowed to deploy (format: owner/repo)"
  type        = string
  default     = "mahmoud-47/teletrack360"
}
