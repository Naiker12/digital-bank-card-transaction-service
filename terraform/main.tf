provider "aws" {
  region = "us-east-1"
}

# 1. Crear API Gateway
resource "aws_api_gateway_rest_api" "bank_api" {
  name        = "bank-api"
  description = "API Gateway for Bank Microservices"
}

# 2. Recurso /users
resource "aws_api_gateway_resource" "users" {
  rest_api_id = aws_api_gateway_rest_api.bank_api.id
  parent_id   = aws_api_gateway_rest_api.bank_api.root_resource_id
  path_part   = "users"
}

# 2.1 /users/register
resource "aws_api_gateway_resource" "register" {
  rest_api_id = aws_api_gateway_rest_api.bank_api.id
  parent_id   = aws_api_gateway_resource.users.id
  path_part   = "register"
}

# 2.2 /users/login
resource "aws_api_gateway_resource" "login" {
  rest_api_id = aws_api_gateway_rest_api.bank_api.id
  parent_id   = aws_api_gateway_resource.users.id
  path_part   = "login"
}

# 2.3 /users/profile
resource "aws_api_gateway_resource" "profile" {
  rest_api_id = aws_api_gateway_rest_api.bank_api.id
  parent_id   = aws_api_gateway_resource.users.id
  path_part   = "profile"
}

# 3. Recurso /cards
resource "aws_api_gateway_resource" "cards" {
  rest_api_id = aws_api_gateway_rest_api.bank_api.id
  parent_id   = aws_api_gateway_rest_api.bank_api.root_resource_id
  path_part   = "cards"
}

# 3.1 /cards/request
resource "aws_api_gateway_resource" "card_request" {
  rest_api_id = aws_api_gateway_rest_api.bank_api.id
  parent_id   = aws_api_gateway_resource.cards.id
  path_part   = "request"
}

# 3.2 /cards/purchase
resource "aws_api_gateway_resource" "purchase" {
  rest_api_id = aws_api_gateway_rest_api.bank_api.id
  parent_id   = aws_api_gateway_resource.cards.id
  path_part   = "purchase"
}

# 4. Recurso /transactions
resource "aws_api_gateway_resource" "transactions" {
  rest_api_id = aws_api_gateway_rest_api.bank_api.id
  parent_id   = aws_api_gateway_rest_api.bank_api.root_resource_id
  path_part   = "transactions"
}

# 4.1 /transactions/history
resource "aws_api_gateway_resource" "history" {
  rest_api_id = aws_api_gateway_rest_api.bank_api.id
  parent_id   = aws_api_gateway_resource.transactions.id
  path_part   = "history"
}

# --- Métodos e Integraciones User Service ---

# POST /users/register
resource "aws_api_gateway_method" "register_user" {
  rest_api_id   = aws_api_gateway_rest_api.bank_api.id
  resource_id   = aws_api_gateway_resource.register.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "register_lambda" {
  rest_api_id             = aws_api_gateway_rest_api.bank_api.id
  resource_id             = aws_api_gateway_resource.register.id
  http_method             = aws_api_gateway_method.register_user.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:195802271670:function:register-user-lambda/invocations"
}

# POST /users/login
resource "aws_api_gateway_method" "login_user" {
  rest_api_id   = aws_api_gateway_rest_api.bank_api.id
  resource_id   = aws_api_gateway_resource.login.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "login_lambda" {
  rest_api_id             = aws_api_gateway_rest_api.bank_api.id
  resource_id             = aws_api_gateway_resource.login.id
  http_method             = aws_api_gateway_method.login_user.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:195802271670:function:login-user-lambda/invocations"
}

# --- Métodos e Integraciones Card Service ---

# POST /cards/request
resource "aws_api_gateway_method" "request_card" {
  rest_api_id   = aws_api_gateway_rest_api.bank_api.id
  resource_id   = aws_api_gateway_resource.card_request.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "card_request_lambda" {
  rest_api_id             = aws_api_gateway_rest_api.bank_api.id
  resource_id             = aws_api_gateway_resource.card_request.id
  http_method             = aws_api_gateway_method.request_card.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:195802271670:function:create-request-card-lambda/invocations"
}

# POST /cards/purchase
resource "aws_api_gateway_method" "purchase_card" {
  rest_api_id   = aws_api_gateway_rest_api.bank_api.id
  resource_id   = aws_api_gateway_resource.purchase.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "purchase_lambda" {
  rest_api_id             = aws_api_gateway_rest_api.bank_api.id
  resource_id             = aws_api_gateway_resource.purchase.id
  http_method             = aws_api_gateway_method.purchase_card.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:195802271670:function:card-purchase-lambda/invocations"
}

# 8. Deploy API
resource "aws_api_gateway_deployment" "bank_api_deploy" {
  depends_on = [
    aws_api_gateway_integration.register_lambda,
    aws_api_gateway_integration.login_lambda,
    aws_api_gateway_integration.card_request_lambda,
    aws_api_gateway_integration.purchase_lambda
  ]
  rest_api_id = aws_api_gateway_rest_api.bank_api.id
  stage_name  = "prod"
}

output "base_url" {
  value = "${aws_api_gateway_deployment.bank_api_deploy.invoke_url}/prod"
}
