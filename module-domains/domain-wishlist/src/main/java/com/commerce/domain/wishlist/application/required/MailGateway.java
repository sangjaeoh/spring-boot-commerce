package com.commerce.domain.wishlist.application.required;

/** 위시리스트 메일 발송 포트다. 제목·본문 조립은 구현이 소유한다. */
public interface MailGateway {

    /** 찜한 상품의 재입고 알림 메일을 보낸다. */
    void sendRestockMail(String to, String productName);
}
