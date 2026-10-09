# TESTING — Pruebas unitarias de `ms-iam`

Microservicio de identidad de **Cuida Tu Agua** (Java 21, Spring Boot 4.1.1). Documento exigido por la *Guía de Aprendizaje – Pruebas Unitarias* (SENA, Tecnólogo en Análisis y Desarrollo de Software).

> Resultados reales de `.\mvnw clean test`, ejecutado por el equipo el 2026-10-09 (Java 25, Windows).

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
| CP-01 | Service | `AuthenticationService` | `givenValidCredentials_whenLogin_thenOpensSessionAndRecordsSuccess` | login: con credenciales correctas abre sesión y registra el intento exitoso | HU-003 / HU-004 | ✅ |
| CP-02 | Service | `AuthenticationService` | `givenUnknownEmail_whenLogin_thenThrowsEmailNotFound` | login: un correo que no existe lanza EmailNotFoundException y deja rastro | HU-003 / HU-004 | ✅ |
| CP-03 | Service | `AuthenticationService` | `givenDeletedAccount_whenLogin_thenThrowsEmailNotFound` | login: una cuenta eliminada se comporta como si no existiera | HU-003 / HU-004 | ✅ |
| CP-04 | Service | `AuthenticationService` | `givenBlockedAccount_whenLogin_thenThrowsAccountBlocked` | login: una cuenta bloqueada por el administrador no puede entrar | HU-003 / HU-004 | ✅ |
| CP-05 | Service | `AuthenticationService` | `givenTemporarilyLockedAccount_whenLogin_thenThrowsAccountLocked` | login: una cuenta con bloqueo temporal vigente lanza AccountLockedException | HU-003 / HU-004 | ✅ |
| CP-06 | Service | `AuthenticationService` | `givenWrongPassword_whenLogin_thenReportsRemainingAttempts` | login: contraseña incorrecta informa cuántos intentos quedan | HU-003 / HU-004 | ✅ |
| CP-07 | Service | `AuthenticationService` | `givenFifthWrongPassword_whenLogin_thenLocksAccountForFifteenMinutes` | login: el quinto fallo bloquea la cuenta 15 minutos | HU-003 / HU-004 | ✅ |
| CP-08 | Service | `AuthenticationService` | `givenNullPassword_whenLogin_thenTreatedAsWrongPassword` | login: una contraseña nula se trata como incorrecta, sin romper | HU-003 / HU-004 | ✅ |
| CP-09 | Service | `AuthenticationService` | `givenUnverifiedAccount_whenLoginWithRightPassword_thenThrowsNotVerified` | login: la contraseña correcta de una cuenta sin verificar no abre sesión | HU-003 / HU-004 | ✅ |
| CP-10 | Service | `AuthenticationService` | `givenBlankRefreshToken_whenRefresh_thenThrowsInvalidRefreshToken` | refresh: un token vacío se rechaza sin consultar nada | HU-003 / HU-004 | ✅ |
| CP-11 | Service | `AuthenticationService` | `givenUnknownRefreshToken_whenRefresh_thenThrowsInvalidRefreshToken` | refresh: un token desconocido se rechaza | HU-003 / HU-004 | ✅ |
| CP-12 | Service | `AuthenticationService` | `givenReusedRefreshToken_whenRefresh_thenRevokesEverySessionOfTheUser` | refresh: reutilizar un token ya revocado cierra todas las sesiones del usuario | HU-003 / HU-004 | ✅ |
| CP-13 | Service | `AuthenticationService` | `givenExpiredRefreshToken_whenRefresh_thenThrowsInvalidRefreshToken` | refresh: un token vencido se rechaza | HU-003 / HU-004 | ✅ |
| CP-14 | Service | `AuthenticationService` | `givenBlockedUser_whenRefresh_thenThrowsAccountBlocked` | refresh: si el usuario fue bloqueado no se renueva la sesión | HU-003 / HU-004 | ✅ |
| CP-15 | Service | `AuthenticationService` | `givenTokenAlreadyConsumedByParallelRequest_whenRefresh_thenThrowsInvalidRefreshToken` | refresh: dos peticiones paralelas con el mismo token, solo una gana | HU-003 / HU-004 | ✅ |
| CP-16 | Service | `AuthenticationService` | `givenValidRefreshToken_whenRefresh_thenRotatesSession` | refresh: un token válido se consume y entrega una sesión nueva | HU-003 / HU-004 | ✅ |
| CP-17 | Service | `AuthenticationService` | `givenTokens_whenLogout_thenRevokesBothAndRecordsActivity` | logout: revoca el access token y el refresh token del propio usuario | HU-003 / HU-004 | ✅ |
| CP-18 | Service | `AuthenticationService` | `givenRefreshTokenOfAnotherUser_whenLogout_thenDoesNotRevokeIt` | logout: no revoca un refresh token que pertenece a otro usuario | HU-003 / HU-004 | ✅ |
| CP-19 | Service | `OneTimeCodes` | `givenNoCooldown_whenIssue_thenStoresHashAndReturnsCode` | issue: guarda solo el hash del código y devuelve el código en claro | HU-002 / HU-005 | ✅ |
| CP-20 | Service | `OneTimeCodes` | `givenCodeSentTenSecondsAgo_whenIssueWithCooldown_thenThrowsWithRemainingWait` | issue: pedir otro código antes del tiempo de espera lanza CodeRecentlySentException | HU-002 / HU-005 | ✅ |
| CP-21 | Service | `OneTimeCodes` | `givenCooldownElapsed_whenIssueWithCooldown_thenIssuesNewCode` | issue: pasado el tiempo de espera sí emite otro código | HU-002 / HU-005 | ✅ |
| CP-22 | Service | `OneTimeCodes` | `givenCorrectCode_whenVerify_thenConsumesIt` | verify: un código correcto se consume | HU-002 / HU-005 | ✅ |
| CP-23 | Service | `OneTimeCodes` | `givenNoActiveCode_whenVerify_thenThrowsCodeExpired` | verify: sin código vigente lanza CodeExpiredException | HU-002 / HU-005 | ✅ |
| CP-24 | Service | `OneTimeCodes` | `givenCodeWithMaxFailedAttempts_whenVerify_thenThrowsCodeExpired` | verify: un código que ya agotó sus intentos se considera vencido | HU-002 / HU-005 | ✅ |
| CP-25 | Service | `OneTimeCodes` | `givenWrongCode_whenVerify_thenRegistersFailureAndReportsRemaining` | verify: un código incorrecto cuenta el fallo e informa los intentos restantes | HU-002 / HU-005 | ✅ |
| CP-26 | Service | `OneTimeCodes` | `givenMalformedCode_whenVerify_thenFailsWithoutComparingHash` | verify: un código que no tiene 6 dígitos falla sin comparar el hash | HU-002 / HU-005 | ✅ |
| CP-27 | Service | `OneTimeCodes` | `givenCodeConsumedByParallelRequest_whenVerify_thenThrowsCodeExpired` | verify: si otra petición ya consumió el código, este intento falla | HU-002 / HU-005 | ✅ |
| CP-28 | Service | `UserBlockingService` | `givenAdminAndActiveUser_whenBlock_thenBlocksRevokesSessionsAndAudits` | block: bloquea la cuenta, cierra sus sesiones y deja dos filas de auditoría | HU-060 | ✅ |
| CP-29 | Service | `UserBlockingService` | `givenAlreadyBlockedUser_whenBlock_thenNothingChanges` | block: bloquear una cuenta ya bloqueada no cambia nada (idempotente) | HU-060 | ✅ |
| CP-30 | Service | `UserBlockingService` | `givenVeryLongReason_whenBlock_thenReasonIsTruncated` | block: el motivo se recorta a 200 caracteres | HU-060 | ✅ |
| CP-31 | Service | `UserBlockingService` | `givenNonAdministrator_whenBlock_thenThrowsNotAdministrator` | block: quien no es administrador no puede bloquear | HU-060 | ✅ |
| CP-32 | Service | `UserBlockingService` | `givenBlockedAdministrator_whenBlock_thenThrowsNotAdministrator` | block: un administrador que ya está bloqueado pierde el poder de inmediato | HU-060 | ✅ |
| CP-33 | Service | `UserBlockingService` | `givenUnknownTarget_whenBlock_thenThrowsUserNotFound` | block: un usuario inexistente lanza UserNotFoundException | HU-060 | ✅ |
| CP-34 | Service | `UserBlockingService` | `givenAdministratorTargetingSelf_whenBlock_thenThrowsCannotBlockSelf` | block: nadie puede bloquear su propia cuenta | HU-060 | ✅ |
| CP-35 | Service | `UserBlockingService` | `givenBlockedUser_whenUnblock_thenAccountIsActiveAgain` | unblock: reactiva una cuenta bloqueada y audita | HU-060 | ✅ |
| CP-36 | Service | `UserBlockingService` | `givenActiveUser_whenUnblock_thenNothingChanges` | unblock: desbloquear una cuenta que no estaba bloqueada no hace nada | HU-060 | ✅ |
| CP-37 | Service | `ActionCodeService` | `givenActiveUser_whenRequestCode_thenIssuesAndSendsEmail` | requestCode: emite el código, lo envía por correo y devuelve el correo enmascarado | Cierre de válvula | ✅ |
| CP-38 | Service | `ActionCodeService` | `givenUnknownAction_whenRequestCode_thenThrowsUnknownAction` | requestCode: una acción desconocida lanza UnknownActionException | Cierre de válvula | ✅ |
| CP-39 | Service | `ActionCodeService` | `givenNullAction_whenRequestCode_thenThrowsUnknownAction` | requestCode: una acción nula se trata como desconocida | Cierre de válvula | ✅ |
| CP-40 | Service | `ActionCodeService` | `givenDeletedUser_whenRequestCode_thenThrowsUserNotFound` | requestCode: un usuario inexistente o eliminado lanza UserNotFoundException | Cierre de válvula | ✅ |
| CP-41 | Service | `ActionCodeService` | `givenBlockedUser_whenRequestCode_thenThrowsAccountBlocked` | requestCode: un usuario bloqueado no recibe códigos | Cierre de válvula | ✅ |
| CP-42 | Service | `ActionCodeService` | `givenActiveUser_whenVerifyCode_thenVerifiesAndRecordsActivity` | verifyCode: verifica el código y registra la actividad | Cierre de válvula | ✅ |
| CP-43 | Repository | `JpaUserRepositoryAdapter` | `givenNewUser_whenCreate_thenFindByEmailReturnsItWithRole` | create + findByEmail: guarda el usuario con su rol y lo recupera igual | HU-059 / HU-060 / HU-062 | ✅ |
| CP-44 | Repository | `JpaUserRepositoryAdapter` | `givenExistingEmail_whenCreate_thenThrowsEmailAlreadyRegistered` | create: un correo repetido lanza EmailAlreadyRegisteredException | HU-059 / HU-060 / HU-062 | ✅ |
| CP-45 | Repository | `JpaUserRepositoryAdapter` | `givenStoredUser_whenQueryingExistence_thenReflectsStoredData` | existsByEmail / findById: distinguen entre existente e inexistente | HU-059 / HU-060 / HU-062 | ✅ |
| CP-46 | Repository | `JpaUserRepositoryAdapter` | `givenPhoneOfAna_whenPhoneTakenByOther_thenOnlyOthersCount` | phoneTakenByOther: ignora al propio usuario y detecta a otro | HU-059 / HU-060 / HU-062 | ✅ |
| CP-47 | Repository | `JpaUserRepositoryAdapter` | `givenBlockedUser_whenUpdate_thenBlockIsPersisted` | update: persiste el bloqueo hecho en el dominio | HU-059 / HU-060 / HU-062 | ✅ |
| CP-48 | Repository | `JpaUserRepositoryAdapter` | `givenUsersInEveryState_whenCountByStatus_thenCountsEachGroup` | countByStatus: separa activos, bloqueados y sin verificar, y no cuenta eliminados | HU-059 / HU-060 / HU-062 | ✅ |
| CP-49 | Repository | `JpaUserRepositoryAdapter` | `givenSeveralUsers_whenSearch_thenFiltersByTextStatusAndPage` | search: filtra por texto y por estado, y pagina | HU-059 / HU-060 / HU-062 | ✅ |
| CP-50 | Repository | `JpaUserRepositoryAdapter` | `givenPercentSign_whenSearch_thenItIsNotAWildcard` | search: un % escrito por el usuario es texto, no un comodín | HU-059 / HU-060 / HU-062 | ✅ |
| CP-51 | Repository | `JpaUserRepositoryAdapter` | `givenDeletedUser_whenSearch_thenItIsExcluded` | search: las cuentas eliminadas nunca aparecen | HU-059 / HU-060 / HU-062 | ✅ |
| CP-52 | Repository | JpaLoginAttempt/RefreshToken/Credential/ActivityLog/OneTimeCode adapters | `givenMixedAttempts_whenCountFailuresSince_thenCountsOnlyRecentWrongPasswords` | login attempts: cuenta solo las contraseñas incorrectas posteriores al corte | HU-003 / HU-005 / HU-004 | ✅ |
| CP-53 | Repository | JpaLoginAttempt/RefreshToken/Credential/ActivityLog/OneTimeCode adapters | `givenSuccesses_whenLastSuccessAt_thenReturnsLatestOrEmpty` | login attempts: lastSuccessAt devuelve el último éxito, o vacío si nunca hubo | HU-003 / HU-005 / HU-004 | ✅ |
| CP-54 | Repository | JpaLoginAttempt/RefreshToken/Credential/ActivityLog/OneTimeCode adapters | `givenVeryLongEmail_whenRecord_thenItIsTruncated` | login attempts: un correo de más de 320 caracteres se recorta en vez de fallar | HU-003 / HU-005 / HU-004 | ✅ |
| CP-55 | Repository | JpaLoginAttempt/RefreshToken/Credential/ActivityLog/OneTimeCode adapters | `givenSavedToken_whenFindByHash_thenReturnsActiveToken` | refresh tokens: se guarda por hash y se recupera sin revocar | HU-003 / HU-005 / HU-004 | ✅ |
| CP-56 | Repository | JpaLoginAttempt/RefreshToken/Credential/ActivityLog/OneTimeCode adapters | `givenActiveToken_whenRevokeIfActiveTwice_thenOnlyFirstSucceeds` | refresh tokens: revokeIfActive solo funciona la primera vez | HU-003 / HU-005 / HU-004 | ✅ |
| CP-57 | Repository | JpaLoginAttempt/RefreshToken/Credential/ActivityLog/OneTimeCode adapters | `givenTokensOfTwoUsers_whenRevokeAllForUser_thenOnlyThatUserIsAffected` | refresh tokens: revokeAllForUser revoca solo los tokens activos de ese usuario | HU-003 / HU-005 / HU-004 | ✅ |
| CP-58 | Repository | JpaLoginAttempt/RefreshToken/Credential/ActivityLog/OneTimeCode adapters | `givenExistingCredential_whenSavePasswordHashAgain_thenReplacesHash` | credentials: guarda el hash y al guardar de nuevo lo reemplaza (no duplica) | HU-003 / HU-005 / HU-004 | ✅ |
| CP-59 | Repository | JpaLoginAttempt/RefreshToken/Credential/ActivityLog/OneTimeCode adapters | `givenEvents_whenRecord_thenOneRowPerEvent` | activity log: guarda una fila por evento | HU-003 / HU-005 / HU-004 | ✅ |
| CP-60 | Repository | JpaLoginAttempt/RefreshToken/Credential/ActivityLog/OneTimeCode adapters | `givenSpecialCharacters_whenToJson_thenEscapesAndSortsKeys` | activity log: toJson ordena las claves y escapa comillas, barras y saltos de línea | HU-003 / HU-005 / HU-004 | ✅ |
| CP-61 | Repository | JpaLoginAttempt/RefreshToken/Credential/ActivityLog/OneTimeCode adapters | `givenControlCharactersAndNull_whenToJson_thenEscapesThem` | activity log: toJson escapa caracteres de control y trata null como vacío | HU-003 / HU-005 / HU-004 | ✅ |
| CP-62 | Repository | JpaLoginAttempt/RefreshToken/Credential/ActivityLog/OneTimeCode adapters | `givenIssuedCode_whenFullLifecycle_thenBehavesAsOneTimeCode` | one-time codes: emitir, buscar, fallar, consumir (una sola vez) | HU-003 / HU-005 / HU-004 | ✅ |
| CP-63 | Repository | JpaLoginAttempt/RefreshToken/Credential/ActivityLog/OneTimeCode adapters | `givenActiveCode_whenIssueAnother_thenOnlyTheNewOneIsActive` | one-time codes: un código nuevo invalida el anterior | HU-003 / HU-005 / HU-004 | ✅ |
| CP-64 | Repository | JpaLoginAttempt/RefreshToken/Credential/ActivityLog/OneTimeCode adapters | `givenExpiredCode_whenFindActive_thenEmpty` | one-time codes: un código vencido ya no está activo y sin emitir no hay historial | HU-003 / HU-005 / HU-004 | ✅ |
| CP-65 | Infraestructura | LocalAvatarStorage | `givenContent_whenStore_thenWritesFileAndReturnsUrl` | store: crea la carpeta, escribe el archivo y devuelve su URL pública | HU-007 | ✅ |
| CP-66 | Infraestructura | LocalAvatarStorage | `givenStoredAvatar_whenDelete_thenFileIsRemoved` | delete: borra la foto guardada | HU-007 | ✅ |
| CP-67 | Infraestructura | LocalAvatarStorage | `givenNullForeignOrMissingUrl_whenDelete_thenDoesNothing` | delete: ignora URLs nulas, ajenas o de un archivo que ya no existe | HU-007 | ✅ |
| CP-68 | Infraestructura | LocalAvatarStorage | `givenPathTraversal_whenDelete_thenOutsideFileSurvives` | delete: una ruta con ../ no puede borrar archivos fuera de la carpeta | HU-007 | ✅ |
| CP-69 | Infraestructura | BCrypt / SHA-256 / SecureRandom | `givenHashedPassword_whenMatches_thenOnlyTheRightOneMatches` | BCrypt: la contraseña correcta coincide y la incorrecta no | HU-001 / HU-003 | ✅ |
| CP-70 | Infraestructura | BCrypt / SHA-256 / SecureRandom | `givenNullHash_whenMatches_thenFalse` | BCrypt: un hash nulo nunca coincide | HU-001 / HU-003 | ✅ |
| CP-71 | Infraestructura | BCrypt / SHA-256 / SecureRandom | `givenSamePassword_whenHashedTwice_thenHashesDiffer` | BCrypt: el mismo texto produce hashes distintos (sal aleatoria) | HU-001 / HU-003 | ✅ |
| CP-72 | Infraestructura | BCrypt / SHA-256 / SecureRandom | `givenSecret_whenHashed_thenDeterministicHex` | SHA-256: es determinista, de 64 caracteres hex y compara bien | HU-001 / HU-003 | ✅ |
| CP-73 | Infraestructura | BCrypt / SHA-256 / SecureRandom | `givenGenerator_whenSixDigitCode_thenAlwaysSixDigits` | generador: el código tiene siempre 6 dígitos | HU-001 / HU-003 | ✅ |
| CP-74 | Infraestructura | BCrypt / SHA-256 / SecureRandom | `givenGenerator_whenOpaqueToken_thenUrlSafeAndUnique` | generador: el token opaco es URL-safe, largo y no se repite | HU-001 / HU-003 | ✅ |
| CP-75 | Infraestructura | InternalKeyGuard | `givenConfiguredKey_whenRequireWithSameKey_thenPasses` | require: la llave correcta pasa | Llave entre servicios | ✅ |
| CP-76 | Infraestructura | InternalKeyGuard | `givenConfiguredKey_whenRequireWithWrongOrNull_thenThrows` | require: una llave distinta o ausente se rechaza | Llave entre servicios | ✅ |
| CP-77 | Infraestructura | InternalKeyGuard | `givenMissingOrShortKey_whenRequire_thenAlwaysThrows` | require: sin llave configurada o demasiado corta, todo se rechaza | Llave entre servicios | ✅ |
| CP-78 | Infraestructura | SmtpNotificationSender | `givenVerificationCode_whenSend_thenSubjectHasCodeAndValidityInHours` | sendVerificationCode: asunto con el código y vigencia en horas | HU-002 / HU-005 | ✅ |
| CP-79 | Infraestructura | SmtpNotificationSender | `givenResetCode_whenSend_thenValidityInMinutes` | sendPasswordResetCode: vigencia en minutos | HU-002 / HU-005 | ✅ |
| CP-80 | Infraestructura | SmtpNotificationSender | `givenPasswordChanged_whenSend_thenNoCodeBlock` | sendPasswordChanged: el aviso no lleva código | HU-002 / HU-005 | ✅ |
| CP-81 | Infraestructura | SmtpNotificationSender | `givenHtmlInNameAndAction_whenSendActionCode_thenEscapesIt` | sendActionCode: escapa el HTML del nombre y de la acción, y usa 1 hora en singular | HU-002 / HU-005 | ✅ |
| CP-82 | Infraestructura | SmtpNotificationSender | `givenMailServerDown_whenSend_thenDoesNotThrow` | send: si el servidor de correo falla, no se propaga la excepción | HU-002 / HU-005 | ✅ |

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
| Fecha | 2026-10-09 |
| Comando | `.\mvnw clean test` |
| Entorno | Windows, Java 25.0.4.1, Maven Wrapper, Spring Boot 4.1.1 |
| Pruebas ejecutadas | **213** |
| Exitosas | **213** |
| Fallidas | 0 |
| Con error | 0 |
| Omitidas | 0 |
| Resultado | BUILD SUCCESS (≈ 23 s) |

