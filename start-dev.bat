@echo off
echo Iniciando base de datos PostgreSQL
docker-compose up -d

echo Iniciando Backend
start cmd /k "cd backend && .\mvnw spring-boot:run"

echo Iniciando Frontend Angular
start cmd /k "cd frontend && npm start"


