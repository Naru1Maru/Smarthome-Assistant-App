plugins {
    // Empty on purpose; plugins applied in module.
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
