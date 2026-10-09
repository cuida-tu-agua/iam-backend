package com.sywater.ms_iam.infrastructure.persistence;

import com.sywater.ms_iam.application.dto.PageView;
import com.sywater.ms_iam.application.dto.UserCounts;
import com.sywater.ms_iam.domain.exception.EmailAlreadyRegisteredException;
import com.sywater.ms_iam.domain.model.AccountStatus;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.PhoneNumber;
import com.sywater.ms_iam.domain.model.Role;
import com.sywater.ms_iam.domain.model.User;
import com.sywater.ms_iam.infrastructure.persistence.adapter.JpaUserRepositoryAdapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Capa de persistencia: el adaptador JPA contra una base H2 en memoria (no toca SQL Server).
 * Hibernate crea las tablas a partir de las entidades; los roles se siembran a mano, como hace Liquibase en producción.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-test;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS security",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.use_nationalized_character_data=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaUserRepositoryAdapter.class)
class JpaUserRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-29T15:00:00Z");

    @Autowired private JpaUserRepositoryAdapter repository;
    @Autowired private EntityManager em;

    @BeforeEach
    void seedRolesAndConstraints() {
        // The adapter recognises the duplicate by the name of this constraint (it exists in the real schema).
        // DDL commits in H2, so it is written to be repeatable: the schema lives as long as the Spring context.
        em.createNativeQuery("alter table security.users add constraint if not exists \"UQ_users_email\" unique (email)")
                .executeUpdate();
        em.createNativeQuery("merge into security.roles (id, code, description, created_at) key (id) values (1, 'USER', 'u', current_timestamp)")
                .executeUpdate();
        em.createNativeQuery("merge into security.roles (id, code, description, created_at) key (id) values (2, 'ADMIN', 'a', current_timestamp)")
                .executeUpdate();
    }

    private User newUser(String first, String last, String email, String phone, boolean verified) {
        User user = User.register(first, last, new Email(email), PhoneNumber.ofNullable(phone), NOW);
        if (verified) user.verifyEmail(NOW);
        return user;
    }

    @Test
    @DisplayName("create + findByEmail: guarda el usuario con su rol y lo recupera igual")
    void givenNewUser_whenCreate_thenFindByEmailReturnsItWithRole() {
        // Arrange
        User ana = newUser("Ana", "Gómez", "ana@correo.com", "3001234567", false);

        // Act
        repository.create(ana);
        Optional<User> found = repository.findByEmail(new Email("ANA@correo.com"));

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(ana.id());
        assertThat(found.get().fullName()).isEqualTo("Ana Gómez");
        assertThat(found.get().phone().value()).isEqualTo("3001234567");
        assertThat(found.get().roles()).containsExactly(Role.USER);
        assertThat(found.get().isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("create: un correo repetido lanza EmailAlreadyRegisteredException")
    void givenExistingEmail_whenCreate_thenThrowsEmailAlreadyRegistered() {
        // Arrange
        repository.create(newUser("Ana", "Gómez", "ana@correo.com", null, false));

        // Act + Assert
        assertThatThrownBy(() -> repository.create(newUser("Otra", "Persona", "ana@correo.com", null, false)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    @DisplayName("existsByEmail / findById: distinguen entre existente e inexistente")
    void givenStoredUser_whenQueryingExistence_thenReflectsStoredData() {
        // Arrange
        User ana = newUser("Ana", "Gómez", "ana@correo.com", null, false);
        repository.create(ana);

        // Act + Assert
        assertThat(repository.existsByEmail(new Email("ana@correo.com"))).isTrue();
        assertThat(repository.existsByEmail(new Email("nadie@correo.com"))).isFalse();
        assertThat(repository.findById(ana.id())).isPresent();
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("phoneTakenByOther: ignora al propio usuario y detecta a otro")
    void givenPhoneOfAna_whenPhoneTakenByOther_thenOnlyOthersCount() {
        // Arrange
        User ana = newUser("Ana", "Gómez", "ana@correo.com", "3001234567", false);
        repository.create(ana);
        PhoneNumber phone = new PhoneNumber("3001234567");

        // Act + Assert
        assertThat(repository.phoneTakenByOther(phone, ana.id())).isFalse();
        assertThat(repository.phoneTakenByOther(phone, UUID.randomUUID())).isTrue();
        assertThat(repository.findByPhone(phone)).isPresent();
    }

    @Test
    @DisplayName("update: persiste el bloqueo hecho en el dominio")
    void givenBlockedUser_whenUpdate_thenBlockIsPersisted() {
        // Arrange
        User ana = newUser("Ana", "Gómez", "ana@correo.com", null, true);
        repository.create(ana);
        UUID admin = UUID.randomUUID();
        ana.block(admin, NOW.plusSeconds(60));

        // Act
        repository.update(ana);
        em.flush();
        em.clear();

        // Assert
        User reloaded = repository.findById(ana.id()).orElseThrow();
        assertThat(reloaded.isBlocked()).isTrue();
        assertThat(reloaded.blockedBy()).isEqualTo(admin);
        assertThat(reloaded.status()).isEqualTo(AccountStatus.BLOCKED);
    }

    @Test
    @DisplayName("countByStatus: separa activos, bloqueados y sin verificar, y no cuenta eliminados")
    void givenUsersInEveryState_whenCountByStatus_thenCountsEachGroup() {
        // Arrange
        User active = newUser("Ana", "Gómez", "a1@correo.com", null, true);
        User blocked = newUser("Beto", "Ruiz", "b1@correo.com", null, true);
        blocked.block(UUID.randomUUID(), NOW);
        User unverified = newUser("Carla", "Díaz", "c1@correo.com", null, false);
        User deleted = newUser("Dino", "Vega", "d1@correo.com", null, true);
        deleted.delete(NOW);
        for (User u : new User[]{active, blocked, unverified, deleted}) repository.create(u);

        // Act
        UserCounts counts = repository.countByStatus();

        // Assert
        assertThat(counts.active()).isEqualTo(1);
        assertThat(counts.blocked()).isEqualTo(1);
        assertThat(counts.unverified()).isEqualTo(1);
        assertThat(counts.total()).isEqualTo(3);
    }

    @Test
    @DisplayName("search: filtra por texto y por estado, y pagina")
    void givenSeveralUsers_whenSearch_thenFiltersByTextStatusAndPage() {
        // Arrange
        repository.create(newUser("Ana", "Gómez", "ana@correo.com", null, true));
        repository.create(newUser("Anabel", "Ruiz", "anabel@correo.com", null, false));
        repository.create(newUser("Luis", "Pérez", "luis@correo.com", null, true));

        // Act
        PageView<User> byText = repository.search("ana", null, 0, 10);
        PageView<User> byStatus = repository.search("ana", AccountStatus.UNVERIFIED, 0, 10);
        PageView<User> firstPage = repository.search("", null, 0, 2);

        // Assert
        assertThat(byText.totalItems()).isEqualTo(2);
        assertThat(byStatus.items()).extracting(u -> u.email().value()).containsExactly("anabel@correo.com");
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("search: un % escrito por el usuario es texto, no un comodín")
    void givenPercentSign_whenSearch_thenItIsNotAWildcard() {
        // Arrange
        repository.create(newUser("Ana", "Gómez", "ana@correo.com", null, true));

        // Act
        PageView<User> result = repository.search("%", null, 0, 10);

        // Assert
        assertThat(result.totalItems()).isZero();
    }

    @Test
    @DisplayName("search: las cuentas eliminadas nunca aparecen")
    void givenDeletedUser_whenSearch_thenItIsExcluded() {
        // Arrange
        User gone = newUser("Dino", "Vega", "dino@correo.com", null, true);
        gone.delete(NOW);
        repository.create(gone);

        // Act
        PageView<User> result = repository.search("", null, 0, 10);

        // Assert
        assertThat(result.items()).isEmpty();
    }
}
