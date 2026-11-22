package com.chozy.server.auth.repository;

import com.chozy.server.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t where t.tokenHash = :hash")
  Optional<RefreshToken> lockByHash(@Param("hash") String hash);

  // Take the member lock before updating refresh tokens, consistent with rotation.
  @Modifying
  @Query("update RefreshToken t set t.revokedAt = CURRENT_TIMESTAMP where t.memberId = :id and t.revokedAt is null")
  int revokeAllByMemberId(@Param("id") long memberId);
}
