// :api — the dependency floor. Contracts only: ports, records/DTOs, enums, Result/AppError/ErrorCode.
//
// It declares NO dependencies, internal or third-party. That emptiness is the point: every other module
// points here, so a framework added at this level propagates to all eight. Enforced by the
// `api-is-framework-free` ArchUnit rule.

plugins {
    id("bookloom.java-conventions")
    id("bookloom.spotless-conventions")
    id("bookloom.test-conventions")
    id("bookloom.coverage-conventions")
}
