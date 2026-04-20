# Servicio de Tarjetas y Transacciones Bancarias (Bank Card & Transaction Service)

Este es el repositorio central para la gestión de tarjetas bancarias y el procesamiento de transacciones. El servicio gestiona el ciclo de vida completo de la tarjeta, desde la solicitud y activación hasta la validación de compras y el procesamiento de pagos.

## 📋 Resumen de Arquitectura

Siguiendo la **Guía Definitiva de Arquitectura EDA**, este servicio se integra de la siguiente manera:

| Servicio | Rol Principal | Tecnología | Enlace con Datos |
|---|---|---|---|
| **Card Transaction** | Core Bancario / Débito | Java / Spring Boot | DynamoDB (Tarjetas) |
| **Payment Service** | API Gateway / Orquestador | Python / Lambda | Redis (Catálogo) / DynamoDB (Pagos) |
| **Notification Service** | Procesador de Colas (Workers) | Python / Lambda | SQS (start, check, transaction) |

## Responsabilidades del Servicio

El sistema está diseñado para manejar las siguientes funcionalidades críticas:

* **Creación de Tarjetas**: Procesamiento asíncrono de nuevas solicitudes de tarjetas de débito y crédito vía SQS.
* **Aprobación de Crédito**: Lógica para validar y asignar límites de crédito iniciales (por defecto: 5000).
* **Activación de Tarjetas**: Proceso de activación seguro basado en reglas de negocio.
* **Validación de Compras**: Procesamiento en tiempo real y actualización de saldos para compras.
* **Pagos**: Gestión de abonos y pagos a los saldos de las tarjetas.
* **Historial de Transacciones**: Almacenamiento seguro de todos los movimientos financieros para auditoría.
* **Reportes**: Generación de estados de cuenta y reportes de actividad.

## Stack Tecnológico e Integraciones

El servicio está construido usando **Java 17** y utiliza varios **Amazon Web Services (AWS)** para garantizar alta disponibilidad:

* **Java**: Lenguaje principal para la lógica financiera y procesamiento de datos.
* **AWS DynamoDB**: Almacenamiento NoSQL de alto rendimiento para perfiles de tarjetas y transacciones.
* **AWS SQS & DLQ**: Colas de mensajería para comunicación asíncrona entre servicios.
* **AWS S3**: Almacenamiento para reportes generados (PDF/CSV).
* **AWS Lambda**: Entorno de ejecución serverless para tareas especializadas.

## Arquitectura de Lambdas

El proyecto se compone de múltiples funciones Lambda especializadas:

| Lambda | Descripción |
| :--- | :--- |
| `create-request-card-lambda` | Procesa mensajes SQS para crear tarjetas de Débito (ACTIVADAS) o Crédito (PENDIENTES). |
| `card-activate-lambda` | **Regla de Negocio**: Solo activa tarjetas de Crédito si el usuario tiene 10+ compras con Débito. |
| `card-purchase-lambda` | Valida fondos y procesa transacciones de compra en tiempo real. |
| `bank-transaction-save-lambda`| Persiste los registros de transacciones en la base de datos. |
| `card-paid-lambda` | Procesa pagos y actualiza los saldos de las tarjetas. |
| `card-report-lambda` | Agrega datos y genera reportes de actividad vía email. |

## Estructura del Proyecto

```text
bank-card-transaction-service
│
├── lambdas                 # Handlers de AWS Lambda (Java)
│   ├── createRequestCardLambda.java
│   ├── cardActivateLambda.java
│   ├── cardPurchaseLambda.java
│   ├── transactionSaveLambda.java
│   ├── cardPaidLambda.java
│   └── cardReportLambda.java
│
├── src                     # Lógica Central de la Aplicación
│   ├── main/java
│   │   ├── model           # Estructuras de datos (Card, Transaction)
│   │   ├── service         # Implementaciones de lógica de negocio
│   │   └── utils           # Clientes AWS (DynamoDB, SQS, SES)
│
├── terraform               # Infraestructura como Código
│   └── main.tf             # Definiciones de recursos de AWS
├── pom.xml                 # Gestión de dependencias con Maven
└── README.md               # Documentación técnica
```

## Comandos de Desarrollo y Despliegue

### 1. Compilación del Proyecto (Maven)
El proyecto utiliza el `maven-shade-plugin` para generar un "Fat JAR" que contiene todas las dependencias necesarias.

```bash
# Compilar y empaquetar el proyecto
mvn clean package
```

**Artefactos Generados:**
- `target/bank-card-transaction-service-1.0-SNAPSHOT.jar`: El JAR listo para producción para AWS Lambda.

### 2. Despliegue de Infraestructura (Terraform Independiente)
Este servicio ahora gestiona su propia infraestructura de forma independiente. Ya no contiene la configuración global para otros servicios.
1. Asegúrate de que `terraform.tfvars` esté presente con los valores compartidos (IAM Role, APIGW).
2. Desplegar:
```bash
cd terraform
terraform init
terraform apply -auto-approve
```

### 3. Regla de Negocio: Activación de Tarjeta de Crédito
Para activar una tarjeta de crédito, se deben cumplir los siguientes requisitos:
1. La tarjeta debe estar en estado `PENDING`.
2. El usuario debe haber completado al menos **10 compras** usando su tarjeta de **Débito**.
3. Si no se cumplen los requisitos, la API retorna un error descriptivo explicando el criterio faltante.