Evolución:

| Corrida | Pruebas | Resultado |
|---|---|---|
| Pruebas existentes (antes de este trabajo) | 121 | 0 fallos |
| + Ronda 1 (servicios Mockito + repositorio de usuarios H2) | 176 | 0 fallos |
| + Ronda 2 (adaptadores, storage, seguridad, correo) — primer intento | 213 | 1 fallo: la prueba esperaba `válvula` sin escapar, pero `HtmlUtils.htmlEscape` la convierte en `v&aacute;lvula` (el código era correcto; se corrigió la expectativa) |
| + Ronda 2 corregida | **213** | **0 fallos** |

Capturas de la consola: `docs/testing/consola-mvnw-test.png` (pendiente de agregar al repositorio).

## 5. Cobertura (JaCoCo)

Informe HTML: `target/site/jacoco/index.html` (no se versiona).

**Total: 89,2 % de líneas (1208/1355) y 79,3 % de ramas (387/488).**

| Capa / paquete | Líneas | Ramas | Meta | Estado |
|---|---|---|---|---|
| `application.service` (Service) | 98,2 % | 80,7 % | ≥ 80 % | ✅ |
| `domain.model` (Dominio) | 97,8 % | 85,3 % | ≥ 90 % | ✅ |
| `infrastructure.persistence.adapter` (Repository) | 97,5 % | 90,9 % | ≥ 60 % | ✅ |
| `infrastructure.persistence.entity` | 81,1 % | 0,0 %¹ | ≥ 60 % | ✅ |
| `presentation.controller` (Controller) | 65,1 % | 80,0 % | ≥ 70 % | ⚠ 4,9 puntos por debajo |
| `infrastructure.notification` | 100 % | 87,5 % | — | ✅ |
| `infrastructure.storage` | 78,9 % | 100 % | — | ✅ |
| `infrastructure.security` | 70,7 % | 55,6 % | — | ✅ |
| `infrastructure.integration` | 90,6 % | 81,3 % | — | ✅ |
| `infrastructure.config` | 66,3 % | 50,0 % | — | ✅ |
| `presentation.error` | 85,7 % | 75,0 % | — | ✅ |
| `presentation.dto` | 35,7 % | 0,0 %¹ | — | ⚠ |
| `application.dto`, `application.port.in/out`, `domain.exception` | 100 % | —¹ | — | ✅ |
| `com.sywater.ms_iam` (clase `main`) | 33,3 % | 0,0 % | — | no se prueba (arranque) |
| **Total** | **89,2 %** | **79,3 %** | ≥ 75 % | ✅ |

