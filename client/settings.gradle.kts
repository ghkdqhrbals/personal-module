rootProject.name = "client"

// oauth 모듈을 composite build로 포함하여 client 단독 빌드 지원
includeBuild("../oauth")
