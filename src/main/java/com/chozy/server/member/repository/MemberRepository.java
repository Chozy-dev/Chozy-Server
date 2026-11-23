package com.chozy.server.member.repository;

import com.chozy.server.member.entity.Member;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface MemberRepository extends JpaRepository<Member, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Member m where m.loginId = :loginId")
  Optional<Member> lockByLoginId(String loginId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Member m where m.id = :id")
  Optional<Member> lockById(long id);

  Optional<Member> findByLoginId(String loginId);
  boolean existsByIdAndCredentialVersionAndWithdrawnAtIsNull(long id, long credentialVersion);
  boolean existsByLoginId(String loginId);
  boolean existsByEmail(String email);
}