¹ Registros, interfaces y entidades sin lógica de decisión: casi no tienen ramas.

### 5.1 Evolución de la cobertura total

| Momento | Pruebas | Líneas | Ramas |
|---|---|---|---|
| Pruebas existentes (antes) | 121 | no medido² | no medido² |
| Después de la ronda 1 | 176 | 71,9 % (974/1355) | 65,6 % (320/488) |
| Después de la ronda 2 | 213 | **89,2 %** (1208/1355) | **79,3 %** (387/488) |

² JaCoCo se agregó al proyecto junto con las pruebas nuevas, y la corrida de las 121 pruebas sola no se midió.

Capturas: `docs/testing/jacoco-resumen.png` (pendiente de agregar al repositorio).

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
| Todas las pruebas pasan | ✅ |
| Cobertura dentro de las metas | ✅ |
| Informe JaCoCo generado | ✅ |

## 7. Plan de mejora técnica

| # | Hallazgo | Acción | Estado |
|---|---|---|---|
| 1 | Los servicios solo tenían pruebas con dobles escritos a mano (`Fakes`); la guía pide Mockito | Pruebas Mockito de `AuthenticationService`, `OneTimeCodes`, `UserBlockingService` y `ActionCodeService` | Hecho (ronda 1) |
| 2 | La capa de persistencia no tenía pruebas unitarias (solo Testcontainers) | `@DataJpaTest` + H2: `JpaUserRepositoryAdapterTest` (ronda 1) y `PersistenceAdaptersTest` (ronda 2). Cobertura de `persistence.adapter`: 38,7 % → 97,5 % | Hecho |
| 3 | No se medía cobertura | JaCoCo 0.8.14 en `pom.xml`; informe en cada `mvnw test` | Hecho |
| 4 | Sin pruebas: envío de correo, almacenamiento de fotos, hashers, llave interna (0 % de cobertura en notification y storage) | `SmtpNotificationSenderTest`, `LocalAvatarStorageTest`, `HashersAndGeneratorTest`, `InternalKeyGuardTest`. Notification: 0 % → 100 %; storage: 0 % → 78,9 % | Hecho (ronda 2) |
| 5 | `presentation.controller` en 65,1 %, por debajo de la meta del 70 % | Agregar casos de error de `AuthController` / `ProfileController` (p. ej. carga de foto inválida, cuerpo mal formado) con `@WebMvcTest` | Pendiente |
| 6 | `infrastructure.security` en 70,7 %: faltan `NimbusAccessTokenIssuer` y `RedisTokenRevocationStore` | Pruebas con un par de llaves generado en la prueba y con `StringRedisTemplate` simulado | Pendiente |
| 7 | Pruebas antiguas con nombres que no siguen `given_when_then` | Renombrar al tocarlas | Pendiente |
| 8 | Refactor candidato: `AuthenticationService.login` mezcla validación, registro de intentos y bloqueo en un solo método (~45 líneas) | Extraer `registerFailure(...)` y `lockAccount(...)` manteniendo el comportamiento; las pruebas nuevas actúan como red de seguridad | Pendiente |
