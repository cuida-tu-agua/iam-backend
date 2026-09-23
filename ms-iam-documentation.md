# Documentación Completa - Microservicio ms-iam

## 📋 Tabla de Contenidos
1. [Arquitectura General](#arquitectura-general)
2. [Estructura del Proyecto](#estructura-del-proyecto)
3. [Componentes Principales](#componentes-principales)
4. [Flujos de Negocio](#flujos-de-negocio)
5. [Entidades JPA](#entidades-jpa)
6. [Configuración de Seguridad](#configuración-de-seguridad)
7. [API Endpoints](#api-endpoints)
8. [Configuración (application.yml)](#configuración-applicationyml)
9. [Dependencias (pom.xml)](#dependencias-pomxml)
10. [Ciclo de Vida de Datos](#ciclo-de-vida-de-datos)

---

## Arquitectura General

El ms-iam implementa **Arquitectura Hexagonal (Ports & Adapters)** con **Domain-Driven Design (DDD)**:

```
┌─────────────────────────────────────────────────────────────┐
│                    INFRASTRUCTURE LAYER                      │
│  (Controllers, Repositories, Configs, Security, Persistence) │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                   APPLICATION LAYER                          │
│         (Use Cases: Register, Login, Token Management)       │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                            │
│      (Entities, Value Objects, Business Logic, Rules)        │
└─────────────────────────────────────────────────────────────┘
```

**Principios:**
- Separación clara entre capas
- El dominio no depende de frameworks
- Los use cases orquestan la lógica de negocio
- La infraestructura inyecta dependencias via Spring

---

## Estructura del Proyecto

```
ms-iam/
├── src/main/java/com/sywater/ms_iam/
│   ├── MsIamApplication.java              # Main class
│   ├── domain/                             # Lógica de negocio pura
│   │   ├── entity/
│   │   │   ├── User.java                  # Entidad de Usuario
│   │   │   └── UserCredential.java        # Entidad de Credenciales
│   │   ├── valueobject/
│   │   │   └── Email.java                 # Value Object para Email
│   │   ├── exception/
│   │   │   ├── EmailAlreadyExistsException.java
│   │   │   ├── InvalidCredentialsException.java
│   │   │   └── UserNotFoundException.java
│   │   └── repository/
│   │       ├── UserRepository.java        # Interface (puerto)
│   │       └── UserCredentialRepository.java
│   ├── application/                        # Use Cases / Orquestación
│   │   └── usecase/
│   │       ├── RegisterUserUseCase.java   # Lógica de registro
│   │       └── LoginUseCase.java          # Lógica de autenticación
│   └── infrastructure/                     # Implementaciones concretas
│       ├── web/
│       │   └── AuthController.java        # REST endpoints
│       ├── persistence/
│       │   ├── entity/
│       │   │   ├── UserJpaEntity.java     # Mapeo JPA → User
│       │   │   └── UserCredentialJpaEntity.java
│       │   └── repository/
│       │       ├── UserJpaRepository.java
│       │       └── UserCredentialJpaRepository.java
│       ├── security/
│       │   ├── JwtTokenIssuer.java        # Generador de JWT (RS256)
│       │   ├── PasswordHasher.java        # BCrypt hashing
│       │   ├── PasswordValidator.java     # Validación de políticas
│       │   └── JwtTokenValidator.java     # Validación de tokens
│       ├── audit/
│       │   └── AuditLogger.java           # Logs de seguridad
│       ├── config/
│       │   ├── SecurityConfig.java        # Spring Security
│       │   ├── WebConfig.java             # CORS, Web config
│       │   └── JwtConfig.java             # Configuración JWT
│       └── filter/
│           └── JwtAuthenticationFilter.java # Filtro JWT
├── src/main/resources/
│   ├── application.yml                    # Configuración principal
│   ├── db/changelog/                      # Liquibase migrations
│   │   └── db.changelog-master.yaml
│   ├── keys/                              # Claves RSA
│   │   ├── private.pem
│   │   └── public.pem
│   └── logback-spring.xml                 # Configuración de logs
└── pom.xml                                # Dependencias Maven
```

---

## Componentes Principales

### 1️⃣ AuthController.java
**Ubicación:** `infrastructure/web/`

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
        @RequestBody RegisterRequest request) {
        // 1. Extrae datos del request
        // 2. Llama a RegisterUserUseCase
        // 3. Retorna 201 Created con userId
        // 4. Maneja excepciones: EmailAlreadyExistsException (409)
    }
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @RequestBody LoginRequest request) {
        // 1. Extrae email y password
        // 2. Llama a LoginUseCase
        // 3. Retorna 200 OK con JWT token
        // 4. Maneja excepciones: InvalidCredentialsException (401)
    }
}
```

**Responsabilidades:**
- ✅ Recibir requests HTTP
- ✅ Validar formato de entrada (delegado a @Valid)
- ✅ Llamar use cases
- ✅ Mapear respuestas a DTO
- ✅ Manejar excepciones de negocio
- ✅ Loguear intentos de registro/login

**Excepciones Manejadas:**
| Excepción | HTTP Code | Mensaje |
|-----------|-----------|---------|
| EmailAlreadyExistsException | 409 | El email ya está registrado |
| InvalidCredentialsException | 401 | Email o contraseña incorrectos |
| IllegalArgumentException | 400 | Validación de entrada falló |

---

### 2️⃣ RegisterUserUseCase.java
**Ubicación:** `application/usecase/`

```
┌─────────────────────────────────────────────────────┐
│     REGISTRO DE NUEVO USUARIO - FLUJO COMPLETO      │
└─────────────────────────────────────────────────────┘
    ↓
1. VALIDACIONES PREVIAS
   ├─ ¿Email vacío o nulo?
   ├─ ¿Email tiene formato válido? (RFC 5322)
   ├─ ¿Email ya existe en BD?
   ├─ ¿Nombre tiene 1-100 caracteres?
   └─ ¿Contraseña cumple política?
    ↓
2. CREAR USUARIO
   ├─ new UserJpaEntity(email, firstName, lastName)
   ├─ userRepository.save(user)
   └─ Asignar id generado por BD (BIGINT IDENTITY)
    ↓
3. CREAR CREDENCIALES
   ├─ Hash contraseña con BCrypt (strength=12)
   ├─ new UserCredentialJpaEntity(
   │    String.valueOf(userId),  ← Conversión Long→String
   │    hashedPassword
   │  )
   └─ credentialRepository.save(credential)
    ↓
4. RETORNAR RESPUESTA
   └─ RegisterResponse(
       userId: String.valueOf(user.getId()),
       email: user.getEmail(),
       createdAt: user.getCreatedAt()
     )
```

**Política de Contraseña:**
```regex
^(?=.*[a-z])        # Al menos 1 minúscula
(?=.*[A-Z])         # Al menos 1 mayúscula
(?=.*\d)            # Al menos 1 dígito
(?=.*[@$!%*?&])     # Al menos 1 carácter especial
[A-Za-z\d@$!%*?&]{8,}  # Mínimo 8 caracteres
```

**Ejemplo Válido:** `Admin@1234`
**Ejemplo Inválido:** `password` (sin mayúscula, sin dígito, sin especial)

**@Transactional:**
- Asegura que User Y Credential se guardan juntos
- Si una falla, ambas se revierten (rollback)
- Evita estados inconsistentes en BD

---

### 3️⃣ LoginUseCase.java
**Ubicación:** `application/usecase/`

```
┌─────────────────────────────────────────────────────┐
│    AUTENTICACIÓN (LOGIN) - FLUJO COMPLETO          │
└─────────────────────────────────────────────────────┘
    ↓
1. VALIDACIONES PREVIAS
   ├─ ¿Email vacío o nulo?
   ├─ ¿Password vacío o nulo?
   └─ ¿Email tiene formato válido?
    ↓
2. BUSCAR USUARIO
   ├─ userRepository.findByEmail(email)
   ├─ ¿Usuario existe?
   │  └─ NO → InvalidCredentialsException (WARN log)
   └─ SÍ → Continuar
    ↓
3. BUSCAR CREDENCIALES
   ├─ credentialRepository.findByUserId(String.valueOf(userId))
   ├─ ¿Credencial existe?
   │  └─ NO → ERROR log + Exception
   └─ SÍ → Continuar
    ↓
4. VALIDAR CONTRASEÑA
   ├─ BCrypt.matches(passwordPlain, passwordHashedEnBD)
   ├─ ¿Coinciden?
   │  └─ NO → WARN log + InvalidCredentialsException
   └─ SÍ → Continuar
    ↓
5. GENERAR JWT
   ├─ tokenIssuer.issueAccessToken(
   │    userId: String.valueOf(user.getId()),
   │    email: user.getEmail()
   │  )
   ├─ Algoritmo: RS256 (asimétrico)
   ├─ Firma con private.pem
   ├─ Claims:
   │  ├─ sub: userId
   │  ├─ email: email
   │  ├─ iat: ahora
   │  └─ exp: ahora + 1 hora
   └─ Retorna token JWT
    ↓
6. RETORNAR RESPUESTA
   └─ LoginResponse(
       userId: String.valueOf(user.getId()),
       email: user.getEmail(),
       accessToken: jwt,
       tokenType: "Bearer",
       expiresIn: 3600  # segundos
     )
    ↓
7. LOGGING
   └─ DEBUG: "Usuario autenticado exitosamente"
```

**Conversión de Tipos:**
```
user.getId()  →  Long (ej: 1)
                  ↓
String.valueOf(user.getId())  →  String (ej: "1")
                  ↓
Usado en métodos que esperan String
```

---

### 4️⃣ JwtTokenIssuer.java
**Ubicación:** `infrastructure/security/`

```
┌─────────────────────────────────────────────────────┐
│        GENERACIÓN DE JWT - RS256 ASIMÉTRICO         │
└─────────────────────────────────────────────────────┘

COMPONENTES:
├─ private.pem      (clave privada - FIRMA)
├─ public.pem       (clave pública - VERIFICACIÓN)
└─ Claims           (datos dentro del token)

ESTRUCTURA DEL JWT:
┌──────────────┬──────────────┬──────────────┐
│   HEADER     │   PAYLOAD    │  SIGNATURE   │
├──────────────┼──────────────┼──────────────┤
│ {            │ {            │ HMACSHA256(  │
│  "alg":      │  "sub":      │  base64url(  │
│  "RS256",    │  "1",        │    header)   │
│  "typ":      │  "email":    │  +           │
│  "JWT"       │  "juan@...", │  base64url(  │
│ }            │  "iat":      │    payload), │
│              │  1726...,    │  privateKey) │
│              │  "exp":      │              │
│              │  1726...     │              │
│              │ }            │              │
└──────────────┴──────────────┴──────────────┘
                    ↓
         Token Base64 entera
  eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.
  eyJzdWIiOiIxIiwiZW1haWwiOiJqdWFuQHN5d2F0ZXIuY29tIiwi...
  SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c...
```

**Flujo de Firma:**
1. Header (alg: RS256) + Payload (sub, email, iat, exp)
2. Base64 encode ambos
3. Concatenar: `header.payload`
4. Firmar con **private.pem** usando SHA-256
5. Base64 encode la firma
6. Resultado final: `header.payload.signature`

**Validación (en el cliente o en otro servicio):**
```
1. Base64 decode header, payload, signature
2. Reconstruir: header.payload
3. Verificar con public.pem que signature es válida
4. Validar claims: exp > ahora, iat < ahora
5. Si todo OK → Token válido
```

**Configuración en application.yml:**
```yaml
jwt:
  issuer: ms-iam
  key-id: default-key
  access-ttl: 1h        # 1 hora = 3600 segundos
  refresh-ttl: 7d       # 7 días (no implementado aún)
```

---

### 5️⃣ PasswordHasher.java
**Ubicación:** `infrastructure/security/`

```
┌─────────────────────────────────────────────────────┐
│       HASHING DE CONTRASEÑA - BCrypt (Strength=12)  │
└─────────────────────────────────────────────────────┘

CONTRASEÑA PLANA:
  "Admin@1234"
       ↓
  BCryptPasswordEncoder
  (strength=12, ~100ms por hash)
       ↓
HASH ALMACENADO EN BD:
  $2a$12$jq6H0gNJ5K9kL8mP0oQrS.
  eFjKxW0bZ9cL2qR5tU7vW8xY1a...
  (60 caracteres, incluye salt y costo)
       ↓
VALIDACIÓN EN LOGIN:
  matches("Admin@1234", "$2a$12$jq6H0gNJ5K9kL8...")
       ↓
  BCrypt extrae el salt del hash almacenado
  Re-computa el hash con el salt
  Compara con hash almacenado
       ↓
  ¿Coinciden? → SÍ: Login OK / NO: Fallido
```

**Por qué BCrypt es seguro:**
- ✅ Salt automático (no predecible)
- ✅ Costo adaptable (strength=12 es muy seguro)
- ✅ Lentitud intencional (dificulta fuerza bruta)
- ✅ Imposible revertir a contraseña original

**Tiempo de hashing:**
- Strength 10: ~10ms
- Strength 12: ~100ms (usado aquí)
- Strength 15: ~1 segundo

---

### 6️⃣ PasswordValidator.java
**Ubicación:** `infrastructure/security/`

```
VALIDACIÓN DE POLÍTICA DE CONTRASEÑA:

┌─────────────────────────────────────────────────────┐
│        REQUISITOS (se validan todos)                │
├─────────────────────────────────────────────────────┤
│ ✓ Mínimo 8 caracteres                              │
│ ✓ Al menos 1 mayúscula (A-Z)                       │
│ ✓ Al menos 1 minúscula (a-z)                       │
│ ✓ Al menos 1 dígito (0-9)                          │
│ ✓ Al menos 1 carácter especial (@$!%*?&)           │
└─────────────────────────────────────────────────────┘

EJEMPLOS:

VÁLIDA:
✓ Admin@1234    (mayús, minús, dígito, especial, 9 chars)
✓ Pass@word123  (mayús, minús, dígito, especial, 11 chars)
✓ MyP@ss99      (mayús, minús, dígito, especial, 8 chars)

INVÁLIDA:
✗ password      (sin mayúscula, sin dígito, sin especial)
✗ Admin1234     (sin carácter especial)
✗ Admin@pass    (sin dígito)
✗ Admin@1      (menos de 8 caracteres)
✗ ADMIN@1234    (sin minúscula)
```

**Método en Controller:**
```java
@PostMapping("/register")
public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
    if (!passwordValidator.isValid(req.getPassword())) {
        return ResponseEntity
            .badRequest()
            .body(Map.of(
                "error", "Contraseña no cumple requisitos",
                "requirements", passwordValidator.getRequirements()
            ));
    }
    // ... continuar con registro
}
```

---

### 7️⃣ AuditLogger.java
**Ubicación:** `infrastructure/audit/`

```
┌─────────────────────────────────────────────────────┐
│         AUDITORÍA - LOGS DE SEGURIDAD               │
└─────────────────────────────────────────────────────┘

EVENTOS REGISTRADOS:

1. REGISTRO EXITOSO
   logRegistrationAttempt(email, userId)
   ↓
   [2026-09-23 16:37:52] AUDIT - 
   Registro exitoso: juan@sywater.com (userId: 1)

2. INTENTO DE REGISTRO DUPLICADO
   logDuplicateEmail(email)
   ↓
   [2026-09-23 16:37:53] AUDIT - 
   Intento de registrar email ya existente: juan@sywater.com

3. LOGIN EXITOSO
   logLoginAttempt(email, userId)
   ↓
   [2026-09-23 16:37:54] AUDIT - 
   Login exitoso: juan@sywater.com (userId: 1)

4. LOGIN FALLIDO (Contraseña incorrecta)
   logInvalidCredentials(email)
   ↓
   [2026-09-23 16:37:55] AUDIT - 
   Intento de login fallido: juan@sywater.com
   (password incorrecta)

Logger separado:
- Nombre: "AUDIT"
- Nivel: INFO
- Destino: logs/audit.log (separado de application.log)
- Timestamp: Instant.now() (UTC)
```

---

### 8️⃣ SecurityConfig.java
**Ubicación:** `infrastructure/config/`

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            // 1. CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 2. CSRF (deshabilitado para /api/auth/**)
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/**"))
            
            // 3. AUTORIZACIONES
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()      // Público
                .requestMatchers("/actuator/health").permitAll()  // Health check público
                .requestMatchers("/actuator/**").authenticated()  // Actuator requiere auth
                .anyRequest().authenticated()                     // Todo lo demás requiere auth
            )
            
            // 4. SESIONES
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 5. FILTROS JWT
            .addFilterBefore(jwtAuthenticationFilter(), 
                UsernamePasswordAuthenticationFilter.class)
            
            // 6. MANEJO DE EXCEPCIONES
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            );
        
        return http.build();
    }
}
```

**Flujo de una Request:**

```
REQUEST → HTTP llega a servidor
   ↓
CORS Filter → ¿Origin permitido? (localhost:19006)
   ↓
CSRF Filter → ¿Es /api/auth/**? → Saltar
   ↓
JWT Authentication Filter → 
   ├─ Extrae header "Authorization: Bearer <token>"
   ├─ Valida token con public.pem
   ├─ Si OK → Security Context = usuario autenticado
   └─ Si NO → 401 Unauthorized
   ↓
Authorization Filter →
   ├─ ¿Request hacia /api/auth/**? → Permitir
   ├─ ¿Request hacia /actuator/health? → Permitir
   ├─ ¿Request hacia /actuator/**? → ¿Autenticado? Si NO → 403
   └─ ¿Request hacia otro lado? → ¿Autenticado? Si NO → 403
   ↓
RESPONSE → 200, 401, 403, etc.
```

---

### 9️⃣ JwtAuthenticationFilter.java
**Ubicación:** `infrastructure/filter/`

```
┌─────────────────────────────────────────────────────┐
│    JWT AUTHENTICATION FILTER - VALIDACIÓN TOKEN     │
└─────────────────────────────────────────────────────┘

REQUEST ENTRA:
  Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
        ↓
1. EXTRAE HEADER
   String authHeader = request.getHeader("Authorization")
   ├─ ¿Es null? → Dejar pasar (sin autenticación)
   ├─ ¿Empieza con "Bearer "? → NO → Error 401
   └─ ¿Empieza con "Bearer "? → SÍ → Extraer token
        ↓
2. EXTRAE TOKEN
   String token = authHeader.substring("Bearer ".length())
   (Ej: "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
        ↓
3. VALIDA TOKEN
   jwtTokenValidator.validateToken(token)
   ├─ Desencripta con public.pem
   ├─ Verifica firma RS256
   ├─ Valida exp > ahora
   ├─ Extrae claims (sub, email, iat, exp)
   └─ ¿Todo OK? → Continuar / NO → Lanzar excepción
        ↓
4. CREA SECURITY CONTEXT
   UsernamePasswordAuthenticationToken auth = 
       new UsernamePasswordAuthenticationToken(
           userId,
           null,
           authorities
       )
   SecurityContextHolder.getContext().setAuthentication(auth)
        ↓
5. CONTINÚA CON SIGUIENTE FILTRO
   filterChain.doFilter(request, response)
```

---

## Flujos de Negocio

### Flujo de Registro (Happy Path)
```
Cliente                    AuthController              Use Cases                     BD
   │                             │                         │                        │
   ├─ POST /api/auth/register───→│                         │                        │
   │  {email, firstName,         │                         │                        │
   │   lastName, password}       │                         │                        │
   │                             │                         │                        │
   │                             ├─ RegisterUserUseCase────→│                        │
   │                             │                         │                        │
   │                             │     ✓ Validar email     │                        │
   │                             │     ✓ Validar password  │                        │
   │                             │     ✓ Email no existe   │                        │
   │                             │                         │                        │
   │                             │     createUser()────────→│                        │
   │                             │                         │  INSERT users           │
   │                             │                         │──────────────────→      │
   │                             │                         │  RETURN userId: 1       │
   │                             │         ←───────────────│←───────────────────     │
   │                             │                         │                        │
   │                             │     hashPassword()      │                        │
   │                             │     (BCrypt strength=12) │                        │
   │                             │                         │                        │
   │                             │     createCredential()──→│                        │
   │                             │                         │  INSERT user_credentials│
   │                             │                         │──────────────────→      │
   │                             │                         │  (userId="1", hash)     │
   │                             │         RegisterResponse │←───────────────────     │
   │                             │←────────────────────────│                        │
   │                             │                         │                        │
   │  ←─ 201 Created ────────────│                         │                        │
   │  {userId: "1",               │                         │                        │
   │   email: juan@...,           │                         │                        │
   │   createdAt: 2026-09-23}     │                         │                        │
   │                             │                         │                        │
   └─ Guardar userId, mostrar ✓──┘                         │                        │
                                                           │                        │
[AUDIT LOG: Registro exitoso: juan@sywater.com (userId: 1)]
```

### Flujo de Login (Happy Path)
```
Cliente                    AuthController              Use Cases                     BD
   │                             │                         │                        │
   ├─ POST /api/auth/login──────→│                         │                        │
   │  {email, password}          │                         │                        │
   │                             │                         │                        │
   │                             ├─ LoginUseCase──────────→│                        │
   │                             │                         │                        │
   │                             │   ✓ Validar email      │                        │
   │                             │   ✓ Validar password   │                        │
   │                             │                         │                        │
   │                             │   findByEmail(email)───→│                        │
   │                             │                         │  SELECT * FROM users   │
   │                             │                         │  WHERE email=?         │
   │                             │                         │──────────────────→      │
   │                             │                         │  User encontrado       │
   │                             │         ←───────────────│←───────────────────     │
   │                             │                         │                        │
   │                             │   findByUserId()───────→│                        │
   │                             │                         │  SELECT * FROM        │
   │                             │                         │  user_credentials     │
   │                             │                         │  WHERE userId=?        │
   │                             │                         │──────────────────→      │
   │                             │                         │  Credential encontrado │
   │                             │         ←───────────────│←───────────────────     │
   │                             │                         │                        │
   │                             │   matches(plainPwd,    │                        │
   │                             │   hashedPwd)           │                        │
   │                             │   ✓ Contraseña OK      │                        │
   │                             │                         │                        │
   │                             │   issueAccessToken()   │                        │
   │                             │   Firmar JWT con private.pem                     │
   │                             │                         │                        │
   │                             │      LoginResponse      │                        │
   │                             │←────────────────────────│                        │
   │                             │                         │                        │
   │  ←─ 200 OK ─────────────────│                         │                        │
   │  {userId: "1",               │                         │                        │
   │   email: juan@...,           │                         │                        │
   │   accessToken: "eyJhbGciO...",                         │                        │
   │   tokenType: "Bearer",       │                         │                        │
   │   expiresIn: 3600}           │                         │                        │
   │                             │                         │                        │
   └─ Guardar JWT en localStorage│                         │                        │
     Enviar en próximas requests:│                         │                        │
     Authorization: Bearer <jwt> │                         │                        │
                                                           │                        │
[AUDIT LOG: Login exitoso: juan@sywater.com (userId: 1)]
```

### Flujo de Error - Email Duplicado
```
Cliente envía: POST /api/auth/register con email juan@sywater.com (ya existe)
                          ↓
RegisterUserUseCase valida:
  findByEmail(juan@sywater.com) → Encontrado
                          ↓
Lanza: EmailAlreadyExistsException
                          ↓
AuthController captura la excepción
                          ↓
@ExceptionHandler(EmailAlreadyExistsException.class)
ResponseEntity.status(409).body({
    "error": "El email ya está registrado",
    "email": "juan@sywater.com"
})
                          ↓
Cliente recibe: 409 Conflict
                          ↓
[AUDIT LOG: Intento de registrar email ya existente: juan@sywater.com]
```

---

## Entidades JPA

### UserJpaEntity.java
**Tabla:** `security.users` en SQL Server

```java
@Entity
@Table(name = "users", schema = "security")
public class UserJpaEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // BIGINT IDENTITY en BD
    
    @Column(unique = true, nullable = false)
    private String email;  // VARCHAR(255), UNIQUE
    
    @Column(name = "first_name")
    private String firstName;  // VARCHAR(100)
    
    @Column(name = "last_name")
    private String lastName;   // VARCHAR(100)
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;  // DATETIME2
    
    @Column(name = "email_verified")
    private Boolean emailVerified;  // BIT (0/1), default 0
    
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;  // DATETIME2, nullable
    
    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;  // Bloqueo temporal
}
```

**SQL en BD:**
```sql
CREATE TABLE security.users (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    created_at DATETIME2 DEFAULT GETUTCDATE(),
    email_verified BIT DEFAULT 0,
    email_verified_at DATETIME2 NULL,
    account_locked_until DATETIME2 NULL
);
```

---

### UserCredentialJpaEntity.java
**Tabla:** `security.user_credentials` en SQL Server

```java
@Entity
@Table(name = "user_credentials", schema = "security")
public class UserCredentialJpaEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // BIGINT IDENTITY en BD
    
    @Column(name = "user_id")
    private String userId;  // VARCHAR(255)
    // Referencia a users.id, pero como STRING
    // Permite flexibilidad: UUIDs, números, etc.
    
    @Column(name = "password_hash")
    private String passwordHash;  // VARCHAR(MAX)
    // Almacena hash BCrypt (~60 caracteres)
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;  // DATETIME2
    
    // Posibles campos futuros (ya en esquema):
    // mfa_enabled: Boolean
    // mfa_secret: String (para TOTP)
    // backup_codes: String (JSON array)
}
```

**SQL en BD:**
```sql
CREATE TABLE security.user_credentials (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    password_hash VARCHAR(MAX) NOT NULL,
    created_at DATETIME2 DEFAULT GETUTCDATE(),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**Por qué user_id es STRING:**
```
Beneficios:
✓ Flexibilidad: puede ser "1", "uuid-abc-123", "user@domain", etc.
✓ Separación de concerns: users.id (BIGINT) vs user_id (STRING reference)
✓ Futura federación: otros proveedores de identidad pueden usar IDs distintos
✓ Compatibilidad: cambiar formato de ID sin impactar user_credentials

Implementación en código:
  user.getId()  →  Long (ej: 1)
  String.valueOf(user.getId())  →  String (ej: "1")
  Usar String en user_credentials
```

---

## Configuración de Seguridad

### SecurityConfig.java - Detalle Completo

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Orígenes permitidos
        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:19006"  // React Native frontend
        ));
        
        // Métodos HTTP permitidos
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Headers permitidos en request
        config.setAllowedHeaders(Arrays.asList("*"));
        
        // Headers expuestos en response
        config.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type"
        ));
        
        // Permitir credenciales (cookies, auth headers)
        config.setAllowCredentials(true);
        
        // Cache: navegador no pregunta por 1 hora
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // CSRF: Deshabilitado para /api/auth/**, habilitado para resto
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/**"))
            
            // Autorización
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").authenticated()
                .anyRequest().authenticated()
            )
            
            // Sesiones: STATELESS (sin cookies)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Añadir JWT Filter antes de UsernamePasswordAuthenticationFilter
            .addFilterBefore(
                jwtAuthenticationFilter(),
                UsernamePasswordAuthenticationFilter.class
            )
            
            // Manejo de excepciones
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            );
        
        return http.build();
    }
    
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenValidator());
    }
    
    @Bean
    public JwtTokenValidator jwtTokenValidator() {
        return new JwtTokenValidator(publicKeyProvider());
    }
}
```

---

## API Endpoints

### POST /api/auth/register
**Registrar nuevo usuario**

**Request:**
```json
{
  "email": "juan@sywater.com",
  "firstName": "Juan",
  "lastName": "Pérez",
  "password": "Admin@1234"
}
```

**Response 201 Created:**
```json
{
  "userId": "1",
  "email": "juan@sywater.com",
  "createdAt": "2026-09-23T16:37:52Z"
}
```

**Errores:**
| Caso | HTTP | Response |
|------|------|----------|
| Email vacío | 400 | `{"error": "Email no puede estar vacío"}` |
| Email inválido | 400 | `{"error": "Email no es válido"}` |
| Email ya existe | 409 | `{"error": "El email ya está registrado"}` |
| Password muy corta | 400 | `{"error": "Contraseña no cumple requisitos"}` |
| Nombre muy largo | 400 | `{"error": "Nombre no debe exceder 100 caracteres"}` |

---

### POST /api/auth/login
**Autenticarse y obtener JWT**

**Request:**
```json
{
  "email": "juan@sywater.com",
  "password": "Admin@1234"
}
```

**Response 200 OK:**
```json
{
  "userId": "1",
  "email": "juan@sywater.com",
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJqdWFuQHN5d2F0ZXIuY29tIiwiaWF0IjoxNjI2MTAwMDAwLCJleHAiOjE2MjYxMDM2MDB9.signature",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**Errores:**
| Caso | HTTP | Response |
|------|------|----------|
| Email no existe | 401 | `{"error": "Email o contraseña incorrectos"}` |
| Password incorrecta | 401 | `{"error": "Email o contraseña incorrectos"}` |
| Email vacío | 400 | `{"error": "Email no puede estar vacío"}` |
| Password vacía | 400 | `{"error": "Password no puede estar vacío"}` |

---

## Configuración (application.yml)

```yaml
spring:
  application:
    name: ms-iam
  
  # DATASOURCE - SQL Server
  datasource:
    url: jdbc:sqlserver://localhost:1433;database=sy-water-db;trustServerCertificate=true
    username: security_app
    password: ${SECURITY_APP_PASSWORD}
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
    
    # HikariCP Connection Pool
    hikari:
      maximum-pool-size: 10           # Máx 10 conexiones simultáneas
      minimum-idle: 2                 # Al menos 2 inactivas
      connection-timeout: 20000       # 20 segundos para obtener conexión
      idle-timeout: 300000            # 5 minutos sin usar → cerrar
      max-lifetime: 1800000           # 30 minutos máximo por conexión
      auto-commit: true
      leak-detection-threshold: 60000 # Alerta si conexión > 1 minuto sin cerrar
  
  # JPA / Hibernate
  jpa:
    database-platform: org.hibernate.dialect.SQLServerDialect
    hibernate:
      ddl-auto: validate              # Solo validar, no crear
    show-sql: false
    properties:
      hibernate:
        jdbc:
          batch_size: 20
          fetch_size: 50
        order_inserts: true
        order_updates: true
        type:
          preferred_instant_jdbc_type: instant
  
  # Redis (configurado pero no conectado aún)
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
      database: 0  # Base de datos 0 (de 0-15)
  
  # Security CORS
  web:
    cors:
      allowed-origins: http://localhost:19006
      allowed-methods: GET,POST,PUT,DELETE,OPTIONS
      allowed-headers: "*"
      max-age: 3600
  
  # Logging
  logging:
    level:
      root: WARN
      com.sywater.ms_iam: INFO
      org.springframework.security: WARN
    pattern:
      console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
      file: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file:
      name: logs/application.log
      max-size: 10MB
      max-history: 10

# JWT Configuration
jwt:
  issuer: ms-iam
  key-id: default-key
  access-ttl: 1h        # Access token: 1 hora
  refresh-ttl: 7d       # Refresh token: 7 días (no implementado)
  rsa:
    private-key-path: classpath:keys/private.pem
    public-key-path: classpath:keys/public.pem

# Server
server:
  port: 8081
  servlet:
    context-path: /
  error:
    include-message: always
    include-stacktrace: on_param

# Actuator (Health & Monitoring)
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

---

## Dependencias (pom.xml)

```xml
<project>
  <groupId>com.sywater</groupId>
  <artifactId>ms-iam</artifactId>
  <version>1.0.0</version>
  <packaging>jar</packaging>
  
  <properties>
    <java.version>25</java.version>
    <maven.compiler.source>25</maven.compiler.source>
    <maven.compiler.target>25</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  
  <dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
      <version>4.1.1</version>
    </dependency>
    
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
      <version>4.1.1</version>
    </dependency>
    
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
      <version>4.1.1</version>
    </dependency>
    
    <!-- SQL Server JDBC Driver -->
    <dependency>
      <groupId>com.microsoft.sqlserver</groupId>
      <artifactId>mssql-jdbc</artifactId>
      <version>13.4.0.jre11</version>
    </dependency>
    
    <!-- Hibernate ORM -->
    <dependency>
      <groupId>org.hibernate.orm</groupId>
      <artifactId>hibernate-core</artifactId>
      <version>7.4.5.Final</version>
    </dependency>
    
    <!-- Jakarta EE 10 (no javax) -->
    <dependency>
      <groupId>jakarta.servlet</groupId>
      <artifactId>jakarta.servlet-api</artifactId>
      <version>6.0.0</version>
      <scope>provided</scope>
    </dependency>
    
    <!-- JWT (JSON Web Token) -->
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>0.12.6</version>
    </dependency>
    
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>0.12.6</version>
      <scope>runtime</scope>
    </dependency>
    
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>0.12.6</version>
      <scope>runtime</scope>
    </dependency>
    
    <!-- Redis -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
      <version>4.1.1</version>
    </dependency>
    
    <!-- Liquibase (DB Versioning) -->
    <dependency>
      <groupId>org.liquibase</groupId>
      <artifactId>liquibase-core</artifactId>
      <version>4.25.0</version>
    </dependency>
    
    <!-- Lombok (Reducir boilerplate) -->
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <version>1.18.30</version>
      <scope>provided</scope>
    </dependency>
    
    <!-- Testing -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <version>4.1.1</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
  
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <version>4.1.1</version>
      </plugin>
    </plugins>
  </build>
</project>
```

---

## Ciclo de Vida de Datos

### Registro de Usuario

```
┌─────────────────────────────────────────────────────────────┐
│                  FLUJO DE DATOS - REGISTRO                  │
└─────────────────────────────────────────────────────────────┘

1. CLIENT-SIDE (React Native)
   ┌────────────────────────────┐
   │ Formulario Registro        │
   │ input[email] = "juan@..."  │
   │ input[password] = "Admin@1"│
   │ input[firstName] = "Juan"  │
   │ input[lastName] = "Pérez"  │
   └────────────────────────────┘
         ↓
   Validar localmente (UX)
         ↓
   POST /api/auth/register
   Content-Type: application/json
   {
     "email": "juan@sywater.com",
     "firstName": "Juan",
     "lastName": "Pérez",
     "password": "Admin@1234"  ← PLANA (HTTPS solo)
   }

2. SERVER-SIDE (AuthController)
   ┌────────────────────────────┐
   │ AuthController.register()  │
   │ ├─ @Valid registra request │
   │ ├─ Loguea intento          │
   │ └─ Llama RegisterUserUseCase│
   └────────────────────────────┘

3. APPLICATION LAYER (RegisterUserUseCase)
   ┌──────────────────────────────────────┐
   │ Validaciones                         │
   │ ✓ Email no vacío                     │
   │ ✓ Email válido (RFC 5322)            │
   │ ✓ Email no duplicado (query BD)      │
   │ ✓ Nombres 1-100 caracteres           │
   │ ✓ Contraseña cumple política         │
   └──────────────────────────────────────┘
         ↓
   Crear UserJpaEntity
   {
     email: "juan@sywater.com",
     firstName: "Juan",
     lastName: "Pérez",
     createdAt: LocalDateTime.now(ZoneId.of("UTC"))
   }
         ↓
   userRepository.save(user)

4. PERSISTENCE LAYER (JPA/Hibernate)
   ┌────────────────────────────────────┐
   │ Hibernate genera SQL                │
   │ INSERT INTO security.users          │
   │ (email, first_name, last_name,      │
   │  created_at)                        │
   │ VALUES (?, ?, ?, ?)                 │
   └────────────────────────────────────┘
         ↓
   SQL Server ejecuta & retorna id generado
   (IDENTITY, BIGINT: 1, 2, 3, ...)
         ↓
   JPA setta: user.id = 1

5. CREAR CREDENCIALES
   ┌──────────────────────────────────────┐
   │ PasswordHasher.hash(plainPassword)   │
   │ BCryptPasswordEncoder.encode()       │
   │ strength=12 (~100ms)                 │
   │                                      │
   │ plainPassword:  "Admin@1234"         │
   │       ↓                              │
   │ hash: "$2a$12$jq6H0gNJ5K9kL8mP0...  │
   │       (60 caracteres, incluye salt)  │
   └──────────────────────────────────────┘
         ↓
   Crear UserCredentialJpaEntity
   {
     userId: String.valueOf(user.getId())  # "1"
     passwordHash: "$2a$12$jq6H0gNJ5K9...",
     createdAt: LocalDateTime.now()
   }
         ↓
   credentialRepository.save(credential)

6. PERSISTENCE - CREDENCIALES
   ┌────────────────────────────────────┐
   │ INSERT INTO security.user_credentials│
   │ (user_id, password_hash, created_at)│
   │ VALUES (?, ?, ?)                    │
   └────────────────────────────────────┘
         ↓
   SQL Server inserta y retorna id generado
         ↓
   JPA setta: credential.id = 1

7. RESPUESTA AL CLIENTE
   ┌────────────────────────────────────┐
   │ RegisterResponse                   │
   │ {                                  │
   │   userId: "1",                     │
   │   email: "juan@sywater.com",       │
   │   createdAt: "2026-09-23T16:37:52Z"│
   │ }                                  │
   └────────────────────────────────────┘
         ↓
   HTTP 201 Created
         ↓
   [AUDIT LOG: Registro exitoso: juan@sywater.com (userId: 1)]

8. ESTADO EN BD
   ┌─ security.users ─────────────────────┐
   │ id=1, email=juan@..., firstName=Juan │
   │ lastName=Pérez, created_at=2026-..., │
   │ email_verified=0                      │
   └───────────────────────────────────────┘
   
   ┌─ security.user_credentials ──────────┐
   │ id=1, user_id="1",                    │
   │ password_hash=$2a$12$jq6H0gNJ5K9..., │
   │ created_at=2026-...                   │
   └───────────────────────────────────────┘

9. CLIENTE ALMACENA
   localStorage.setItem("userId", "1")
   localStorage.setItem("email", "juan@sywater.com")
   Mostrar: "¡Registro exitoso! Ahora inicia sesión"
```

### Autenticación (Login)

```
┌─────────────────────────────────────────────────────────────┐
│                    FLUJO DE DATOS - LOGIN                   │
└─────────────────────────────────────────────────────────────┘

1. CLIENTE INICIA SESIÓN
   POST /api/auth/login
   {
     "email": "juan@sywater.com",
     "password": "Admin@1234"  ← PLANA (solo en HTTPS)
   }

2. AUTENTICACIÓN (LoginUseCase)
   
   Paso A: Buscar usuario
   ┌────────────────────────────────────┐
   │ SELECT * FROM security.users       │
   │ WHERE email = 'juan@sywater.com'   │
   └────────────────────────────────────┘
         ↓
   ¿Encontrado? NO → InvalidCredentialsException (401)
                    Loguear: WARN
   
   Paso B: Buscar credenciales
   ┌────────────────────────────────────┐
   │ SELECT * FROM user_credentials     │
   │ WHERE user_id = '1'                │
   └────────────────────────────────────┘
         ↓
   ¿Encontrado? NO → ERROR log + Exception
   
   Paso C: Validar contraseña
   ┌──────────────────────────────────────────┐
   │ BCryptPasswordEncoder.matches(            │
   │   plainPassword: "Admin@1234",            │
   │   hashedPassword: "$2a$12$jq6H0gNJ5K9..." │
   │ )                                         │
   │ ├─ Extrae salt del hash almacenado        │
   │ ├─ Re-computa hash con salt               │
   │ └─ Compara con hash almacenado            │
   └──────────────────────────────────────────┘
         ↓
   ¿Coinciden? NO → InvalidCredentialsException (401)
                    Loguear: WARN

3. GENERAR JWT (JwtTokenIssuer)
   
   Datos del token:
   {
     "alg": "RS256",      # Algoritmo
     "typ": "JWT",        # Tipo
     "kid": "default-key" # Key ID
   }
   
   Claims (payload):
   {
     "sub": "1",                        # Subject (userId)
     "email": "juan@sywater.com",       # Email
     "iat": 1726000000,                 # Emitido en
     "exp": 1726003600,                 # Expira en (iat + 1h)
     "iss": "ms-iam"                    # Emisor
   }
         ↓
   Firmar con private.pem (RSA-256):
   signature = HMACSHA256(
     base64(header) + "." + base64(payload),
     privateKey
   )
         ↓
   JWT final:
   eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.
   eyJzdWIiOiIxIiwiZW1haWwiOiJqdWFuQHN5d2F0ZXIuY29tIiwi...
   SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c...

4. RESPUESTA AL CLIENTE
   ┌──────────────────────────────────────────┐
   │ HTTP 200 OK                              │
   │ LoginResponse:                           │
   │ {                                        │
   │   userId: "1",                           │
   │   email: "juan@sywater.com",             │
   │   accessToken: "eyJhbGciOiJSUzI1Ni...",  │
   │   tokenType: "Bearer",                   │
   │   expiresIn: 3600  # segundos            │
   │ }                                        │
   └──────────────────────────────────────────┘

5. CLIENTE ALMACENA JWT
   localStorage.setItem("accessToken", "eyJhbGciO...")
   localStorage.setItem("expiresAt", Date.now() + 3600000)
   localStorage.setItem("userId", "1")

6. PRÓXIMAS REQUESTS
   GET /api/device/1/readings
   Authorization: Bearer eyJhbGciOiJSUzI1Ni...
            ↓
   JwtAuthenticationFilter intercepta:
   ├─ Extrae "eyJhbGciOiJSUzI1Ni..." del header
   ├─ Desencripta con public.pem
   ├─ Verifica firma RS256
   ├─ Valida exp > ahora
   ├─ Extrae claims (sub="1", email="juan@...")
   ├─ Crea SecurityContext con userId="1"
   └─ Deja pasar a siguiente filtro
            ↓
   Endpoint recibe authenticated user
   Puede usar: SecurityContextHolder.getContext()
                 .getAuthentication().getPrincipal()

7. LOGS DE AUDITORÍA
   [2026-09-23 16:37:52] AUDIT - 
   Login exitoso: juan@sywater.com (userId: 1)
```

---

## Posibles Mejoras Futuras

1. **Refresh Token Flow**
   - Endpoint: `POST /api/auth/refresh`
   - Recibe: `{ refreshToken: "..." }`
   - Retorna: nuevo `accessToken`
   - Almacenar en Redis con TTL 7 días

2. **Email Verification**
   - Campo `emailVerified` ya existe en tabla
   - Flujo: Enviar email → Link con código → Validar
   - Endpoint: `GET /api/auth/verify?token=abc123`

3. **Password Reset**
   - Endpoint: `POST /api/auth/forgot-password`
   - Enviar email con link reset
   - Endpoint: `POST /api/auth/reset-password`

4. **Multi-Factor Authentication (MFA)**
   - Campos preparados: `mfaEnabled`, `mfaSecret`
   - Implementar TOTP (Time-based One-Time Password)
   - Google Authenticator, Microsoft Authenticator

5. **Account Lockout**
   - Campo `accountLockedUntil` existe
   - Bloquear tras N intentos fallidos
   - Auto-desbloqueo tras tiempo configurado

6. **Token Revocation/Denylist**
   - Conectar Redis
   - Al logout, almacenar token en denylist
   - TTL = exp del token

7. **OAuth2 / Social Login**
   - Google, GitHub, Microsoft
   - Spring Security OAuth2

8. **Audit Trail Persistente**
   - Crear tabla `audit_logs`
   - Registrar todos los eventos
   - Query: `SELECT * FROM audit_logs WHERE event_type='LOGIN'`

---

## Notas Arquitectónicas

### Por qué userId es STRING en user_credentials

```
Escenario 1: Sistema monolítico
  users.id = 1 (BIGINT)
  user_credentials.user_id = "1" (VARCHAR)
  
Escenario 2: Sistema federado futuro
  users.id = 1 (BIGINT) - Mi BD
  external_id = "google-12345" (STRING) - Google
  user_credentials.user_id = "google-12345" (VARCHAR)
  
Ventaja: Cambiar format sin migrar user_credentials
```

### Transactionality en Registro

```
@Transactional
public RegisterResponse register(RegisterRequest req) {
    // Línea 1-50: Validaciones
    
    // Línea 51: Guardar usuario
    User savedUser = userRepository.save(user);
    
    // Línea 52-70: Hash password, crear credencial
    UserCredential credential = new UserCredential(
        String.valueOf(savedUser.getId()),
        hashedPassword
    );
    credentialRepository.save(credential);  // Línea 67
    
    // Si aquí hay error → Rollback automático de ambos
    return new RegisterResponse(...);
}
```

### Separación entre Entidades JPA y Domain

```
┌─────────────────────────────────────┐
│   UserJpaEntity (infraestructura)   │
│   @Entity, @Table, @Column, @Id     │
│   Mapeo directo a BD                │
└─────────────────────────────────────┘
           ↕ Mapper
┌─────────────────────────────────────┐
│   User (dominio)                    │
│   Lógica de negocio pura            │
│   Sin anotaciones Spring             │
└─────────────────────────────────────┘

Beneficios:
✓ Dominio independiente de frameworks
✓ Cambiar BD sin cambiar lógica
✓ Testeable sin Spring
✓ Reutilizable en otros contextos
```

---

**Documentación completa generada el 2026-09-23**

**Versión del Microservicio:** 1.0.0  
**Java:** 25  
**Spring Boot:** 4.1.1  
**Base de Datos:** SQL Server (esquema: security)
