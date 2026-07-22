package com.commerce.api.web.v1.admin.category;

import com.commerce.api.web.auth.Admin;
import com.commerce.api.web.v1.admin.category.request.CategoryCreationRequest;
import com.commerce.api.web.v1.admin.category.request.CategoryRenameRequest;
import com.commerce.api.web.v1.admin.category.response.CategoryCreationResponse;
import com.commerce.api.web.v1.admin.category.response.CategoryListResponse;
import com.commerce.product.service.CategoryAppender;
import com.commerce.product.service.CategoryModifier;
import com.commerce.product.service.CategoryReader;
import com.commerce.product.service.CategoryRemover;
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

/** 카테고리 생성·목록·이름 변경·삭제의 관리자 엔드포인트다. */
@Tag(name = "카테고리 관리", description = "카테고리 생성·목록·이름 변경·삭제")
@Admin
@RestController
@RequestMapping("/api/v1/admin/categories")
public class CategoryAdminController {

    private final CategoryAppender categoryAppender;
    private final CategoryReader categoryReader;
    private final CategoryModifier categoryModifier;
    private final CategoryRemover categoryRemover;

    public CategoryAdminController(
            CategoryAppender categoryAppender,
            CategoryReader categoryReader,
            CategoryModifier categoryModifier,
            CategoryRemover categoryRemover) {
        this.categoryAppender = categoryAppender;
        this.categoryReader = categoryReader;
        this.categoryModifier = categoryModifier;
        this.categoryRemover = categoryRemover;
    }

    @Operation(summary = "카테고리 생성", description = "카테고리를 생성하고 생성된 카테고리 ID를 반환한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "생성됨"),
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
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryCreationResponse create(@Valid @RequestBody CategoryCreationRequest request) {
        return CategoryCreationResponse.from(categoryAppender.create(request.name()));
    }

    @Operation(summary = "카테고리 목록 조회", description = "활성 카테고리 목록을 이름순으로 조회한다.")
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
    })
    @GetMapping
    public CategoryListResponse getCategories() {
        return CategoryListResponse.from(categoryReader.getCategories());
    }

    @Operation(summary = "카테고리 이름 변경", description = "카테고리 이름을 바꾼다.")
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
                responseCode = "403",
                description = "권한 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "카테고리 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PatchMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rename(
            @Parameter(description = "카테고리 ID") @PathVariable UUID categoryId,
            @Valid @RequestBody CategoryRenameRequest request) {
        categoryModifier.rename(categoryId, request.name());
    }

    @Operation(summary = "카테고리 삭제", description = "카테고리를 논리삭제한다. 소속 상품은 재지정 전까지 기존 분류 ID를 유지한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제됨"),
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
                description = "카테고리 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(description = "카테고리 ID") @PathVariable UUID categoryId) {
        categoryRemover.delete(categoryId);
    }
}
