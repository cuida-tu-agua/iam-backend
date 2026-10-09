# TESTING — Pruebas unitarias de `ms-iam`

Microservicio de identidad de **Cuida Tu Agua** (Java 21, Spring Boot 4.1.1). Documento exigido por la *Guía de Aprendizaje – Pruebas Unitarias* (SENA, Tecnólogo en Análisis y Desarrollo de Software).

> Las celdas marcadas con ⏳ se completan con la salida real de `.\mvnw clean test` en la máquina del equipo (ver sección 9).

## 1. Plan de pruebas

### 1.1 Objetivo
Verificar de forma aislada y repetible que la lógica de `ms-iam` (registro, inicio y renovación de sesión, bloqueo de cuentas por el administrador, códigos de un solo uso y consultas de usuarios) se comporta según las historias de usuario, y medir cuánto código ejercitan las pruebas.

### 1.2 Alcance

| Capa | Clases bajo prueba | Estrategia |
|---|---|---|
| Controller (`presentation`) | `AuthController`, `ProfileController`, `ActionCodeController`, `InternalUserController`, `AdminUserController`, `AdminMetricsController` | `@WebMvcTest` + `MockMvc`; casos de uso simulados con `@MockitoBean` (ya existentes: `HttpContractTest`, `AdminUserHttpTest`) |
| Service (`application.service`) | `AuthenticationService`, `OneTimeCodes`, `UserBlockingService`, `ActionCodeService` y los demás servicios | JUnit 5 + **Mockito** (`@Mock`, `MockitoExtension`, sin contexto de Spring) + AssertJ; además las pruebas con dobles en memoria ya existentes (`Fakes`, `TestWorld`) |
| Repository (`infrastructure.persistence`) | `JpaUserRepositoryAdapter` y repositorios Spring Data | `@DataJpaTest` + **H2** en memoria |
| Dominio (`domain`) | `User`, `Email`, `PhoneNumber`, `PasswordPolicy`, `LockoutPolicy` | JUnit 5 + AssertJ puro (existentes) |

Fuera de alcance: servicios .NET (`ms-places`, `ms-device`, `ms-consumption`, `ms-valve`) y la app móvil (278 pruebas Jest propias); integración real con SQL Server/Redis/SMTP.

### 1.3 Herramientas

| Herramienta | Uso |
|---|---|
| JUnit 5 (`@Test`, `@ParameterizedTest`, `@DisplayName`) | Ejecutor de pruebas |
| Mockito 5 | Doble de las dependencias (puertos de salida) |
| AssertJ | Aserciones legibles |
| H2 | Base en memoria para la capa de persistencia |
| Spring Boot Test (`@WebMvcTest`, `@DataJpaTest`) | Cortes del contexto por capa |
| JaCoCo 0.8.14 | Cobertura de líneas y ramas |

### 1.4 Convenciones
- **AAA**: cada prueba se divide con los comentarios `// Arrange`, `// Act`, `// Assert`.
- **Nombre** `given_when_then` (p. ej. `givenWrongPassword_whenLogin_thenReportsRemainingAttempts`) más `@DisplayName` en español.
- Una causa de fallo por prueba; reloj fijo (`2026-09-29T15:00:00Z`) para que no dependan de la hora.
- Sin red, sin Docker y sin SQL Server para las pruebas nuevas.

### 1.5 Criterios de entrada y salida
- Entrada: el proyecto compila (`.\mvnw clean compile`).
- Salida: 0 pruebas fallidas y cobertura de líneas de la capa de servicios ≥ 80 % (meta; ver sección 7).

## 2. Cómo ejecutar

```powershell
cd C:\cuida-tu-agua\sy-water-backend\ms-iam
.\mvnw clean test                 # ejecuta todo y genera el informe de JaCoCo
start target\site\jacoco\index.html   # informe HTML de cobertura
```

Algunas pruebas antiguas usan Testcontainers: necesitan Docker Desktop abierto.

## 3. Matriz de casos de prueba (pruebas nuevas, escritas con la guía)

