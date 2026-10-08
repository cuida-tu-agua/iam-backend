package com.sywater.ms_iam.application;

import com.sywater.ms_iam.application.port.out.AccessTokenIssuer;
import com.sywater.ms_iam.application.port.out.ActivityLog;
import com.sywater.ms_iam.application.port.out.AvatarStorage;
import com.sywater.ms_iam.application.port.out.CredentialRepository;
import com.sywater.ms_iam.application.port.out.DeviceCleanup;
import com.sywater.ms_iam.application.port.out.LoginAttemptRepository;
import com.sywater.ms_iam.application.port.out.NotificationSender;
import com.sywater.ms_iam.application.port.out.OneTimeCodeRepository;
import com.sywater.ms_iam.application.port.out.PasswordHasher;
import com.sywater.ms_iam.application.port.out.RefreshTokenRepository;
import com.sywater.ms_iam.application.port.out.SecretGenerator;
import com.sywater.ms_iam.application.port.out.SecretHasher;
import com.sywater.ms_iam.application.port.out.TokenRevocationStore;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.model.Email;
import com.sywater.ms_iam.domain.model.PhoneNumber;
import com.sywater.ms_iam.domain.model.User;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
public final class Fakes {

    private Fakes() {
    }

    public static final class Users implements UserRepository {
        public final Map<UUID, User> byId = new HashMap<>();
        public int updates;

        @Override public Optional<User> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
        @Override public Optional<User> findByEmail(Email email) {
            return byId.values().stream().filter(u -> u.email().equals(email)).findFirst();
        }
        @Override public Optional<User> findByPhone(PhoneNumber phone) {
            return byId.values().stream().filter(u -> phone.equals(u.phone())).findFirst();
        }
        @Override public boolean existsByEmail(Email email) { return findByEmail(email).isPresent(); }
        @Override public boolean phoneTakenByOther(PhoneNumber phone, UUID exceptUserId) {
            return byId.values().stream().anyMatch(u -> phone.equals(u.phone()) && !u.id().equals(exceptUserId));
        }
        @Override public void create(User user) { byId.put(user.id(), user); }
        @Override public void update(User user) { updates++; byId.put(user.id(), user); }
        @Override public com.sywater.ms_iam.application.dto.PageView<User> search(String text, com.sywater.ms_iam.domain.model.AccountStatus status, int page, int size) {
            String needle = text == null ? "" : text.trim().toLowerCase();
            List<User> all = byId.values().stream()
                    .filter(u -> !u.isDeleted())
                    .filter(u -> status == null || u.status() == status)
                    .filter(u -> needle.isEmpty() || u.email().value().toLowerCase().contains(needle)
                            || u.fullName().toLowerCase().contains(needle))
                    .sorted(java.util.Comparator.comparing(User::createdAt).reversed().thenComparing(User::id))
                    .toList();
            List<User> slice = all.stream().skip((long) page * size).limit(size).toList();
            return com.sywater.ms_iam.application.dto.PageView.of(slice, page, size, all.size());
        }
    }

    public static final class Credentials implements CredentialRepository {
        public final Map<UUID, String> hashes = new HashMap<>();
        @Override public Optional<String> findPasswordHash(UUID userId) { return Optional.ofNullable(hashes.get(userId)); }
        @Override public void savePasswordHash(UUID userId, String passwordHash) { hashes.put(userId, passwordHash); }
    }

    /** "hash" = "H(" + raw + ")": readable in assertions and never equal to the raw value. */
    public static final class Hasher implements PasswordHasher, SecretHasher {
        @Override public String hash(String raw) { return "H(" + raw + ")"; }
        @Override public boolean matches(String raw, String hash) { return Objects.equals(hash(raw), hash); }
    }

    /** Returns queued codes/tokens so the test knows the plain values. */
    public static final class Generator implements SecretGenerator {
        public final Deque<String> codes = new ArrayDeque<>(List.of("111111", "222222", "333333", "444444"));
        private int tokens;
        @Override public String sixDigitCode() { return codes.isEmpty() ? "999999" : codes.poll(); }
        @Override public String opaqueToken() { return "refresh-" + (++tokens); }
    }

    public static final class Codes implements OneTimeCodeRepository {
        public record Row(long id, Purpose purpose, UUID userId, String hash, Instant expiresAt, Instant createdAt,
                          int failed, Instant usedAt) {}
        public final List<Row> rows = new ArrayList<>();

        @Override public void issue(Purpose purpose, UUID userId, String codeHash, Instant expiresAt, Instant now) {
            rows.replaceAll(r -> r.purpose() == purpose && r.userId().equals(userId) && r.usedAt() == null && r.expiresAt().isAfter(now)
                    ? new Row(r.id(), r.purpose(), r.userId(), r.hash(), now, r.createdAt(), r.failed(), r.usedAt()) : r);
            rows.add(new Row(rows.size() + 1, purpose, userId, codeHash, expiresAt, now, 0, null));
        }
        @Override public Optional<StoredCode> findActive(Purpose purpose, UUID userId, Instant now) {
            return rows.stream()
                    .filter(r -> r.purpose() == purpose && r.userId().equals(userId) && r.usedAt() == null && r.expiresAt().isAfter(now))
                    .reduce((a, b) -> b)
                    .map(r -> new StoredCode(r.id(), r.hash(), r.expiresAt(), r.failed()));
        }
        @Override public Optional<Instant> lastIssuedAt(Purpose purpose, UUID userId) {
            return rows.stream().filter(r -> r.purpose() == purpose && r.userId().equals(userId)).reduce((a, b) -> b).map(Row::createdAt);
        }
        @Override public int registerFailedAttempt(Purpose purpose, long codeId) {
            Row r = rows.get((int) codeId - 1);
            rows.set((int) codeId - 1, new Row(r.id(), r.purpose(), r.userId(), r.hash(), r.expiresAt(), r.createdAt(), r.failed() + 1, r.usedAt()));
            return r.failed() + 1;
        }
        @Override public boolean consume(Purpose purpose, long codeId, Instant now) {
            Row r = rows.get((int) codeId - 1);
            if (r.usedAt() != null) return false;
            rows.set((int) codeId - 1, new Row(r.id(), r.purpose(), r.userId(), r.hash(), r.expiresAt(), r.createdAt(), r.failed(), now));
            return true;
        }
    }

