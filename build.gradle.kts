plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.android.junit5) apply false
}

tasks.register("verify") {
    group = "verification"
    description = "Canonical project gate (mirrors CI): ktlint, detekt, all unit tests, and assemble."
    dependsOn(
        ":core:ktlintCheck",
        ":core:detekt",
        ":core:test",
        ":core:assemble",
        ":app:ktlintCheck",
        ":app:detekt",
        ":app:test",
        ":app:assemble",
    )
}
