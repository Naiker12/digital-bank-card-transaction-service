output "card_table_name" {
  value = aws_dynamodb_table.card_table.name
}

output "transaction_table_name" {
  value = aws_dynamodb_table.transaction_table.name
}

output "create_card_queue_url" {
  value = aws_sqs_queue.create_card_queue.url
}

output "transactions_report_bucket" {
  value = aws_s3_bucket.transactions_reports.id
}
