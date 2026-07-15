package com.commerce.api.presentation.v1;

import com.commerce.api.facade.MemberWithdrawalFacade;
import com.commerce.api.presentation.v1.request.MemberRegistrationRequest;
import com.commerce.api.presentation.v1.response.MemberRegistrationResponse;
import com.commerce.api.presentation.v1.response.MemberResponse;
import com.commerce.member.entity.WithdrawalReason;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberReader;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 가입·조회·탈퇴 엔드포인트다.
 *
 * <p>가입·조회는 회원 도메인 서비스에, 탈퇴는 탈퇴 파사드에 위임한다. 이메일 형식 오류·중복·미존재·탈퇴 거부는
 * 도메인/파사드가 던지는 예외를 전역 핸들러가 problem+json으로 매핑한다. 인증이 범위 밖이라 회원 소유권은 검사하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberAppender memberAppender;
    private final MemberReader memberReader;
    private final MemberWithdrawalFacade memberWithdrawalFacade;

    public MemberController(
            MemberAppender memberAppender, MemberReader memberReader, MemberWithdrawalFacade memberWithdrawalFacade) {
        this.memberAppender = memberAppender;
        this.memberReader = memberReader;
        this.memberWithdrawalFacade = memberWithdrawalFacade;
    }

    /** 회원을 가입시키고 가입된 회원 ID를 반환한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberRegistrationResponse register(@Valid @RequestBody MemberRegistrationRequest request) {
        UUID memberId = memberAppender.register(request.email(), request.name());
        return MemberRegistrationResponse.from(memberId);
    }

    /** 회원 상세를 계정 상태·정지 사유와 함께 조회한다. */
    @GetMapping("/{memberId}")
    public MemberResponse getMember(@PathVariable UUID memberId) {
        return MemberResponse.from(memberReader.getMember(memberId));
    }

    /** 회원을 탈퇴(논리삭제) 처리한다. */
    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable UUID memberId, @RequestParam WithdrawalReason reason) {
        memberWithdrawalFacade.withdraw(memberId, reason);
    }
}