| ID | Capa | Clase bajo prueba | Método de prueba | Qué verifica | Historia | Resultado |
|---|---|---|---|---|---|---|
| CP-01 | Service | `AuthenticationService` | `givenValidCredentials_whenLogin_thenOpensSessionAndRecordsSuccess` | login: con credenciales correctas abre sesión y registra el intento exitoso | HU-003 / HU-004 | ⏳ |
| CP-02 | Service | `AuthenticationService` | `givenUnknownEmail_whenLogin_thenThrowsEmailNotFound` | login: un correo que no existe lanza EmailNotFoundException y deja rastro | HU-003 / HU-004 | ⏳ |
| CP-03 | Service | `AuthenticationService` | `givenDeletedAccount_whenLogin_thenThrowsEmailNotFound` | login: una cuenta eliminada se comporta como si no existiera | HU-003 / HU-004 | ⏳ |
| CP-04 | Service | `AuthenticationService` | `givenBlockedAccount_whenLogin_thenThrowsAccountBlocked` | login: una cuenta bloqueada por el administrador no puede entrar | HU-003 / HU-004 | ⏳ |
| CP-05 | Service | `AuthenticationService` | `givenTemporarilyLockedAccount_whenLogin_thenThrowsAccountLocked` | login: una cuenta con bloqueo temporal vigente lanza AccountLockedException | HU-003 / HU-004 | ⏳ |
| CP-06 | Service | `AuthenticationService` | `givenWrongPassword_whenLogin_thenReportsRemainingAttempts` | login: contraseña incorrecta informa cuántos intentos quedan | HU-003 / HU-004 | ⏳ |
| CP-07 | Service | `AuthenticationService` | `givenFifthWrongPassword_whenLogin_thenLocksAccountForFifteenMinutes` | login: el quinto fallo bloquea la cuenta 15 minutos | HU-003 / HU-004 | ⏳ |
| CP-08 | Service | `AuthenticationService` | `givenNullPassword_whenLogin_thenTreatedAsWrongPassword` | login: una contraseña nula se trata como incorrecta, sin romper | HU-003 / HU-004 | ⏳ |
| CP-09 | Service | `AuthenticationService` | `givenUnverifiedAccount_whenLoginWithRightPassword_thenThrowsNotVerified` | login: la contraseña correcta de una cuenta sin verificar no abre sesión | HU-003 / HU-004 | ⏳ |
| CP-10 | Service | `AuthenticationService` | `givenBlankRefreshToken_whenRefresh_thenThrowsInvalidRefreshToken` | refresh: un token vacío se rechaza sin consultar nada | HU-003 / HU-004 | ⏳ |
| CP-11 | Service | `AuthenticationService` | `givenUnknownRefreshToken_whenRefresh_thenThrowsInvalidRefreshToken` | refresh: un token desconocido se rechaza | HU-003 / HU-004 | ⏳ |
| CP-12 | Service | `AuthenticationService` | `givenReusedRefreshToken_whenRefresh_thenRevokesEverySessionOfTheUser` | refresh: reutilizar un token ya revocado cierra todas las sesiones del usuario | HU-003 / HU-004 | ⏳ |
| CP-13 | Service | `AuthenticationService` | `givenExpiredRefreshToken_whenRefresh_thenThrowsInvalidRefreshToken` | refresh: un token vencido se rechaza | HU-003 / HU-004 | ⏳ |
| CP-14 | Service | `AuthenticationService` | `givenBlockedUser_whenRefresh_thenThrowsAccountBlocked` | refresh: si el usuario fue bloqueado no se renueva la sesión | HU-003 / HU-004 | ⏳ |
| CP-15 | Service | `AuthenticationService` | `givenTokenAlreadyConsumedByParallelRequest_whenRefresh_thenThrowsInvalidRefreshToken` | refresh: dos peticiones paralelas con el mismo token, solo una gana | HU-003 / HU-004 | ⏳ |
| CP-16 | Service | `AuthenticationService` | `givenValidRefreshToken_whenRefresh_thenRotatesSession` | refresh: un token válido se consume y entrega una sesión nueva | HU-003 / HU-004 | ⏳ |
| CP-17 | Service | `AuthenticationService` | `givenTokens_whenLogout_thenRevokesBothAndRecordsActivity` | logout: revoca el access token y el refresh token del propio usuario | HU-003 / HU-004 | ⏳ |
| CP-18 | Service | `AuthenticationService` | `givenRefreshTokenOfAnotherUser_whenLogout_thenDoesNotRevokeIt` | logout: no revoca un refresh token que pertenece a otro usuario | HU-003 / HU-004 | ⏳ |
| CP-19 | Service | `OneTimeCodes` | `givenNoCooldown_whenIssue_thenStoresHashAndReturnsCode` | issue: guarda solo el hash del código y devuelve el código en claro | HU-002 / HU-005 | ⏳ |
| CP-20 | Service | `OneTimeCodes` | `givenCodeSentTenSecondsAgo_whenIssueWithCooldown_thenThrowsWithRemainingWait` | issue: pedir otro código antes del tiempo de espera lanza CodeRecentlySentException | HU-002 / HU-005 | ⏳ |
| CP-21 | Service | `OneTimeCodes` | `givenCooldownElapsed_whenIssueWithCooldown_thenIssuesNewCode` | issue: pasado el tiempo de espera sí emite otro código | HU-002 / HU-005 | ⏳ |
| CP-22 | Service | `OneTimeCodes` | `givenCorrectCode_whenVerify_thenConsumesIt` | verify: un código correcto se consume | HU-002 / HU-005 | ⏳ |
| CP-23 | Service | `OneTimeCodes` | `givenNoActiveCode_whenVerify_thenThrowsCodeExpired` | verify: sin código vigente lanza CodeExpiredException | HU-002 / HU-005 | ⏳ |
| CP-24 | Service | `OneTimeCodes` | `givenCodeWithMaxFailedAttempts_whenVerify_thenThrowsCodeExpired` | verify: un código que ya agotó sus intentos se considera vencido | HU-002 / HU-005 | ⏳ |
| CP-25 | Service | `OneTimeCodes` | `givenWrongCode_whenVerify_thenRegistersFailureAndReportsRemaining` | verify: un código incorrecto cuenta el fallo e informa los intentos restantes | HU-002 / HU-005 | ⏳ |
| CP-26 | Service | `OneTimeCodes` | `givenMalformedCode_whenVerify_thenFailsWithoutComparingHash` | verify: un código que no tiene 6 dígitos falla sin comparar el hash | HU-002 / HU-005 | ⏳ |
| CP-27 | Service | `OneTimeCodes` | `givenCodeConsumedByParallelRequest_whenVerify_thenThrowsCodeExpired` | verify: si otra petición ya consumió el código, este intento falla | HU-002 / HU-005 | ⏳ |
| CP-28 | Service | `UserBlockingService` | `givenAdminAndActiveUser_whenBlock_thenBlocksRevokesSessionsAndAudits` | block: bloquea la cuenta, cierra sus sesiones y deja dos filas de auditoría | HU-060 | ⏳ |
| CP-29 | Service | `UserBlockingService` | `givenAlreadyBlockedUser_whenBlock_thenNothingChanges` | block: bloquear una cuenta ya bloqueada no cambia nada (idempotente) | HU-060 | ⏳ |
| CP-30 | Service | `UserBlockingService` | `givenVeryLongReason_whenBlock_thenReasonIsTruncated` | block: el motivo se recorta a 200 caracteres | HU-060 | ⏳ |
| CP-31 | Service | `UserBlockingService` | `givenNonAdministrator_whenBlock_thenThrowsNotAdministrator` | block: quien no es administrador no puede bloquear | HU-060 | ⏳ |
| CP-32 | Service | `UserBlockingService` | `givenBlockedAdministrator_whenBlock_thenThrowsNotAdministrator` | block: un administrador que ya está bloqueado pierde el poder de inmediato | HU-060 | ⏳ |
| CP-33 | Service | `UserBlockingService` | `givenUnknownTarget_whenBlock_thenThrowsUserNotFound` | block: un usuario inexistente lanza UserNotFoundException | HU-060 | ⏳ |
| CP-34 | Service | `UserBlockingService` | `givenAdministratorTargetingSelf_whenBlock_thenThrowsCannotBlockSelf` | block: nadie puede bloquear su propia cuenta | HU-060 | ⏳ |
| CP-35 | Service | `UserBlockingService` | `givenBlockedUser_whenUnblock_thenAccountIsActiveAgain` | unblock: reactiva una cuenta bloqueada y audita | HU-060 | ⏳ |
| CP-36 | Service | `UserBlockingService` | `givenActiveUser_whenUnblock_thenNothingChanges` | unblock: desbloquear una cuenta que no estaba bloqueada no hace nada | HU-060 | ⏳ |
| CP-37 | Service | `ActionCodeService` | `givenActiveUser_whenRequestCode_thenIssuesAndSendsEmail` | requestCode: emite el código, lo envía por correo y devuelve el correo enmascarado | Cierre de válvula | ⏳ |
| CP-38 | Service | `ActionCodeService` | `givenUnknownAction_whenRequestCode_thenThrowsUnknownAction` | requestCode: una acción desconocida lanza UnknownActionException | Cierre de válvula | ⏳ |
| CP-39 | Service | `ActionCodeService` | `givenNullAction_whenRequestCode_thenThrowsUnknownAction` | requestCode: una acción nula se trata como desconocida | Cierre de válvula | ⏳ |
| CP-40 | Service | `ActionCodeService` | `givenDeletedUser_whenRequestCode_thenThrowsUserNotFound` | requestCode: un usuario inexistente o eliminado lanza UserNotFoundException | Cierre de válvula | ⏳ |
| CP-41 | Service | `ActionCodeService` | `givenBlockedUser_whenRequestCode_thenThrowsAccountBlocked` | requestCode: un usuario bloqueado no recibe códigos | Cierre de válvula | ⏳ |
| CP-42 | Service | `ActionCodeService` | `givenActiveUser_whenVerifyCode_thenVerifiesAndRecordsActivity` | verifyCode: verifica el código y registra la actividad | Cierre de válvula | ⏳ |
| CP-43 | Repository | `JpaUserRepositoryAdapter` | `givenNewUser_whenCreate_thenFindByEmailReturnsItWithRole` | create + findByEmail: guarda el usuario con su rol y lo recupera igual | HU-059 / HU-060 / HU-062 | ⏳ |
| CP-44 | Repository | `JpaUserRepositoryAdapter` | `givenExistingEmail_whenCreate_thenThrowsEmailAlreadyRegistered` | create: un correo repetido lanza EmailAlreadyRegisteredException | HU-059 / HU-060 / HU-062 | ⏳ |
| CP-45 | Repository | `JpaUserRepositoryAdapter` | `givenStoredUser_whenQueryingExistence_thenReflectsStoredData` | existsByEmail / findById: distinguen entre existente e inexistente | HU-059 / HU-060 / HU-062 | ⏳ |
| CP-46 | Repository | `JpaUserRepositoryAdapter` | `givenPhoneOfAna_whenPhoneTakenByOther_thenOnlyOthersCount` | phoneTakenByOther: ignora al propio usuario y detecta a otro | HU-059 / HU-060 / HU-062 | ⏳ |
| CP-47 | Repository | `JpaUserRepositoryAdapter` | `givenBlockedUser_whenUpdate_thenBlockIsPersisted` | update: persiste el bloqueo hecho en el dominio | HU-059 / HU-060 / HU-062 | ⏳ |
| CP-48 | Repository | `JpaUserRepositoryAdapter` | `givenUsersInEveryState_whenCountByStatus_thenCountsEachGroup` | countByStatus: separa activos, bloqueados y sin verificar, y no cuenta eliminados | HU-059 / HU-060 / HU-062 | ⏳ |
| CP-49 | Repository | `JpaUserRepositoryAdapter` | `givenSeveralUsers_whenSearch_thenFiltersByTextStatusAndPage` | search: filtra por texto y por estado, y pagina | HU-059 / HU-060 / HU-062 | ⏳ |
| CP-50 | Repository | `JpaUserRepositoryAdapter` | `givenPercentSign_whenSearch_thenItIsNotAWildcard` | search: un % escrito por el usuario es texto, no un comodín | HU-059 / HU-060 / HU-062 | ⏳ |
| CP-51 | Repository | `JpaUserRepositoryAdapter` | `givenDeletedUser_whenSearch_thenItIsExcluded` | search: las cuentas eliminadas nunca aparecen | HU-059 / HU-060 / HU-062 | ⏳ |

