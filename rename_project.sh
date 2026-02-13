#!/bin/bash

echo "🔄 Preparando para renombrar proyecto a RestAssured_Payment_Example..."

# Directorio actual
CURRENT_DIR="/Users/consuelofigueroa/Downloads/restAssuredTrial-main"
NEW_DIR="/Users/consuelofigueroa/Downloads/RestAssured_Payment_Example"

echo "📁 Directorio actual: $CURRENT_DIR"
echo "📁 Nuevo directorio: $NEW_DIR"

# Verificar si el directorio actual existe
if [ ! -d "$CURRENT_DIR" ]; then
    echo "❌ Error: El directorio actual no existe"
    exit 1
fi

# Verificar si el nuevo directorio ya existe
if [ -d "$NEW_DIR" ]; then
    echo "⚠️  Advertencia: El directorio destino ya existe"
    echo "¿Deseas continuar? (y/n)"
    read -r response
    if [[ "$response" != "y" && "$response" != "Y" ]]; then
        echo "❌ Operación cancelada"
        exit 1
    fi
    echo "🗑️  Removiendo directorio existente..."
    rm -rf "$NEW_DIR"
fi

# Realizar el renombrado
echo "🚀 Moviendo proyecto..."
mv "$CURRENT_DIR" "$NEW_DIR"

if [ $? -eq 0 ]; then
    echo "✅ Proyecto renombrado exitosamente!"
    echo "📍 Nueva ubicación: $NEW_DIR"
    echo ""
    echo "🎯 Para continuar trabajando:"
    echo "   cd '$NEW_DIR'"
    echo "   ./generate_allure_report.sh"
    echo ""
    echo "📋 Archivos actualizados:"
    echo "   ✅ pom.xml - artifactId y name"
    echo "   ✅ README.md - título y descripción"
    echo "   ✅ .gitignore - reglas completas"
    echo "   ✅ Scripts - referencias actualizadas"
else
    echo "❌ Error al renombrar el proyecto"
    exit 1
fi
