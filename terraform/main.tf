provider "aws" {
  region = "us-east-1"
}

# 1. DynamoDB Tables
resource "aws_dynamodb_table" "user_table" {
  name           = "bank-users"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "uuid"
  range_key      = "document"

  attribute {
    name = "uuid"
    type = "S"
  }
  attribute {
    name = "document"
    type = "S"
  }
}

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

resource "aws_dynamodb_table" "notification_table" {
  name           = "notification-table"
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
}

# 2. SQS Queues & DLQ
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

resource "aws_sqs_queue" "notification_dlq" {
  name = "notification-email-error-sqs"
}

resource "aws_sqs_queue" "notification_queue" {
  name = "notification-email-sqs"
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.notification_dlq.arn
    maxReceiveCount     = 3
  })
}

# 3. S3 Buckets
resource "random_id" "bucket_suffix" {
  byte_length = 4
}

resource "aws_s3_bucket" "user_avatars" {
  bucket = "bank-user-avatars-${random_id.bucket_suffix.hex}"
}

resource "aws_s3_bucket" "transactions_reports" {
  bucket = "transactions-report-bucket-${random_id.bucket_suffix.hex}"
}

resource "aws_s3_bucket" "email_templates" {
  bucket = "templates-email-notification-${random_id.bucket_suffix.hex}"
}

