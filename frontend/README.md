# DeployOps - Frontend SPA

Cliente web de observabilidad de alta densidad y monitoreo en tiempo real desarrollado con **Angular 20.3** y **Three.js r187**.

## Tecnologías y Arquitectura

- **Framework:** Angular 20.3 (arquitectura basada en componentes Standalone, Signals reactivas y sintaxis de flujo de control moderna `@if`, `@for`).
- **Visualización 3D:** Three.js r187 con WebGL para el globo terráqueo de telemetría, mapeo geodésico de nodos, arcos de datos intercontinentales y física de rotación continua con inercia.
- **Cartografía Vectorial:** Textura procedural de alta definición (4096 x 2048 px) basada en polígonos continentales de Natural Earth con trazo dual de resplandor.
- **Estilos:** CSS puro modular con sistema de variables para modo oscuro y modo claro conmutables, alineado a estándares de accesibilidad WCAG 2.1 AA.
- **Canal de Datos en Vivo:** Consumo reactivo de eventos Server-Sent Events (SSE) vía `/api/endpoints/stream`.

## Requisitos

- Node.js versión 20.x o 22.x LTS
- npm versión 10 o superior

## Comandos de Desarrollo

### 1. Instalación de Dependencias
```bash
npm install
```

### 2. Servidor de Desarrollo
```bash
npm start
# O alternativamente:
npx ng serve
```
El panel estará disponible en `http://localhost:4200`.

### 3. Pruebas Unitarias Automatizadas
Ejecución de las 33 pruebas unitarias con Jasmine y Karma en Chrome Headless:
```bash
npm test -- --watch=false --browsers=ChromeHeadless
```

### 4. Compilación de Producción
```bash
npm run build
```
Genera los artefactos optimizados en el directorio `dist/frontend` con presupuestos estrictos de bundle y chunks diferidos.
