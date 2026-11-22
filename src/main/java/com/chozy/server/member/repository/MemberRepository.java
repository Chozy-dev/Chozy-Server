package com.chozy.server.member.repository;

import com.chozy.server.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
  java.util.Optional<Member> findByLoginId(String loginId);
  boolean existsByLoginId(String loginId);
  boolean existsByEmail(String email);
}
