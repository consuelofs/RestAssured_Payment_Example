#!/bin/bash

echo "🎯 RestAssured Payment Example - Generando reporte de Allure..."

cd "/Users/consuelofigueroa/Downloads/RestAssured_Payment_Example"

# Ejecutar los tests primero
echo "🧪 Ejecutando tests..."
mvn test -Dtest=PaymentOrderIntegrationTestSimple

# Generar reporte de Allure
echo "📊 Generando reporte HTML..."
allure generate target/allure-results -o target/allure-report --clean

# Abrir en navegador
echo "🌐 Abriendo reporte en navegador..."
open target/allure-report/index.html

echo "✅ Reporte generado y abierto en: target/allure-report/index.html"
