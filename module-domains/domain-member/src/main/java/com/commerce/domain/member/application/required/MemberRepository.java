package com.commerce.domain.member.application.required;

import com.commerce.domain.member.domain.Email;
import com.commerce.domain.member.domain.Member;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByIdAndDeletedAtIsNull(UUID id);

    List<Member> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    Optional<Member> findByEmailAndDeletedAtIsNull(Email email);

    boolean existsByEmailAndDeletedAtIsNull(Email email);
}