### 3.1 Pruebas existentes que completan la matriz

| Clase de prueba | Capa | Casos | Qué cubre |
|---|---|---|---|
| `HttpContractTest` | Controller | 17 | Contrato HTTP de auth, perfil, códigos de acción y endpoints internos (códigos de estado, JSON, cabeceras) |
| `AdminUserHttpTest` | Controller | 13 | Listado, bloqueo y métricas del administrador (permisos 401/403) |
| `RegistrationAndVerificationTest` | Service | 7 | Registro y verificación de correo |
| `LoginAndSessionTest` | Service | 9 | Login, bloqueo temporal, refresh y logout |
| `RecoveryProfileAndDeletionTest` | Service | 12 | Recuperación de contraseña, perfil y eliminación de cuenta |
| `UserBlockingTest`, `ActionCodeTest`, `UserListingTest`, `ContactLookupTest`, `PlatformMetricsTest` | Service | 6 + 9 + 8 + 4 + 7 | Casos de uso restantes |
| `DomainRulesTest`, `UserBlockingDomainTest` | Dominio | 14 + 3 | Reglas de `User`, `Email`, contraseña, bloqueo |
| `HttpServiceMetricsReaderTest`, `HttpDeviceCleanupTest` | Infraestructura | 4 + 4 | Clientes HTTP hacia ms-device / ms-places |

