plugins {
    id("convention.event-module")
}

dependencies {
    // DomainEvent가 이벤트 record의 상위 계약으로 소비자에게 재노출된다.
    api(project(":module-common:common-event"))
}