# 4. IAM Role for Lambdas
resource "aws_iam_role" "lambda_execution_role" {
  name = "bank_lambda_execution_role"

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

resource "aws_iam_policy_attachment" "lambda_basic_execution" {
  name       = "lambda_basic_execution"
  roles      = [aws_iam_role.lambda_execution_role.name]
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_policy" "lambda_full_backend_access" {
  name        = "LambdaBackendAccess"
  description = "Allows Lambda to access DynamoDB, SQS, S3 and Secrets Manager"
  policy      = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = [
          "dynamodb:*",
          "sqs:*",
          "s3:*",
          "secretsmanager:*",
          "ses:*"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "attach_backend_access" {
  role       = aws_iam_role.lambda_execution_role.name
  policy_arn = aws_iam_policy.lambda_full_backend_access.arn
}

# 5. Lambda Functions (Automated Deployment)

# ZIP for User Service (Python)
# Genera el ZIP automáticamente desde la carpeta de deployment
data "archive_file" "user_service_zip" {
  type        = "zip"
  source_dir  = "d:/bank-user-service/deployment_package"
  output_path = "${path.module}/user_service.zip"
}

# User Service: Register
resource "aws_lambda_function" "register_user" {
  filename         = data.archive_file.user_service_zip.output_path
  function_name    = "register-user-lambda"
  role             = aws_iam_role.lambda_execution_role.arn
  handler          = "register_user.handler"
  runtime          = "python3.11"
  source_code_hash = data.archive_file.user_service_zip.output_base64sha256

  environment {
    variables = {
      USERS_TABLE            = aws_dynamodb_table.user_table.name
      CARD_QUEUE_URL         = aws_sqs_queue.create_card_queue.url
      NOTIFICATION_QUEUE_URL = aws_sqs_queue.notification_queue.url
      DEPLOY_TIMESTAMP       = "20260309_2210"
    }
  }
}

# User Service: Login
resource "aws_lambda_function" "login_user" {
  filename         = data.archive_file.user_service_zip.output_path
  function_name    = "login-user-lambda"
  role             = aws_iam_role.lambda_execution_role.arn
  handler          = "login_user.handler"
  runtime          = "python3.11"
  source_code_hash = data.archive_file.user_service_zip.output_base64sha256

  environment {
    variables = {
      USERS_TABLE            = aws_dynamodb_table.user_table.name
      JWT_SECRET             = "super-secret-bank-key-2026"
      NOTIFICATION_QUEUE_URL = aws_sqs_queue.notification_queue.url
    }
  }
}

# User Service: Get Profile
resource "aws_lambda_function" "get_profile" {
  filename         = data.archive_file.user_service_zip.output_path
  function_name    = "get-profile-lambda"
  role             = aws_iam_role.lambda_execution_role.arn
  handler          = "get_profile.handler"
  runtime          = "python3.11"
  source_code_hash = data.archive_file.user_service_zip.output_base64sha256

  environment {
    variables = {
      USERS_TABLE = aws_dynamodb_table.user_table.name
      JWT_SECRET  = "super-secret-bank-key-2026"
    }
  }
}

# User Service: Update User
resource "aws_lambda_function" "update_user" {
  filename         = data.archive_file.user_service_zip.output_path
  function_name    = "update-user-lambda"
  role             = aws_iam_role.lambda_execution_role.arn
  handler          = "update_user.handler"
  runtime          = "python3.11"
  source_code_hash = data.archive_file.user_service_zip.output_base64sha256

  environment {
    variables = {
      USERS_TABLE = aws_dynamodb_table.user_table.name
      JWT_SECRET  = "super-secret-bank-key-2026"
    }
  }
}

# User Service: Upload Avatar
resource "aws_lambda_function" "upload_avatar" {
  filename         = data.archive_file.user_service_zip.output_path
  function_name    = "upload-avatar-lambda"
  role             = aws_iam_role.lambda_execution_role.arn
  handler          = "upload_avatar.handler"
  runtime          = "python3.11"
  source_code_hash = data.archive_file.user_service_zip.output_base64sha256

  environment {
    variables = {
      USERS_TABLE = aws_dynamodb_table.user_table.name
      S3_BUCKET   = aws_s3_bucket.user_avatars.id
      JWT_SECRET  = "super-secret-bank-key-2026"
    }
  }
}

# Notification Service (Node.js)
data "archive_file" "notification_service_zip" {
  type        = "zip"
  source_dir  = "d:/Notification Service/dist"
  output_path = "${path.module}/notification_service.zip"
}

resource "aws_lambda_function" "send_notifications" {
  filename         = data.archive_file.notification_service_zip.output_path
  function_name    = "send-notifications-lambda"
  role             = aws_iam_role.lambda_execution_role.arn
  handler          = "handler/sendNotification.handler"
  runtime          = "nodejs20.x"
  source_code_hash = data.archive_file.notification_service_zip.output_base64sha256

  environment {
    variables = {
      SMTP_HOST       = "smtp.gmail.com"
      USER_TABLE      = aws_dynamodb_table.user_table.name
      TEMPLATE_BUCKET = aws_s3_bucket.email_templates.id
      EMAIL_SOURCE    = "naikergomez0123@gmail.com"
    }
  }
}

resource "aws_lambda_function" "send_notifications_error" {
  filename         = data.archive_file.notification_service_zip.output_path
  function_name    = "send-notifications-error-lambda"
  role             = aws_iam_role.lambda_execution_role.arn
  handler          = "handler/sendNotificationError.handler"
  runtime          = "nodejs20.x"
  source_code_hash = data.archive_file.notification_service_zip.output_base64sha256
}

# Triggers: SQS -> Notifications
resource "aws_lambda_event_source_mapping" "sqs_notifications" {
  event_source_arn = aws_sqs_queue.notification_queue.arn
  function_name    = aws_lambda_function.send_notifications.arn
}

resource "aws_lambda_event_source_mapping" "sqs_notifications_error" {
  event_source_arn = aws_sqs_queue.notification_dlq.arn
  function_name    = aws_lambda_function.send_notifications_error.arn
}

# Card Service: Create Card (Java)
resource "aws_lambda_function" "create_card" {
  filename      = "d:/bank-card-transaction-service/target/bank-card-transaction-service-1.0-SNAPSHOT.jar"
  function_name = "create-request-card-lambda"
  role          = aws_iam_role.lambda_execution_role.arn
  handler       = "lambdas.createRequestCardLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30
  source_code_hash = filebase64sha256("d:/bank-card-transaction-service/target/bank-card-transaction-service-1.0-SNAPSHOT.jar")

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      NOTIFICATION_QUEUE_URL = aws_sqs_queue.notification_queue.url
    }
  }
}

# Trigger: SQS -> Create Card
resource "aws_lambda_event_source_mapping" "sqs_create_card" {
  event_source_arn = aws_sqs_queue.create_card_queue.arn
  function_name    = aws_lambda_function.create_card.arn
}

# Card Service: Purchase (Java)
resource "aws_lambda_function" "card_purchase" {
  filename      = "d:/bank-card-transaction-service/target/bank-card-transaction-service-1.0-SNAPSHOT.jar"
  function_name = "card-purchase-lambda"
  role          = aws_iam_role.lambda_execution_role.arn
  handler       = "lambdas.cardPurchaseLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30
  source_code_hash = filebase64sha256("d:/bank-card-transaction-service/target/bank-card-transaction-service-1.0-SNAPSHOT.jar")

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      TRANSACTION_TABLE_NAME = aws_dynamodb_table.transaction_table.name
      NOTIFICATION_QUEUE_URL = aws_sqs_queue.notification_queue.url
    }
  }
}

