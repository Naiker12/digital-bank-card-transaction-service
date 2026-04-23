# Bank Card Transaction Service

Servicio backend para la gestion de tarjetas, transacciones, pagos, catalogo y reportes de un banco digital.

## Que resuelve este servicio

- Creacion de tarjetas de debito y credito.
- Activacion de tarjetas de credito bajo una regla de negocio de compras con debito.
- Consulta de tarjetas por usuario.
- Registro de compras, recargas y pagos.
- Generacion de reportes por tarjeta.
- Lectura y actualizacion del catalogo de servicios desde S3.
- Seguimiento de transacciones con trazabilidad por `traceId` desde el flujo de pago.

## Stack tecnico

- Java 17
- AWS Lambda
- AWS DynamoDB
- AWS S3
- AWS SQS
- Terraform

## Lambdas principales

| Lambda | Descripcion |
| :--- | :--- |
| `create-request-card-lambda` | Crea solicitudes de tarjetas nuevas. |
| `card-activate-lambda` | Activa tarjetas de credito cuando se cumple la regla de negocio. |
| `card-purchase-lambda` | Procesa compras y valida el saldo o estado de la tarjeta. |
| `bank-transaction-save-lambda` | Guarda transacciones y movimientos asociados. |
| `card-paid-lambda` | Procesa pagos a tarjetas de credito. |
| `card-get-report-lambda` | Genera reportes de actividad por tarjeta. |
| `get-user-cards-lambda` | Consulta las tarjetas de un usuario y su contador de compras. |
| `catalog-lambda` | Lee y actualiza el catalogo desde S3. |

## Flujo de negocio

### Tarjetas

1. Se crean tarjetas de debito o credito.
2. Las tarjetas de credito pueden quedar en estado `PENDING`.
3. Cuando el usuario completa 10 compras con debito, la tarjeta de credito pasa a activa.

### Compras y pagos

1. Las compras se registran con validacion de saldo y estado.
2. Los pagos a tarjetas se procesan por la lambda de `card-paid`.
3. El frontend consulta el estado del proceso mediante `traceId`.

### Catalogo

1. El catalogo de servicios se almacena en S3.
2. La lambda de catalogo expone la lectura y actualizacion del CSV.
3. El frontend consume este catalogo para el flujo de pagos.

## Estructura del proyecto

```text
bank-card-transaction-service
├── src
│   └── main
│       └── java
│           └── lambdas
├── terraform
├── pom.xml
└── README.md
```

## Compilacion

```bash
mvn clean package
```

## Despliegue

```bash
cd terraform
terraform init
terraform apply -auto-approve
```

## Notas de arquitectura

- DynamoDB es la base principal para tarjetas, movimientos y reportes.
- SQS se usa para desacoplar eventos y notificaciones.
- El servicio mantiene trazabilidad de pagos para que el frontend pueda mostrar estados en vivo.
- La regla de activacion de credito se basa en compras reales con tarjeta de debito.