    public static final class RefreshTokens implements RefreshTokenRepository {
        public record Row(long id, UUID userId, String hash, Instant expiresAt, Instant revokedAt) {}
        public final List<Row> rows = new ArrayList<>();

        @Override public void save(UUID userId, String tokenHash, String deviceInfo, Instant expiresAt, Instant createdAt) {
            rows.add(new Row(rows.size() + 1, userId, tokenHash, expiresAt, null));
        }
        @Override public Optional<StoredRefreshToken> findByHash(String tokenHash) {
            return rows.stream().filter(r -> r.hash().equals(tokenHash)).findFirst()
                    .map(r -> new StoredRefreshToken(r.id(), r.userId(), r.expiresAt(), r.revokedAt()));
        }
        @Override public boolean revokeIfActive(long id, Instant now) {
            Row r = rows.get((int) id - 1);
            if (r.revokedAt() != null) return false;
            rows.set((int) id - 1, new Row(r.id(), r.userId(), r.hash(), r.expiresAt(), now));
            return true;
        }
        @Override public int revokeAllForUser(UUID userId, Instant now) {
            int count = 0;
            for (Row r : List.copyOf(rows)) if (r.userId().equals(userId) && revokeIfActive(r.id(), now)) count++;
            return count;
        }
        public long active(UUID userId) {
            return rows.stream().filter(r -> r.userId().equals(userId) && r.revokedAt() == null).count();
        }
    }

    public static final class Attempts implements LoginAttemptRepository {
        public record Row(UUID userId, String email, boolean success, String reason, Instant at) {}
        public final List<Row> rows = new ArrayList<>();

        @Override public void record(UUID userId, String email, String ip, boolean success, String failureReason, Instant at) {
            rows.add(new Row(userId, email, success, failureReason, at));
        }
        @Override public long countFailuresSince(UUID userId, Instant since) {
            return rows.stream().filter(r -> Objects.equals(r.userId(), userId) && "WRONG_PASSWORD".equals(r.reason())
                    && r.at().isAfter(since)).count();
        }
        @Override public Optional<Instant> lastSuccessAt(UUID userId) {
            return rows.stream().filter(r -> Objects.equals(r.userId(), userId) && r.success()).map(Row::at).reduce((a, b) -> b);
        }
    }

    public static final class Activity implements ActivityLog {
        public record Entry(UUID userId, String action, Map<String, String> metadata) {}
        public final List<String> actions = new ArrayList<>();
        public final List<Entry> entries = new ArrayList<>();
        @Override public void record(UUID userId, String action, Map<String, String> metadata, String ip, Instant at) {
            actions.add(action);
            entries.add(new Entry(userId, action, metadata));
        }
    }

    public static final class Tokens implements AccessTokenIssuer {
        private int count;
        @Override public AccessToken issue(User user, Instant now) {
            count++;
            return new AccessToken("access-" + count, "jti-" + count, now.plus(Duration.ofHours(1)));
        }
    }

    public static final class Revocations implements TokenRevocationStore {
        public final List<String> revokedTokenIds = new ArrayList<>();
        public final Map<UUID, Instant> revokedBefore = new HashMap<>();
        @Override public void revokeToken(String tokenId, Instant tokenExpiresAt) { revokedTokenIds.add(tokenId); }
        @Override public void revokeAllIssuedBefore(UUID userId, Instant moment) { revokedBefore.put(userId, moment); }
    }

    public static final class Mailbox implements NotificationSender {
        public record Sent(String kind, String to, String code) {}
        public final List<Sent> sent = new ArrayList<>();
        @Override public void sendVerificationCode(Email to, String firstName, String code, Duration validFor) {
            sent.add(new Sent("VERIFY", to.value(), code));
        }
        @Override public void sendPasswordResetCode(Email to, String firstName, String code, Duration validFor) {
            sent.add(new Sent("RESET", to.value(), code));
        }
        @Override public void sendPasswordChanged(Email to, String firstName) {
            sent.add(new Sent("CHANGED", to.value(), null));
        }
        @Override public void sendActionCode(Email to, String firstName, String action, String code, Duration validFor) {
            sent.add(new Sent("ACTION", to.value(), code)); }

        public String lastCode() { return sent.get(sent.size() - 1).code(); }
    }

    public static final class Avatars implements AvatarStorage {
        public final List<String> stored = new ArrayList<>();
        public final List<String> deleted = new ArrayList<>();
        @Override public String store(UUID userId, byte[] content, String extension) {
            String url = "/api/avatars/" + (stored.size() + 1) + "." + extension;
            stored.add(url);
            return url;
        }
        @Override public void delete(String url) { deleted.add(url); }
    }

    public static final class Devices implements DeviceCleanup {
        public final List<UUID> cleaned = new ArrayList<>();
        public boolean down;
        @Override public void unlinkAllDevicesOf(UUID userId) {
            if (down) throw new com.sywater.ms_iam.domain.exception.ExternalServiceUnavailableException("device");
            cleaned.add(userId);
        }
    }
}
