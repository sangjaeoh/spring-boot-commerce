package com.commerce.api.presentation.v1;

import com.commerce.api.facade.MemberWithdrawalFacade;
import com.commerce.api.presentation.v1.request.MemberRegistrationRequest;
import com.commerce.api.presentation.v1.request.MemberRenameRequest;
import com.commerce.api.presentation.v1.response.MemberRegistrationResponse;
import com.commerce.api.presentation.v1.response.MemberResponse;
import com.commerce.member.entity.SuspensionReason;
import com.commerce.member.entity.WithdrawalReason;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberModifier;
import com.commerce.member.service.MemberReader;
import com.commerce.web.auth.AdminOnly;
import com.commerce.web.auth.AuthUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 가입·조회·탈퇴·관리(정지·해제·이름 변경) 엔드포인트다.
 *
 * <p>가입은 공개, 본인 조회({@code /me})·이름 변경·탈퇴는 토큰 주체({@link AuthUser})에서 회원을 도출하는
 * 본인용 표면이다(미인증은 401). 회원 지정 조회·정지·해제는 대상 회원을 경로로 받는 관리자 표면이라
 * 관리자 토큰만 허용한다({@link AdminOnly}). 가입·조회는 회원 도메인 서비스에, 탈퇴는 탈퇴 파사드에 위임한다. 관리는 단일
 * 도메인 쓰기라 파사드 없이 회원 도메인 Modifier에 얇게 위임하고, 이메일 형식 오류·중복·미존재·탈퇴 거부·
 * 허용되지 않은 상태 전이는 도메인/파사드가 던지는 예외를 전역 핸들러가 problem+json으로 매핑한다. 정지와
 * 탈퇴는 독립 축이라 정지 회원도 탈퇴할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberAppender memberAppender;
    private final MemberReader memberReader;
    private final MemberModifier memberModifier;
    private final MemberWithdrawalFacade memberWithdrawalFacade;

    public MemberController(
            MemberAppender memberAppender,
            MemberReader memberReader,
            MemberModifier memberModifier,
            MemberWithdrawalFacade memberWithdrawalFacade) {
        this.memberAppender = memberAppender;
        this.memberReader = memberReader;
        this.memberModifier = memberModifier;
        this.memberWithdrawalFacade = memberWithdrawalFacade;
    }

    /** 회원을 가입시키고 가입된 회원 ID를 반환한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberRegistrationResponse register(@Valid @RequestBody MemberRegistrationRequest request) {
        UUID memberId = memberAppender.register(request.email(), request.name(), request.password());
        return MemberRegistrationResponse.from(memberId);
    }

    /** 본인 회원 상세를 계정 상태·정지 사유와 함께 조회한다. */
    @GetMapping("/me")
    public MemberResponse getMe(AuthUser authUser) {
        return MemberResponse.from(memberReader.getMember(authUser.memberId()));
    }

    /** 회원 상세를 계정 상태·정지 사유와 함께 조회한다. */
    @AdminOnly
    @GetMapping("/{memberId}")
    public MemberResponse getMember(@PathVariable UUID memberId) {
        return MemberResponse.from(memberReader.getMember(memberId));
    }

    /** 이메일 정확 일치로 활성 회원을 조회한다(대상 회원 ID 발견). 없으면 404다. */
    @AdminOnly
    @GetMapping(params = "email")
    public MemberResponse searchByEmail(@RequestParam String email) {
        return MemberResponse.from(memberReader.getMemberByEmail(email));
    }

    /** 회원을 정지하고 사유를 기록한다. */
    @AdminOnly
    @PostMapping("/{memberId}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suspend(@PathVariable UUID memberId, @RequestParam SuspensionReason reason) {
        memberModifier.suspend(memberId, reason);
    }

    /** 회원 정지를 해제하고 사유를 지운다. */
    @AdminOnly
    @PostMapping("/{memberId}/reinstate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reinstate(@PathVariable UUID memberId) {
        memberModifier.reinstate(memberId);
    }

    /** 본인 표시 이름을 바꾼다. 이메일은 불변이다. */
    @PatchMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rename(AuthUser authUser, @Valid @RequestBody MemberRenameRequest request) {
        memberModifier.rename(authUser.memberId(), request.name());
    }

    /** 본인을 탈퇴(논리삭제) 처리한다. */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(AuthUser authUser, @RequestParam WithdrawalReason reason) {
        memberWithdrawalFacade.withdraw(authUser.memberId(), reason);
    }
}
