package com.commerce.admin.web.v1.admin.product;

import com.commerce.admin.web.auth.Admin;
import com.commerce.admin.web.v1.admin.product.response.ProductImageUploadResponse;
import com.commerce.product.service.ProductImageAppender;
import com.commerce.product.service.ProductImageRemover;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 상품 이미지 업로드·삭제의 관리자 엔드포인트다. */
@Tag(name = "상품 이미지 관리", description = "상품 이미지 업로드·삭제")
@Admin
@RestController
@RequestMapping("/api/v1/admin/products")
public class ProductImageAdminController {

    private final ProductImageAppender productImageAppender;
    private final ProductImageRemover productImageRemover;

    public ProductImageAdminController(
            ProductImageAppender productImageAppender, ProductImageRemover productImageRemover) {
        this.productImageAppender = productImageAppender;
        this.productImageRemover = productImageRemover;
    }

    @Operation(summary = "상품 이미지 업로드", description = "이미지를 보관하고 업로드된 이미지 ID를 반환한다. 정렬 순서는 업로드 순이고 첫 장이 대표다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "업로드됨"),
        @ApiResponse(
                responseCode = "400",
                description = "지원하지 않는 형식 또는 5MB 초과",
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
                description = "상품 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @PostMapping(path = "/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProductImageUploadResponse upload(
            @Parameter(description = "상품 ID") @PathVariable UUID productId,
            @Parameter(description = "이미지 파일(jpeg·png·webp, 5MB 이하)") @RequestPart("image") MultipartFile image) {
        UUID imageId = productImageAppender.append(
                productId, Objects.requireNonNullElse(image.getContentType(), ""), bytes(image));
        return ProductImageUploadResponse.from(imageId);
    }

    @Operation(summary = "상품 이미지 삭제", description = "이미지를 노출과 스토리지에서 제거한다.")
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
                description = "이미지 없음",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    })
    @DeleteMapping("/{productId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "상품 ID") @PathVariable UUID productId,
            @Parameter(description = "이미지 ID") @PathVariable UUID imageId) {
        productImageRemover.purge(productId, imageId);
    }

    /** 멀티파트 파일의 바이트를 읽는다. */
    private static byte[] bytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
