# Guía de ejecución del servicio IAM

## 1. Requisitos

- Java 21 o superior.
- MySQL 8 ejecutándose en `localhost:3306`.
- Maven Wrapper incluido en el proyecto (`mvnw.cmd`).

Comprueba Java desde PowerShell:

```powershell
java -version
```

## 2. Crear la base de datos

Conéctate a MySQL y crea solamente la base de datos. Liquibase creará las tablas y el usuario inicial automáticamente.

```sql
CREATE DATABASE IF NOT EXISTS iam_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

La configuración local usa inicialmente:

```text
Usuario MySQL: root
Contraseña: 123456
Base de datos: iam_db
```

Si tus credenciales son diferentes, configura las variables de entorno antes de iniciar la aplicación:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/iam_db"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "tu_password"
$env:JWT_SECRET = "una-clave-local-de-al-menos-32-caracteres"
$env:JWT_EXPIRATION = "3600000"
```

## 3. Ejecutar el proyecto

Desde la carpeta raíz del proyecto:

```powershell
.\mvnw.cmd clean spring-boot:run
```

La aplicación quedará disponible en:

```text
http://localhost:8080
```

En el primer arranque, Liquibase ejecuta las migraciones y crea:

- `users`
- `roles`
- `user_roles`
- `databasechangelog`
- `databasechangeloglock`

Si la base de datos ya existía, Liquibase ejecutará automáticamente el changeset que
sincroniza la contraseña del usuario de prueba. No edites manualmente
`DATABASECHANGELOG`; las migraciones deben avanzar mediante nuevos changesets.

También crea un usuario inicial para probar el login:

```text
Correo: admin@savewater.local
Contraseña: password
Rol: ADMIN
```

## 4. Abrir Swagger UI

Abre esta URL en el navegador:

http://localhost:8080/swagger-ui/index.html

El JSON de OpenAPI está disponible en:

- http://localhost:8080/v3/api-docs

El proyecto usa Spring Boot 4 con Springdoc 3.0.2. No cambies Springdoc a la
rama 2.x, porque no es compatible con Spring Framework 7 y Swagger puede mostrar
`Failed to load API definition`.

## 5. Probar el login en Swagger

1. En Swagger, abre `POST /auth/login`.
2. Pulsa **Try it out**.
3. Usa este cuerpo:

```json
{
  "email": "admin@savewater.local",
  "password": "password"
}
```

Importante: pega exactamente JSON válido. No uses formato de PowerShell o Java
como `{ email = "...", password = "..." }`, ni elimines las comillas de las
propiedades.

4. Pulsa **Execute**.
5. La respuesta `200` debe incluir `token` y `email`.

Después de obtener el token, pulsa el botón **Authorize**, ubicado normalmente
en la parte superior derecha de Swagger. En la ventana que aparece, pega el JWT
completo y pulsa **Authorize**. Swagger agregará automáticamente el encabezado
`Authorization: Bearer <token>` a los endpoints protegidos.

El token se envía en las peticiones protegidas con este encabezado:

```text
Authorization: Bearer <token>
```

Cuando Swagger muestre el botón **Authorize**, pega únicamente el token o escribe `Bearer <token>` según el esquema que aparezca en la interfaz.

## 6. Probar desde PowerShell

```powershell
$body = @{
  email = "admin@savewater.local"
  password = "password"
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://localhost:8080/auth/login" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body
```

## 7. Ejecutar las pruebas

```powershell
.\mvnw.cmd test
```

## 8. Problemas frecuentes

### MySQL no conecta

Verifica que el servicio esté iniciado, que exista `iam_db` y que las variables `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` sean correctas.

### Liquibase informa un lock

Comprueba que no haya otra instancia de la aplicación ejecutándose. Si el proceso terminó abruptamente, revisa `databasechangeloglock` y libera el lock únicamente cuando estés seguro de que no hay otra migración activa:

```sql
UPDATE databasechangeloglock SET locked = 0, lockgranted = NULL, lockedby = NULL;
```

### El login responde 401

Usa exactamente el usuario inicial y la contraseña indicados arriba. No guardes contraseñas en texto plano: la base de datos almacena un hash BCrypt.

Una respuesta `401` significa que el endpoint sí fue encontrado, pero el correo o la contraseña no coinciden. Una respuesta `403` en una versión anterior puede aparecer cuando la excepción de credenciales no tenía manejador; reinicia la aplicación para cargar la versión actual.

Si el log muestra `JSON parse error`, el problema está en el cuerpo enviado. La
respuesta correcta para ese caso es `400`; vuelve a pegar el JSON del ejemplo
sin modificarlo.