# Card Service: Activate (Java)
resource "aws_lambda_function" "card_activate" {
  filename      = "d:/bank-card-transaction-service/target/bank-card-transaction-service-1.0-SNAPSHOT.jar"
  function_name = "card-activate-lambda"
  role          = aws_iam_role.lambda_execution_role.arn
  handler       = "lambdas.cardActivateLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30
  source_code_hash = filebase64sha256("d:/bank-card-transaction-service/target/bank-card-transaction-service-1.0-SNAPSHOT.jar")

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      NOTIFICATION_QUEUE_URL = aws_sqs_queue.notification_queue.url
    }
  }
}

# Card Service: Transaction Save / Deposit (Java)
resource "aws_lambda_function" "transaction_save" {
  filename      = "d:/bank-card-transaction-service/target/bank-card-transaction-service-1.0-SNAPSHOT.jar"
  function_name = "bank-transaction-save-lambda"
  role          = aws_iam_role.lambda_execution_role.arn
  handler       = "lambdas.transactionSaveLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30
  source_code_hash = filebase64sha256("d:/bank-card-transaction-service/target/bank-card-transaction-service-1.0-SNAPSHOT.jar")

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      TRANSACTION_TABLE_NAME = aws_dynamodb_table.transaction_table.name
      NOTIFICATION_QUEUE_URL = aws_sqs_queue.notification_queue.url
    }
  }
}

# Card Service: Card Paid (Java)
resource "aws_lambda_function" "card_paid" {
  filename      = "d:/bank-card-transaction-service/target/bank-card-transaction-service-1.0-SNAPSHOT.jar"
  function_name = "card-paid-lambda"
  role          = aws_iam_role.lambda_execution_role.arn
  handler       = "lambdas.cardPaidLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30
  source_code_hash = filebase64sha256("d:/bank-card-transaction-service/target/bank-card-transaction-service-1.0-SNAPSHOT.jar")

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      TRANSACTION_TABLE_NAME = aws_dynamodb_table.transaction_table.name
      NOTIFICATION_QUEUE_URL = aws_sqs_queue.notification_queue.url
    }
  }
}

# 6. API Gateway (HTTP API v2)
resource "aws_apigatewayv2_api" "banking_api" {
  name          = "banking-backend-api-v2"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.banking_api.id
  name        = "$default"
  auto_deploy = true
}

# User Integrations
resource "aws_apigatewayv2_integration" "register" {
  api_id           = aws_apigatewayv2_api.banking_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.register_user.invoke_arn
}

resource "aws_apigatewayv2_route" "register_route" {
  api_id    = aws_apigatewayv2_api.banking_api.id
  route_key = "POST /register"
  target    = "integrations/${aws_apigatewayv2_integration.register.id}"
}

resource "aws_apigatewayv2_integration" "login" {
  api_id           = aws_apigatewayv2_api.banking_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.login_user.invoke_arn
}

resource "aws_apigatewayv2_route" "login_route" {
  api_id    = aws_apigatewayv2_api.banking_api.id
  route_key = "POST /login"
  target    = "integrations/${aws_apigatewayv2_integration.login.id}"
}

resource "aws_apigatewayv2_integration" "get_profile" {
  api_id           = aws_apigatewayv2_api.banking_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.get_profile.invoke_arn
}

resource "aws_apigatewayv2_route" "get_profile_route" {
  api_id    = aws_apigatewayv2_api.banking_api.id
  route_key = "GET /profile/{user_id}"
  target    = "integrations/${aws_apigatewayv2_integration.get_profile.id}"
}

resource "aws_apigatewayv2_integration" "update_profile" {
  api_id           = aws_apigatewayv2_api.banking_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.update_user.invoke_arn
}

resource "aws_apigatewayv2_route" "update_profile_route" {
  api_id    = aws_apigatewayv2_api.banking_api.id
  route_key = "PUT /profile/{user_id}"
  target    = "integrations/${aws_apigatewayv2_integration.update_profile.id}"
}

resource "aws_apigatewayv2_integration" "upload_avatar" {
  api_id           = aws_apigatewayv2_api.banking_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.upload_avatar.invoke_arn
}

resource "aws_apigatewayv2_route" "upload_avatar_route" {
  api_id    = aws_apigatewayv2_api.banking_api.id
  route_key = "POST /profile/{user_id}/avatar"
  target    = "integrations/${aws_apigatewayv2_integration.upload_avatar.id}"
}

