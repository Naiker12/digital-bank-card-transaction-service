# Bank Card & Transaction Service 

Este es el repositorio central para la gestión de tarjetas bancarias y transacciones del sistema financiero. Este servicio se encarga de todo el ciclo de vida de una tarjeta, desde su solicitud y activación hasta el procesamiento de compras y pagos.

##  Responsabilidades del Servicio

El sistema está diseñado para manejar las siguientes funcionalidades críticas:

*   **Creación de Tarjetas:** Procesamiento inicial de solicitudes de nuevas tarjetas.
*   **Aprobación de Crédito:** Lógica para validar y otorgar límites de crédito.
*   **Activación de Tarjeta:** Proceso seguro para poner la tarjeta en estado operativo.
*   **Registro de Compras:** Procesamiento en tiempo real de transacciones de compra.
*   **Pagos:** Gestión de abonos y pagos a la tarjeta de crédito.
*   **Historial de Transacciones:** Almacenamiento y consulta de movimientos financieros.
*   **Reportes:** Generación de informes periódicos de estados de cuenta y consumos.

##  Stack Tecnológico & Integraciones

Este servicio está construido sobre **Java** y utiliza diversos servicios de **Amazon Web Services (AWS)** para garantizar escalabilidad y resiliencia:

*   **Java:** Lenguaje principal para la lógica de negocio.
*   **AWS DynamoDB:** Almacenamiento NoSQL de alto rendimiento para perfiles de tarjetas y transacciones.
*   **AWS SQS & DLQ:** Cola de mensajes para comunicación asíncrona entre microservicios, con manejo de errores mediante Dead Letter Queues.
*   **AWS S3:** Almacenamiento de reportes generados en formatos como PDF o CSV.
*   **AWS Lambda:** Arquitectura serverless para ejecutar tareas específicas de manera eficiente.

##  Arquitectura de Lambdas

El proyecto se divide en múltiples funciones Lambda especializadas:

| Lambda | Descripción |
| :--- | :--- |
| `create-request-card-lambda` | Inicia el flujo de solicitud de una nueva tarjeta. |
| `card-activate-lambda` | Gestiona el proceso de activación por parte del usuario. |
| `card-purchase-lambda` | Valida y procesa una intención de compra. |
| `card-transaction-save-lambda` | Persiste el registro de la transacción en la base de datos. |
| `card-paid-credit-card-lambda` | Procesa los pagos realizados a la deuda de la tarjeta. |
| `card-get-report-lambda` | Recupera información y genera reportes para el usuario. |
| `card-request-failed` | Maneja los fallos en las solicitudes para auditoría y reintentos. |

##  Estructura del Proyecto

```text
bank-card-transaction-service
│
├── src
│   ├── controller       # Controladores para la lógica de orquestación
│   │   └── cardController.java
│   │
│   ├── service          # Lógica de negocio y reglas financieras
│   │   └── cardService.java
│   │
│   ├── model            # Definición de entidades (Card, Transaction, etc.)
│   │   ├── card.java
│   │   └── transaction.java
│   │
│   └── utils            # Clientes de AWS y utilidades comunes
│       └── dynamoClient.java
│
├── lambdas              # Handlers específicos para AWS Lambda
│   ├── createRequestCardLambda.java
│   ├── cardActivateLambda.java
│   ├── cardPurchaseLambda.java
│   ├── transactionSaveLambda.java
│   ├── cardPaidLambda.java
│   ├── cardReportLambda.java
│   └── cardRequestFailedLambda.java
│
├── pom.xml              # Configuración de dependencias (Maven)
└── README.md            # Documentación del proyecto
```


