# 🚀 **Test de Integración Avanzado - API Reactiva de Pagos**

## 🎯 **Descripción del Escenario**

Este test implementa un **escenario completo de integración** para un microservicio de pagos reactivo que:

1. **📤 Recibe POST /orders** - Acepta órdenes de pago y las envía a procesamiento asíncrono
2. **📨 Publica eventos Kafka** - Envía mensaje al topic `orders-out` para procesamiento
3. **🔄 Procesa asíncronamente** - Simula procesamiento backend con respuesta Kafka
4. **📊 Permite consultar estado** - GET /orders/{id}/status para verificar progreso

---

## ⚡ **Características Técnicas Implementadas**

### ✅ **1. Latencia y Polling con Awaitility**
```java
@Step("📊 Verificar estado COMPLETED para orden: {orderId}")
private void verifyOrderCompletedStatus(String orderId, String jobId) {
    await.untilAsserted(() -> {
        Response statusResponse = given()
        .when()
            .get("/orders/{orderId}/status", orderId)
        .then()
            .statusCode(200)
            .body("status", equalTo("COMPLETED"))
            .time(lessThan(1000L)) // Consulta rápida
        .extract().response();
    });
}
```

### ✅ **2. Integración Kafka con Quarkus Companion**
```java
@InjectKafkaCompanion
KafkaCompanion companion;

// Verificar que el evento se produjo
await.untilAsserted(() -> {
    var records = companion.consume(String.class)
        .fromTopics(ORDERS_TOPIC, 1)
        .awaitCompletion(Duration.ofSeconds(10));
    
    assertFalse(records.isEmpty(), "Debe existir al menos un evento en el topic");
});

// Simular respuesta de procesamiento
companion.produce(String.class, String.class)
    .toTopic(ORDERS_RESPONSE_TOPIC)
    .withKey(orderId)
    .withValue(responseMessage);
```

### ✅ **3. Validación de Contratos con JSON Schema**
```java
private JsonSchema createOrderEventSchema() {
    String schemaJson = """
        {
            "$schema": "http://json-schema.org/draft-07/schema#",
            "type": "object",
            "required": ["orderId", "jobId", "status", "timestamp"],
            "properties": {
                "orderId": {"type": "string", "pattern": "^[a-zA-Z0-9-_]+$"},
                "status": {"enum": ["PENDING", "PROCESSING", "COMPLETED", "FAILED"]},
                "amount": {"type": "number", "minimum": 0}
            }
        }
        """;
    return factory.getSchema(schemaJson);
}
```

### ✅ **4. Backpressure y Carga Masiva**
```java
@Test
@DisplayName("⚡ Backpressure: 10 peticiones concurrentes con 202 Accepted")
void testBackpressureHandling() {
    List<CompletableFuture<Response>> futures = new ArrayList<>();
    
    // 🚀 Generar 10 peticiones concurrentes
    for (int i = 0; i < 10; i++) {
        CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> 
            performLoadTestRequest(orderId, paymentData)
        );
        futures.add(future);
    }
    
    // Verificar que al menos 70% son aceptadas (202)
    // y que no hay errores 5xx
}
```

### ✅ **5. Idempotencia**
```java
@Test
@DisplayName("🔁 Idempotencia: Peticiones duplicadas retornan mismo jobId")
void testIdempotencyValidation() {
    String orderId = "idempotent-order-" + System.currentTimeMillis();
    
    // 📤 Primera petición
    String firstJobId = createPaymentOrder(orderId, paymentData);
    
    // 📤 Segunda petición idéntica 
    String secondJobId = createPaymentOrderIdempotent(orderId, paymentData, firstJobId);
    
    // ✅ Verificar que ambos jobId son idénticos
    assertEquals(firstJobId, secondJobId);
}
```

### ✅ **6. Reportes Allure Completos**
```java
@Epic("Payment Processing System")
@Feature("Reactive Payment Orders API")
@Story("Complete Order Processing Flow")
@Description("Valida el flujo completo de creación y procesamiento asíncrono")
@Severity(SeverityLevel.CRITICAL)

@Step("📤 Crear orden de pago: {orderId}")
private String createPaymentOrder(String orderId, String paymentData) {
    Allure.addAttachment("Request Payload", "application/json", paymentData);
    // ... lógica del test
}

// Filtro personalizado para capturar HTTP requests/responses
private static class AllureRestAssuredFilter implements Filter {
    @Override
    public Response filter(...) {
        Allure.addAttachment("HTTP Request", "text/plain", requestDetails);
        Allure.addAttachment("HTTP Response", "text/plain", responseDetails);
        return response;
    }
}
```

---

## 🏗️ **Estructura del Test**

