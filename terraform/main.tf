locals {
  common_tags = {
    Project     = "Digital Bank"
    Service     = "Card-Transaction-Service"
    Environment = "Dev"
    ManagedBy   = "Terraform"
  }
}

resource "aws_dynamodb_table" "card_table" {
  name         = "card-table"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "uuid"
  range_key    = "createdAt"

  attribute {
    name = "uuid"
    type = "S"
  }
  attribute {
    name = "createdAt"
    type = "S"
  }
  attribute {
    name = "user_id"
    type = "S"
  }

  global_secondary_index {
    name            = "UserIdIndex"
    hash_key        = "user_id"
    range_key       = "createdAt"
    projection_type = "ALL"
  }

  tags = local.common_tags
}

resource "aws_dynamodb_table" "transaction_table" {
  name         = "transaction-table"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "uuid"
  range_key    = "createdAt"

  attribute {
    name = "uuid"
    type = "S"
  }
  attribute {
    name = "createdAt"
    type = "S"
  }
  attribute {
    name = "cardId"
    type = "S"
  }

  global_secondary_index {
    name            = "CardIdIndex"
    hash_key        = "cardId"
    range_key       = "createdAt"
    projection_type = "ALL"
  }

  tags = local.common_tags
}


resource "random_id" "bucket_suffix" {
  byte_length = 4
}

resource "aws_s3_bucket" "transactions_reports" {
  bucket        = "transactions-report-bucket-${random_id.bucket_suffix.hex}"
  force_destroy = true
  tags          = local.common_tags
}

resource "aws_s3_bucket" "catalog_services" {
  bucket        = "catalog-services-bucket-${random_id.bucket_suffix.hex}"
  force_destroy = true
  tags          = local.common_tags
}

resource "aws_s3_object" "catalog_seed" {
  bucket       = aws_s3_bucket.catalog_services.id
  key          = "servicios.csv"
  source       = "${path.module}/../servicios.csv"
  content_type = "text/csv"
  etag         = filemd5("${path.module}/../servicios.csv")
}


resource "aws_sqs_queue" "create_card_dlq" {
  name = "error-create-request-card-sqs"
  tags = local.common_tags
}

resource "aws_sqs_queue" "create_card_queue" {
  name = "create-request-card-sqs"
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.create_card_dlq.arn
    maxReceiveCount     = 3
  })
  tags = local.common_tags
}


resource "aws_apigatewayv2_api" "card_api" {
  name          = "bank-card-api"
  protocol_type = "HTTP"
  cors_configuration {
    allow_origins = ["*"]
    allow_methods = ["POST", "GET", "PUT", "DELETE", "OPTIONS"]
    allow_headers = ["Content-Type", "Authorization"]
  }
  tags = local.common_tags
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.card_api.id
  name        = "$default"
  auto_deploy = true
}

resource "aws_lambda_function" "create_card" {
  filename         = local.jar_path
  function_name    = "create-request-card-lambda"
  role             = aws_iam_role.lambda_role.arn
  handler          = "lambdas.createRequestCardLambda::handleRequest"
  runtime          = "java17"
  memory_size      = 512
  timeout          = 30
  source_code_hash = local.jar_hash

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      NOTIFICATION_QUEUE_URL = var.notification_queue_url
      DEPLOY_TIMESTAMP       = "20260417_1311"
    }
  }

  tags = local.common_tags
}

resource "aws_lambda_function" "card_purchase" {
  filename         = local.jar_path
  function_name    = "card-purchase-lambda"
  role             = aws_iam_role.lambda_role.arn
  handler          = "lambdas.cardPurchaseLambda::handleRequest"
  runtime          = "java17"
  memory_size      = 512
  timeout          = 30
  source_code_hash = local.jar_hash

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      TRANSACTION_TABLE_NAME = aws_dynamodb_table.transaction_table.name
      NOTIFICATION_QUEUE_URL = var.notification_queue_url
    }
  }
}

resource "aws_lambda_function" "card_activate" {
  filename         = local.jar_path
  function_name    = "card-activate-lambda"
  role             = aws_iam_role.lambda_role.arn
  handler          = "lambdas.cardActivateLambda::handleRequest"
  runtime          = "java17"
  memory_size      = 512
  timeout          = 30
  source_code_hash = local.jar_hash

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      NOTIFICATION_QUEUE_URL = var.notification_queue_url
    }
  }

  tags = local.common_tags
}

