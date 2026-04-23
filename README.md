# Servicio de Tarjetas y Transacciones Bancarias

Este repositorio contiene el servicio encargado del ciclo de vida de tarjetas bancarias, la validacion de compras, el registro de transacciones y la generacion de reportes.

## Resumen

El servicio incluye:

- Creacion de tarjetas de debito y credito.
- Activacion de tarjetas de credito bajo reglas de negocio.
- Validacion de compras en tiempo real.
- Registro y persistencia de transacciones.
- Procesamiento de pagos a tarjetas.
- Generacion de reportes de actividad.

## Stack tecnologico

- Java 17
- AWS DynamoDB
- AWS SQS y DLQ
- AWS S3
- AWS Lambda
- Terraform

## Lambdas

| Lambda | Descripcion |
| :--- | :--- |
| `create-request-card-lambda` | Procesa solicitudes de alta de tarjetas. |
| `card-activate-lambda` | Activa tarjetas de credito cuando se cumple la regla de negocio. |
| `card-purchase-lambda` | Valida fondos y procesa compras. |
| `bank-transaction-save-lambda` | Persiste transacciones en la base de datos. |
| `card-paid-lambda` | Procesa pagos y actualiza saldos. |
| `card-get-report-lambda` | Genera reportes de actividad. |
| `get-user-cards-lambda` | Consulta las tarjetas asociadas a un usuario. |
| `catalog-lambda` | Lee y actualiza el catalogo desde S3. |

## Estructura

```text
bank-card-transaction-service
├── src
│   └── main/java
│       └── lambdas
├── terraform
├── pom.xml
└── README.md
```

## Desarrollo

### Compilar

```bash
mvn clean package
```

El artefacto principal queda en `target/bank-card-transaction-service-1.0-SNAPSHOT.jar`.

### Desplegar infraestructura

```bash
cd terraform
terraform init
terraform apply -auto-approve
```

## Regla de negocio

Para activar una tarjeta de credito:

1. La tarjeta debe estar en estado `PENDING`.
2. El usuario debe haber completado al menos 10 compras usando su tarjeta de debito.
3. Si no se cumple la regla, la API devuelve un error descriptivo.
