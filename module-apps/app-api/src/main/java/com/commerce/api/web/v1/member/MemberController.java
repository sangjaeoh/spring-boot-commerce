package com.commerce.api.web.v1.member;

import com.commerce.api.facade.MemberWithdrawalFacade;
import com.commerce.api.web.v1.member.request.MemberRegistrationRequest;
import com.commerce.api.web.v1.member.request.MemberRenameRequest;
import com.commerce.api.web.v1.member.request.MemberSuspensionRequest;
import com.commerce.api.web.v1.member.request.MemberWithdrawalRequest;
import com.commerce.api.web.v1.member.response.MemberRegistrationResponse;
import com.commerce.api.web.v1.member.response.MemberResponse;
import com.commerce.member.service.MemberAppender;
import com.commerce.member.service.MemberModifier;
import com.commerce.member.service.MemberReader;
import com.commerce.web.auth.AdminOnly;
import com.commerce.web.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "회원", description = "회원 가입·조회·탈퇴·관리")
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
    @Operation(summary = "회원 가입", description = "회원을 가입시키고 가입된 회원 ID를 반환한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "가입됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "이메일 중복",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberRegistrationResponse register(@Valid @RequestBody MemberRegistrationRequest request) {
        UUID memberId = memberAppender.register(request.email(), request.name(), request.password());
        return MemberRegistrationResponse.from(memberId);
    }

    /** 본인 회원 상세를 계정 상태·정지 사유와 함께 조회한다. */
    @Operation(summary = "본인 조회", description = "토큰 주체의 회원 상세를 계정 상태·정지 사유와 함께 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping("/me")
    public MemberResponse getMe(AuthUser authUser) {
        return MemberResponse.from(memberReader.getMember(authUser.memberId()));
    }

    /** 회원 상세를 계정 상태·정지 사유와 함께 조회한다. */
    @Operation(summary = "회원 조회", description = "회원 ID로 회원 상세를 계정 상태·정지 사유와 함께 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @GetMapping("/{memberId}")
    public MemberResponse getMember(@PathVariable UUID memberId) {
        return MemberResponse.from(memberReader.getMember(memberId));
    }

    /** 이메일 정확 일치로 활성 회원을 조회한다(대상 회원 ID 발견). 없으면 404다. */
    @Operation(summary = "이메일로 회원 검색", description = "이메일 정확 일치로 활성 회원을 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @GetMapping(params = "email")
    public MemberResponse searchByEmail(@RequestParam String email) {
        return MemberResponse.from(memberReader.getMemberByEmail(email));
    }

    /** 회원을 정지하고 사유를 기록한다. */
    @Operation(summary = "회원 정지", description = "회원을 정지하고 사유를 기록한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "정지됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 상태 전이",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @PostMapping("/{memberId}/suspend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suspend(@PathVariable UUID memberId, @Valid @RequestBody MemberSuspensionRequest request) {
        memberModifier.suspend(memberId, request.reason());
    }

    /** 회원 정지를 해제하고 사유를 지운다. */
    @Operation(summary = "회원 정지 해제", description = "회원 정지를 해제하고 사유를 지운다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "해제됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "403",
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "허용되지 않은 상태 전이",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @AdminOnly
    @PostMapping("/{memberId}/reinstate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reinstate(@PathVariable UUID memberId) {
        memberModifier.reinstate(memberId);
    }

    /** 본인 표시 이름을 바꾼다. 이메일은 불변이다. */
    @Operation(summary = "본인 이름 변경", description = "본인 표시 이름을 바꾼다. 이메일은 불변이다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "변경됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PatchMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rename(AuthUser authUser, @Valid @RequestBody MemberRenameRequest request) {
        memberModifier.rename(authUser.memberId(), request.name());
    }

    /** 본인을 탈퇴(논리삭제) 처리한다. */
    @Operation(summary = "본인 탈퇴", description = "본인을 탈퇴(논리삭제) 처리한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "탈퇴됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "회원 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "미배송 결제 주문이 있어 탈퇴 불가",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(AuthUser authUser, @Valid @RequestBody MemberWithdrawalRequest request) {
        memberWithdrawalFacade.withdraw(authUser.memberId(), request.reason());
    }
}
