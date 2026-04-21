locals {
  jar_path = abspath("${path.module}/../target/bank-card-transaction-service-1.0-SNAPSHOT.jar")
  jar_hash = fileexists(local.jar_path) ? filebase64sha256(local.jar_path) : "missing_jar"
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}