## 4. Reporte de ejecución

| Dato | Valor |
|---|---|
| Fecha | ⏳ |
| Comando | `.\mvnw clean test` |
| Pruebas ejecutadas | ⏳ |
| Exitosas | ⏳ |
| Fallidas | ⏳ |
| Omitidas | ⏳ |
| Duración | ⏳ |

Captura de la consola: `docs/testing/consola-mvnw-test.png` ⏳

## 5. Cobertura (JaCoCo)

Informe HTML: `target/site/jacoco/index.html` (no se versiona).

| Capa / paquete | Líneas | Ramas | Meta | Estado |
|---|---|---|---|---|
| `presentation` (Controller) | ⏳ | ⏳ | ≥ 70 % | ⏳ |
| `application.service` (Service) | ⏳ | ⏳ | ≥ 80 % | ⏳ |
| `infrastructure.persistence` (Repository) | ⏳ | ⏳ | ≥ 60 % | ⏳ |
| `domain` | ⏳ | ⏳ | ≥ 90 % | ⏳ |
| **Total** | ⏳ | ⏳ | ≥ 75 % | ⏳ |

Capturas: `docs/testing/jacoco-resumen.png`, `docs/testing/jacoco-service.png` ⏳

## 6. Lista de verificación de calidad

| Criterio | Estado |
|---|---|
| Patrón AAA con comentarios en las pruebas nuevas | ✅ |
| Nombres `given_when_then` | ✅ (nuevas) · las antiguas usan nombres descriptivos en inglés |
| Pruebas independientes (sin orden ni estado compartido) | ✅ |
| Reloj fijo, sin `Thread.sleep` | ✅ |
| Dependencias externas simuladas (Mockito) o en memoria (H2) | ✅ |
| Casos felices **y** de error por cada método público probado | ✅ |
| Aserciones con AssertJ, sin `assertTrue` sueltos | ✅ |
| Todas las pruebas pasan | ⏳ |
| Cobertura dentro de las metas | ⏳ |
| Informe JaCoCo generado | ⏳ |