resource "aws_lambda_function" "transaction_save" {
  filename         = local.jar_path
  function_name    = "bank-transaction-save-lambda"
  role             = aws_iam_role.lambda_role.arn
  handler          = "lambdas.transactionSaveLambda::handleRequest"
  runtime          = "java17"
  memory_size      = 512
  timeout          = 30
  source_code_hash = local.jar_hash

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      TRANSACTION_TABLE_NAME = aws_dynamodb_table.transaction_table.name
      NOTIFICATION_QUEUE_URL = var.notification_queue_url
    }
  }
}

resource "aws_lambda_function" "card_paid" {
  filename         = local.jar_path
  function_name    = "card-paid-lambda"
  role             = aws_iam_role.lambda_role.arn
  handler          = "lambdas.cardPaidLambda::handleRequest"
  runtime          = "java17"
  memory_size      = 512
  timeout          = 30
  source_code_hash = local.jar_hash

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      TRANSACTION_TABLE_NAME = aws_dynamodb_table.transaction_table.name
      NOTIFICATION_QUEUE_URL = var.notification_queue_url
    }
  }
}

resource "aws_lambda_function" "card_report" {
  filename         = local.jar_path
  function_name    = "card-get-report-lambda"
  role             = aws_iam_role.lambda_role.arn
  handler          = "lambdas.cardGetReportLambda::handleRequest"
  runtime          = "java17"
  memory_size      = 512
  timeout          = 30
  source_code_hash = local.jar_hash

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      TRANSACTION_TABLE_NAME = aws_dynamodb_table.transaction_table.name
      NOTIFICATION_QUEUE_URL = var.notification_queue_url
    }
  }
}

resource "aws_lambda_function" "get_user_cards" {
  filename         = local.jar_path
  function_name    = "get-user-cards-lambda"
  role             = aws_iam_role.lambda_role.arn
  handler          = "lambdas.getUserCardsLambda::handleRequest"
  runtime          = "java17"
  memory_size      = 512
  timeout          = 30
  source_code_hash = local.jar_hash

  environment {
    variables = {
      CARD_TABLE_NAME = aws_dynamodb_table.card_table.name
    }
  }
}

resource "aws_lambda_function" "catalog" {
  filename         = local.jar_path
  function_name    = "catalog-lambda"
  role             = aws_iam_role.lambda_role.arn
  handler          = "lambdas.catalogLambda::handleRequest"
  runtime          = "java17"
  memory_size      = 512
  timeout          = 30
  source_code_hash = local.jar_hash

  environment {
    variables = {
      CATALOG_BUCKET_NAME = aws_s3_bucket.catalog_services.id
      CATALOG_FILE_KEY    = aws_s3_object.catalog_seed.key
    }
  }

  tags = local.common_tags
}


resource "aws_apigatewayv2_integration" "activate_card" {
  api_id           = aws_apigatewayv2_api.card_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.card_activate.invoke_arn
}

resource "aws_apigatewayv2_route" "activate_card_route" {
  api_id    = aws_apigatewayv2_api.card_api.id
  route_key = "POST /card/activate"
  target    = "integrations/${aws_apigatewayv2_integration.activate_card.id}"
}

resource "aws_apigatewayv2_integration" "purchase" {
  api_id           = aws_apigatewayv2_api.card_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.card_purchase.invoke_arn
}

resource "aws_apigatewayv2_route" "purchase_route" {
  api_id    = aws_apigatewayv2_api.card_api.id
  route_key = "POST /transactions/purchase"
  target    = "integrations/${aws_apigatewayv2_integration.purchase.id}"
}

resource "aws_apigatewayv2_integration" "save_transaction" {
  api_id           = aws_apigatewayv2_api.card_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.transaction_save.invoke_arn
}

resource "aws_apigatewayv2_route" "save_transaction_route" {
  api_id    = aws_apigatewayv2_api.card_api.id
  route_key = "POST /transactions/save/{card_id}"
  target    = "integrations/${aws_apigatewayv2_integration.save_transaction.id}"
}

resource "aws_apigatewayv2_integration" "card_paid" {
  api_id           = aws_apigatewayv2_api.card_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.card_paid.invoke_arn
}

resource "aws_apigatewayv2_route" "card_paid_route" {
  api_id    = aws_apigatewayv2_api.card_api.id
  route_key = "POST /card/paid/{card_id}"
  target    = "integrations/${aws_apigatewayv2_integration.card_paid.id}"
}

resource "aws_apigatewayv2_integration" "card_report" {
  api_id           = aws_apigatewayv2_api.card_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.card_report.invoke_arn
}

