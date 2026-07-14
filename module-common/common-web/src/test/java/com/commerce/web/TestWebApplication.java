package com.commerce.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * common-web 웹 계층 하네스를 부팅하는 테스트 전용 앱이다. {@code com.commerce.web}를 스캔해 핸들러·
 * 필터·저장소와 테스트 컨트롤러를 실제 컨텍스트에 등록한다.
 */
@SpringBootApplication
public class TestWebApplication {}
