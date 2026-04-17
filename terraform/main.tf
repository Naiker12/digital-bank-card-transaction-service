resource "aws_dynamodb_table" "card_table" {
  name           = "card-table"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "uuid"
  range_key      = "createdAt"

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
    name               = "UserIdIndex"
    hash_key           = "user_id"
    range_key          = "createdAt"
    projection_type    = "ALL"
  }
}

resource "aws_dynamodb_table" "transaction_table" {
  name           = "transaction-table"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "uuid"
  range_key      = "createdAt"

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
    name               = "CardIdIndex"
    hash_key           = "cardId"
    range_key          = "createdAt"
    projection_type    = "ALL"
  }
}


resource "random_id" "bucket_suffix" {
  byte_length = 4
}

resource "aws_s3_bucket" "transactions_reports" {
  bucket = "transactions-report-bucket-${random_id.bucket_suffix.hex}"
}


resource "aws_sqs_queue" "create_card_dlq" {
  name = "error-create-request-card-sqs"
}

resource "aws_sqs_queue" "create_card_queue" {
  name = "create-request-card-sqs"
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.create_card_dlq.arn
    maxReceiveCount     = 3
  })
}


resource "aws_lambda_function" "create_card" {
  filename      = local.jar_path
  function_name = "create-request-card-lambda"
  role          = var.lambda_role_arn
  handler       = "lambdas.createRequestCardLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30
  source_code_hash = filebase64sha256(local.jar_path)

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      NOTIFICATION_QUEUE_URL = var.notification_queue_url
    }
  }
}

resource "aws_lambda_function" "card_purchase" {
  filename      = local.jar_path
  function_name = "card-purchase-lambda"
  role          = var.lambda_role_arn
  handler       = "lambdas.cardPurchaseLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30
  source_code_hash = filebase64sha256(local.jar_path)

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      TRANSACTION_TABLE_NAME = aws_dynamodb_table.transaction_table.name
      NOTIFICATION_QUEUE_URL = var.notification_queue_url
    }
  }
}

resource "aws_lambda_function" "card_activate" {
  filename      = local.jar_path
  function_name = "card-activate-lambda"
  role          = var.lambda_role_arn
  handler       = "lambdas.cardActivateLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30
  source_code_hash = filebase64sha256(local.jar_path)

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      NOTIFICATION_QUEUE_URL = var.notification_queue_url
    }
  }
}

resource "aws_lambda_function" "transaction_save" {
  filename      = local.jar_path
  function_name = "bank-transaction-save-lambda"
  role          = var.lambda_role_arn
  handler       = "lambdas.transactionSaveLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30
  source_code_hash = filebase64sha256(local.jar_path)

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      TRANSACTION_TABLE_NAME = aws_dynamodb_table.transaction_table.name
      NOTIFICATION_QUEUE_URL = var.notification_queue_url
    }
  }
}

resource "aws_lambda_function" "card_paid" {
  filename      = local.jar_path
  function_name = "card-paid-lambda"
  role          = var.lambda_role_arn
  handler       = "lambdas.cardPaidLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30
  source_code_hash = filebase64sha256(local.jar_path)

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      TRANSACTION_TABLE_NAME = aws_dynamodb_table.transaction_table.name
      NOTIFICATION_QUEUE_URL = var.notification_queue_url
    }
  }
}

resource "aws_lambda_function" "card_report" {
  filename      = local.jar_path
  function_name = "card-get-report-lambda"
  role          = var.lambda_role_arn
  handler       = "lambdas.cardGetReportLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30
  source_code_hash = filebase64sha256(local.jar_path)

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      TRANSACTION_TABLE_NAME = aws_dynamodb_table.transaction_table.name
      NOTIFICATION_QUEUE_URL = var.notification_queue_url
    }
  }
}


resource "aws_apigatewayv2_integration" "activate_card" {
  api_id           = var.api_gateway_id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.card_activate.invoke_arn
}

resource "aws_apigatewayv2_route" "activate_card_route" {
  api_id    = var.api_gateway_id
  route_key = "POST /card/activate"
  target    = "integrations/${aws_apigatewayv2_integration.activate_card.id}"
}

resource "aws_apigatewayv2_integration" "purchase" {
  api_id           = var.api_gateway_id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.card_purchase.invoke_arn
}

resource "aws_apigatewayv2_route" "purchase_route" {
  api_id    = var.api_gateway_id
  route_key = "POST /transactions/purchase"
  target    = "integrations/${aws_apigatewayv2_integration.purchase.id}"
}

resource "aws_apigatewayv2_integration" "save_transaction" {
  api_id           = var.api_gateway_id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.transaction_save.invoke_arn
}

resource "aws_apigatewayv2_route" "save_transaction_route" {
  api_id    = var.api_gateway_id
  route_key = "POST /transactions/save/{card_id}"
  target    = "integrations/${aws_apigatewayv2_integration.save_transaction.id}"
}

resource "aws_apigatewayv2_integration" "card_paid" {
  api_id           = var.api_gateway_id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.card_paid.invoke_arn
}

resource "aws_apigatewayv2_route" "card_paid_route" {
  api_id    = var.api_gateway_id
  route_key = "POST /card/paid/{card_id}"
  target    = "integrations/${aws_apigatewayv2_integration.card_paid.id}"
}

resource "aws_apigatewayv2_integration" "card_report" {
  api_id           = var.api_gateway_id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.card_report.invoke_arn
}

resource "aws_apigatewayv2_route" "card_report_route" {
  api_id    = var.api_gateway_id
  route_key = "GET /card/{card_id}"
  target    = "integrations/${aws_apigatewayv2_integration.card_report.id}"
}


resource "aws_lambda_permission" "api_gw_activate" {
  statement_id  = "AllowExecutionFromAPIGatewayActivate"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_activate.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${var.api_gateway_execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_purchase" {
  statement_id  = "AllowExecutionFromAPIGatewayPurchase"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_purchase.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${var.api_gateway_execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_save" {
  statement_id  = "AllowExecutionFromAPIGatewaySave"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.transaction_save.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${var.api_gateway_execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_paid" {
  statement_id  = "AllowExecutionFromAPIGatewayPaid"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_paid.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${var.api_gateway_execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_report" {
  statement_id  = "AllowAPIGwReport"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_report.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${var.api_gateway_execution_arn}/*/*"
}


resource "aws_lambda_event_source_mapping" "sqs_create_card" {
  event_source_arn = aws_sqs_queue.create_card_queue.arn
  function_name    = aws_lambda_function.create_card.arn
}
