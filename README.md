# Bank Card & Transaction Service

This is the central repository for banking card management and transaction processing. This service manages the entire card lifecycle, from request and activation to purchase validation and payment processing.

## Service Responsibilities

The system is designed to handle the following critical functionalities:

* **Card Creation**: Asynchronous processing of new debit and credit card requests via SQS.
* **Credit Approval**: Logic to validate and assign initial credit limits (default: 5000).
* **Card Activation**: Secure activation process based on business rules.
* **Purchase Validation**: Real-time processing and balance updates for purchases.
* **Payments**: Managing credits and payments to card balances.
* **Transaction History**: Secure storage of all financial movements for auditing.
* **Reporting**: Generation of account statements and activity reports.

## Technology Stack & Integrations

The service is built using **Java 17** and leverages several **Amazon Web Services (AWS)** to ensure high availability:

* **Java**: Core language for financial logic and data processing.
* **AWS DynamoDB**: High-performance NoSQL storage for card profiles and transactions.
* **AWS SQS & DLQ**: Message queuing for asynchronous inter-service communication.
* **AWS S3**: Storage for generated reports (PDF/CSV).
* **AWS Lambda**: Serverless execution environment for specialized tasks.

## Lambda Architecture

The project is composed of multiple specialized Lambda functions:

| Lambda | Description |
| :--- | :--- |
| `create-request-card-lambda` | Processes SQS messages to create Debit (ACTIVATED) or Credit (PENDING) cards. |
| `card-activate-lambda` | **Business Rule**: Only activates Credit cards if the user has 10+ Debit purchases. |
| `card-purchase-lambda` | Validates funds and processes real-time purchase transactions. |
| `bank-transaction-save-lambda`| Persists transaction records in the database. |
| `card-paid-lambda` | Processes payments and updates card balances. |
| `card-report-lambda` | Aggregates data and generates activity reports via email. |

## Project Structure

```text
bank-card-transaction-service
│
├── lambdas                 # AWS Lambda Handlers (Java)
│   ├── createRequestCardLambda.java
│   ├── cardActivateLambda.java
│   ├── cardPurchaseLambda.java
│   ├── transactionSaveLambda.java
│   ├── cardPaidLambda.java
│   └── cardReportLambda.java
│
├── src                     # Core Application Logic
│   ├── main/java
│   │   ├── model           # Data structures (Card, Transaction)
│   │   ├── service         # Business logic implementations
│   │   └── utils           # AWS Clients (DynamoDB, SQS, SES)
│
├── terraform               # Infrastructure as Code
│   └── main.tf             # AWS resource definitions
├── pom.xml                 # Maven dependency management
└── README.md               # Technical documentation
```

## Development and Deployment Commands

### 1. Project Compilation (Maven)
The project uses the `maven-shade-plugin` to generate a "Fat JAR" containing all necessary dependencies for the AWS Lambda runtime.

```bash
# Compile and package the project
mvn clean package
```

**Artifacts Generated:**
- `target/bank-card-transaction-service-1.0-SNAPSHOT.jar`: The production-ready JAR for AWS Lambda deployment.

### 2. Infrastructure Deployment (Independent Terraform)
This service now manages its own infrastructure independently. It no longer contains the global configuration for other services.
1. Ensure `terraform.tfvars` is present with shared infrastructure values (IAM Role, APIGW).
2. Deploy:
```bash
cd terraform
terraform init
terraform apply -auto-approve
```

### 3. Business Rule: Credit Card Activation
To activate a credit card, the following requirements must be met:
1. The card must currently be in `PENDING` status.
2. The user must have completed at least **10 purchases** using their **Debit** card.
3. If requirements are not met, the API returns a descriptive error in Spanish explaining the missing criteria.

---

