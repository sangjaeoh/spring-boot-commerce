package com.commerce.api.web.v1.member;

import com.commerce.api.web.auth.Authenticated;
import com.commerce.api.web.v1.member.request.MemberAddressRequest;
import com.commerce.api.web.v1.member.response.MemberAddressCreationResponse;
import com.commerce.api.web.v1.member.response.MemberAddressListResponse;
import com.commerce.member.application.MemberAddressAppender;
import com.commerce.member.application.MemberAddressModifier;
import com.commerce.member.application.MemberAddressReader;
import com.commerce.member.application.MemberAddressRemover;
import com.commerce.web.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 회원 배송지 등록·목록 조회·수정·삭제·기본 지정 엔드포인트다. */
@Tag(name = "회원 배송지", description = "배송지 등록·목록 조회·수정·삭제·기본 지정")
@Authenticated
@RestController
@RequestMapping("/api/v1/members/me/addresses")
public class MemberAddressController {

    private final MemberAddressAppender memberAddressAppender;
    private final MemberAddressReader memberAddressReader;
    private final MemberAddressModifier memberAddressModifier;
    private final MemberAddressRemover memberAddressRemover;

    public MemberAddressController(
            MemberAddressAppender memberAddressAppender,
            MemberAddressReader memberAddressReader,
            MemberAddressModifier memberAddressModifier,
            MemberAddressRemover memberAddressRemover) {
        this.memberAddressAppender = memberAddressAppender;
        this.memberAddressReader = memberAddressReader;
        this.memberAddressModifier = memberAddressModifier;
        this.memberAddressRemover = memberAddressRemover;
    }

    @Operation(summary = "배송지 등록", description = "본인 배송지를 등록하고 배송지 ID를 반환한다. 첫 배송지는 자동으로 기본 배송지가 된다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "등록됨"),
        @ApiResponse(
                responseCode = "400",
                description = "요청 값 무효",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "회원당 등록 한도 초과",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberAddressCreationResponse add(AuthUser authUser, @Valid @RequestBody MemberAddressRequest request) {
        UUID addressId = memberAddressAppender.add(
                authUser.memberId(),
                request.recipientName(),
                request.zipCode(),
                request.roadAddress(),
                request.detailAddress(),
                request.phone());
        return MemberAddressCreationResponse.from(addressId);
    }

    @Operation(summary = "배송지 목록 조회", description = "본인 배송지 목록을 기본 우선·최신 등록순으로 조회한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @GetMapping
    public MemberAddressListResponse getAddresses(AuthUser authUser) {
        return MemberAddressListResponse.from(memberAddressReader.getAddresses(authUser.memberId()));
    }

    @Operation(summary = "배송지 수정", description = "본인 배송지의 정보를 수정한다. 기본 여부는 바꾸지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "수정됨"),
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
                description = "배송지 없음 또는 타인 배송지",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PatchMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revise(
            AuthUser authUser,
            @Parameter(description = "배송지 ID") @PathVariable UUID addressId,
            @Valid @RequestBody MemberAddressRequest request) {
        memberAddressModifier.revise(
                addressId,
                authUser.memberId(),
                request.recipientName(),
                request.zipCode(),
                request.roadAddress(),
                request.detailAddress(),
                request.phone());
    }

    @Operation(summary = "배송지 삭제", description = "본인 배송지를 삭제한다. 기본 배송지 삭제 시 다른 배송지를 자동 승격하지 않는다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "배송지 없음 또는 타인 배송지",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(AuthUser authUser, @Parameter(description = "배송지 ID") @PathVariable UUID addressId) {
        memberAddressRemover.purge(addressId, authUser.memberId());
    }

    @Operation(summary = "기본 배송지 지정", description = "본인 배송지를 기본 배송지로 지정한다. 기존 기본 지정은 해제돼 기본은 항상 하나다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "지정됨"),
        @ApiResponse(
                responseCode = "401",
                description = "미인증",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "배송지 없음 또는 타인 배송지",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping("/{addressId}/default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void designateDefault(AuthUser authUser, @Parameter(description = "배송지 ID") @PathVariable UUID addressId) {
        memberAddressModifier.designateDefault(addressId, authUser.memberId());
    }
}