# Cards & Transactions Integrations
resource "aws_apigatewayv2_integration" "activate_card" {
  api_id           = aws_apigatewayv2_api.banking_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.card_activate.invoke_arn
}

resource "aws_apigatewayv2_route" "activate_card_route" {
  api_id    = aws_apigatewayv2_api.banking_api.id
  route_key = "POST /card/activate"
  target    = "integrations/${aws_apigatewayv2_integration.activate_card.id}"
}

resource "aws_apigatewayv2_integration" "purchase" {
  api_id           = aws_apigatewayv2_api.banking_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.card_purchase.invoke_arn
}

resource "aws_apigatewayv2_route" "purchase_route" {
  api_id    = aws_apigatewayv2_api.banking_api.id
  route_key = "POST /transactions/purchase"
  target    = "integrations/${aws_apigatewayv2_integration.purchase.id}"
}

resource "aws_apigatewayv2_integration" "save_transaction" {
  api_id           = aws_apigatewayv2_api.banking_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.transaction_save.invoke_arn
}

resource "aws_apigatewayv2_route" "save_transaction_route" {
  api_id    = aws_apigatewayv2_api.banking_api.id
  route_key = "POST /transactions/save/{card_id}"
  target    = "integrations/${aws_apigatewayv2_integration.save_transaction.id}"
}

resource "aws_apigatewayv2_integration" "card_paid" {
  api_id           = aws_apigatewayv2_api.banking_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.card_paid.invoke_arn
}

resource "aws_apigatewayv2_route" "card_paid_route" {
  api_id    = aws_apigatewayv2_api.banking_api.id
  route_key = "POST /card/paid/{card_id}"
  target    = "integrations/${aws_apigatewayv2_integration.card_paid.id}"
}

# Permissions
resource "aws_lambda_permission" "api_gw_register" {
  statement_id  = "AllowExecutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.register_user.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.banking_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_login" {
  statement_id  = "AllowExecutionFromAPIGatewayLogin"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.login_user.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.banking_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_profile" {
  statement_id  = "AllowExecutionFromAPIGatewayProfile"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.get_profile.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.banking_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_update" {
  statement_id  = "AllowExecutionFromAPIGatewayUpdate"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.update_user.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.banking_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_activate" {
  statement_id  = "AllowExecutionFromAPIGatewayActivate"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_activate.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.banking_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_purchase" {
  statement_id  = "AllowExecutionFromAPIGatewayPurchase"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_purchase.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.banking_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_save" {
  statement_id  = "AllowExecutionFromAPIGatewaySave"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.transaction_save.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.banking_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_paid" {
  statement_id  = "AllowExecutionFromAPIGatewayPaid"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_paid.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.banking_api.execution_arn}/*/*"
}

resource "aws_lambda_permission" "api_gw_avatar" {
  statement_id  = "AllowExecutionFromAPIGatewayAvatar"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.upload_avatar.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.banking_api.execution_arn}/*/*"
}

resource "aws_apigatewayv2_integration" "card_report" {
  api_id           = aws_apigatewayv2_api.banking_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.card_report.invoke_arn
}

resource "aws_apigatewayv2_route" "card_report_route" {
  api_id    = aws_apigatewayv2_api.banking_api.id
  route_key = "GET /card/{card_id}"
  target    = "integrations/${aws_apigatewayv2_integration.card_report.id}"
}

resource "aws_lambda_permission" "api_gw_report" {
  statement_id  = "AllowAPIGwReport"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_report.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.banking_api.execution_arn}/*/*"
}

# Card Service: Card Report (Java)
resource "aws_lambda_function" "card_report" {
  filename      = "d:/bank-card-transaction-service/target/bank-card-transaction-service-1.0-SNAPSHOT.jar"
  function_name = "card-get-report-lambda"
  role          = aws_iam_role.lambda_execution_role.arn
  handler       = "lambdas.cardGetReportLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30

  environment {
    variables = {
      CARD_TABLE_NAME        = aws_dynamodb_table.card_table.name
      TRANSACTION_TABLE_NAME = aws_dynamodb_table.transaction_table.name
      NOTIFICATION_QUEUE_URL = aws_sqs_queue.notification_queue.url
    }
  }
}

# Final Outputs
output "FINAL_API_URL" {
  description = "USE THIS URL IN POSTMAN"
  value       = aws_apigatewayv2_stage.default.invoke_url
}