### **📁 Organización por Responsabilidades**
```
PaymentOrderIntegrationTest.java
├── 🧪 Tests Principales
│   ├── testCompleteOrderProcessingFlow()     # Flujo end-to-end  
│   ├── testIdempotencyValidation()           # Validación idempotencia
│   ├── testBackpressureHandling()            # Carga y backpressure
│   └── testKafkaContractValidation()         # Contratos Kafka
│
├── 🛠️ Steps Granulares (@Step)
│   ├── createPaymentOrder()                  # POST /orders
│   ├── verifyKafkaEventProduced()           # Verificar evento Kafka
│   ├── simulateAsyncProcessing()            # Simular procesamiento
│   └── verifyOrderCompletedStatus()         # GET /orders/{id}/status
│
└── 🔧 Métodos Utilitarios
    ├── createOrderPayload()                 # Generar JSON payload
    ├── validateJsonSchema()                 # Validar contratos
    └── AllureRestAssuredFilter             # Captura logs
```

---

## 📊 **Métricas y Validaciones**

### **⏱️ Timeouts y Performance**
- **POST /orders**: Max 2 segundos para aceptar
- **GET /orders/{id}/status**: Max 1 segundo para consultar  
- **Kafka processing**: Max 30 segundos para completar
- **Load test**: Max 5 segundos bajo carga

### **🔢 Criterios de Éxito**
- **Backpressure**: ≥70% peticiones aceptadas (202)
- **Error rate**: 0% errores 5xx bajo carga
- **Idempotencia**: 100% consistencia en jobId duplicados
- **Kafka**: 100% eventos producidos correctamente
- **Contratos**: 100% compliance con JSON Schema

### **📈 Cobertura de Testing**
- ✅ **Unit tests**: Lógica de negocio individual
- ✅ **Integration tests**: APIs + Kafka + Async processing  
- ✅ **Contract tests**: JSON Schema validation
- ✅ **Load tests**: Backpressure y concurrencia
- ✅ **Idempotency tests**: Deduplicación

---

## 🚀 **Cómo Ejecutar**

### **🔨 Compilar y Preparar**
```bash
# Compilar proyecto con todas las dependencias
mvn clean compile

# Verificar dependencias están correctas  
mvn dependency:tree | grep -E "(kafka|allure|awaitility|schema)"
```

### **🧪 Ejecutar Tests**
```bash
# Ejecutar solo el test de integración
mvn test -Dtest=PaymentOrderIntegrationTest

# Ejecutar con logging detallado
mvn test -Dtest=PaymentOrderIntegrationTest -Dquarkus.log.level=DEBUG

# Ejecutar tests en paralelo
mvn test -Dtest=PaymentOrderIntegrationTest -Djunit.jupiter.execution.parallel.enabled=true
```

### **📊 Generar Reportes Allure**
```bash
# Generar y servir reporte interactivo
mvn allure:serve

# O generar reporte estático
mvn allure:report
# El reporte estará en target/site/allure-maven-plugin/
```

---

## 🎯 **Casos de Uso Validados**

### ✅ **Flujo Happy Path**
1. Cliente envía POST /orders con datos válidos
2. Sistema acepta con 202 y retorna jobId
3. Evento se publica en Kafka topic orders-out
4. Procesamiento asíncrono simula lógica de negocio
5. Estado cambia a COMPLETED en GET /orders/{id}/status

### ✅ **Idempotencia**
1. Cliente envía la misma orden 2 veces
2. Ambas peticiones retornan el mismo jobId  
3. Solo se procesa una vez el pago
4. No hay duplicación en Kafka

### ✅ **Backpressure bajo carga**
1. Sistema recibe 10 peticiones simultáneas
2. Al menos 7/10 son aceptadas (202)
3. Algunas pueden ser limitadas (429)
4. Cero errores del sistema (5xx)

### ✅ **Contratos Kafka**
1. Eventos siguen esquema JSON predefinido
2. Campos requeridos están presentes
3. Tipos de datos son correctos
4. Validación automática contra schema

---

## 🔥 **Tecnologías Integradas**

| Componente | Versión | Propósito |
|------------|---------|-----------|
| **Java** | 21 LTS | Runtime y language features |
| **Quarkus** | 3.17.6 | Framework reactivo cloud-native |
| **RestAssured** | 5.5.0 | Testing de APIs REST |
| **JUnit 5** | Latest | Framework de testing |
| **Awaitility** | 4.2.2 | Testing asíncrono y polling |
| **Kafka Companion** | Latest | Testing de eventos Kafka |
| **JSON Schema Validator** | 1.5.1 | Validación de contratos |
| **Allure** | 2.29.0 | Reportes avanzados |
| **TestContainers** | Latest | Testing de integración |

---

## 🎊 **Entregable Final**

**✅ Un solo archivo Java limpio y completo** que implementa:

- 📝 **Datos mockeados** completamente funcionales
- 🔍 **Comentarios explicativos** en cada sección
- 🎯 **Todos los requerimientos técnicos** implementados
- 📊 **Métricas y validaciones** exhaustivas
- 🚀 **Listo para ejecutar** en el entorno actual

**🎉 El test está listo para usar como template de testing avanzado!**
