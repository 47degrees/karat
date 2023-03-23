plugins {
  scala
}

dependencies {
  implementation(projects.karatCommon)
  implementation(libs.scalacheck.core)
  implementation(libs.cats.core)
  testImplementation(libs.cats.effect)
  testImplementation(libs.scalacheck.effect)
  testImplementation(libs.munit.core)
  testImplementation(libs.munit.scalacheck)
  testImplementation(libs.scalacheck.effectMunit)
  testRuntimeOnly(libs.junit.jupiter)
}