## 7. Plan de mejora técnica

| # | Hallazgo | Acción | Estado |
|---|---|---|---|
| 1 | Los servicios solo tenían pruebas con dobles escritos a mano (`Fakes`); la guía pide Mockito | Se añadieron pruebas Mockito de `AuthenticationService`, `OneTimeCodes`, `UserBlockingService` y `ActionCodeService` | Hecho |
| 2 | La capa de persistencia no tenía pruebas unitarias (solo Testcontainers) | `JpaUserRepositoryAdapterTest` con `@DataJpaTest` + H2 | Hecho |
| 3 | No se medía cobertura | JaCoCo en `pom.xml` y informe en cada `mvnw test` | Hecho |
| 4 | Servicios sin pruebas Mockito propias: `RegistrationService`, `PasswordRecoveryService`, `ProfileService`, `AccountDeletionService`, `UserListingService`, adaptadores `Jpa*` restantes | Tras ver el informe de JaCoCo, priorizar las clases con menos cobertura y escribir sus pruebas (segunda ronda) | Pendiente |
| 5 | Pruebas antiguas con nombres que no siguen `given_when_then` | Renombrar al tocarlas | Pendiente |
| 6 | Refactor candidato: `AuthenticationService.login` mezcla validación, registro de intentos y bloqueo en un solo método (~45 líneas) | Extraer `registerFailure(...)` y `lockAccount(...)` manteniendo el comportamiento; las pruebas nuevas actúan de red de seguridad | Pendiente |

### 7.1 Cobertura antes / después
| Momento | Líneas | Ramas |
|---|---|---|
| Antes (solo pruebas existentes) | ⏳ | ⏳ |
| Después (con las pruebas nuevas) | ⏳ | ⏳ |
