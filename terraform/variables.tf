variable "region" {
  default = "us-east-1"
}


variable "notification_queue_url" {
  description = "URL of the shared notification SQS queue"
  default     = "https://sqs.us-east-1.amazonaws.com/195802271670/digital-bank-notification-queue"
}