resource "aws_apigatewayv2_route" "card_report_route" {
  api_id    = aws_apigatewayv2_api.card_api.id
  route_key = "GET /card/report/{card_id}"
  target    = "integrations/${aws_apigatewayv2_integration.card_report.id}"
}

resource "aws_apigatewayv2_integration" "get_user_cards" {
  api_id           = aws_apigatewayv2_api.card_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.get_user_cards.invoke_arn
}

resource "aws_apigatewayv2_route" "get_user_cards_route" {
  api_id    = aws_apigatewayv2_api.card_api.id
  route_key = "GET /card/user/{user_id}"
  target    = "integrations/${aws_apigatewayv2_integration.get_user_cards.id}"
}

resource "aws_apigatewayv2_route" "legacy_get_user_cards_route" {
  api_id    = aws_apigatewayv2_api.card_api.id
  route_key = "GET /card/{user_id}"
  target    = "integrations/${aws_apigatewayv2_integration.get_user_cards.id}"
}


resource "aws_apigatewayv2_integration" "catalog" {
  api_id           = aws_apigatewayv2_api.card_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.catalog.invoke_arn
}

resource "aws_apigatewayv2_route" "catalog_get_route" {
  api_id    = aws_apigatewayv2_api.card_api.id
  route_key = "GET /catalog"
  target    = "integrations/${aws_apigatewayv2_integration.catalog.id}"
}

resource "aws_apigatewayv2_route" "catalog_post_route" {
  api_id    = aws_apigatewayv2_api.card_api.id
  route_key = "POST /catalog"
  target    = "integrations/${aws_apigatewayv2_integration.catalog.id}"
}

resource "aws_apigatewayv2_route" "catalog_update_route" {
  api_id    = aws_apigatewayv2_api.card_api.id
  route_key = "POST /catalog/update"
  target    = "integrations/${aws_apigatewayv2_integration.catalog.id}"
}


resource "aws_lambda_permission" "api_gw_activate" {
  statement_id  = "AllowExecutionFromAPIGatewayActivate"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_activate.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.card_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_purchase" {
  statement_id  = "AllowExecutionFromAPIGatewayPurchase"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_purchase.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.card_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_save" {
  statement_id  = "AllowExecutionFromAPIGatewaySave"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.transaction_save.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.card_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_paid" {
  statement_id  = "AllowExecutionFromAPIGatewayPaid"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_paid.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.card_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_report" {
  statement_id  = "AllowAPIGwReport"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_report.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.card_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_user_cards" {
  statement_id  = "AllowAPIGwUserCards"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.get_user_cards.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.card_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_catalog" {
  statement_id  = "AllowAPIGwCatalog"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.catalog.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.card_api.execution_arn}/*/*"
}


resource "aws_lambda_function" "card_info" {
  filename         = local.jar_path
  function_name    = "card-info-lambda"
  role             = aws_iam_role.lambda_role.arn
  handler          = "lambdas.cardInfoLambda::handleRequest"
  runtime          = "java17"
  memory_size      = 512
  timeout          = 30
  source_code_hash = local.jar_hash

  environment {
    variables = {
      CARD_TABLE_NAME = aws_dynamodb_table.card_table.name
    }
  }

  tags = local.common_tags
}

resource "aws_apigatewayv2_integration" "card_info" {
  api_id           = aws_apigatewayv2_api.card_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.card_info.invoke_arn
}

resource "aws_apigatewayv2_route" "card_info_route" {
  api_id    = aws_apigatewayv2_api.card_api.id
  route_key = "GET /card/info/{card_id}"
  target    = "integrations/${aws_apigatewayv2_integration.card_info.id}"
}

resource "aws_lambda_permission" "api_gw_card_info" {
  statement_id  = "AllowAPIGwCardInfo"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_info.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.card_api.execution_arn}/*/*"
}


resource "aws_lambda_event_source_mapping" "sqs_create_card" {
  event_source_arn = aws_sqs_queue.create_card_queue.arn
  function_name    = aws_lambda_function.create_card.arn
}


resource "aws_iam_role" "lambda_role" {
  name = "bank-card-service-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_basic" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_policy" "lambda_access" {
  name        = "bank-card-service-access-policy"
  description = "Permisos para DynamoDB, SQS y S3 para el servicio de tarjetas"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:*",
          "sqs:*",
          "s3:*",
          "ses:*"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "attach_access" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = aws_iam_policy.lambda_access.arn
}


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

output "catalog_services_bucket" {
  value = aws_s3_bucket.catalog_services.id
}

output "api_base_url" {
  value = aws_apigatewayv2_api.card_api.api_endpoint
}
