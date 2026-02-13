#!/bin/bash

echo "🚀 Ejecutando PaymentOrderIntegrationTestSimple..."

cd "/Users/consuelofigueroa/Downloads/RestAssured_Payment_Example"

# Compilar primero
echo "📦 Compilando proyecto..."
mvn clean compile test-compile

# Ejecutar test específico  
echo "🧪 Ejecutando test..."
mvn test -Dtest=com.aws.quarkus.test.PaymentOrderIntegrationTestSimple

echo "✅ Ejecución completada"
