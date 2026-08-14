# Product Instructions

These rules supplement the repository-wide [instructions](../AGENTS.md). Every Product is independently buildable and may add a Product-local `AGENTS.md` when it gains rules not shared by sibling Products.

- Platform applications own UI, navigation, lifecycle, accessibility, theme, permissions, and presentation state.
- Shared Kotlin owns business rules and narrow platform-facing commands, snapshots, failures, and event streams; it never owns platform UI.
- Unselected platforms do not appear in the Product settings or Gradle Module graph.
- Product source does not reach into Workbench tooling internals; exported Products consume only their locked platform-kit and generated metadata.
- Preserve local-first behavior until a Product explicitly selects and implements backend capabilities.

From the repository root, run `./scripts/check.sh focused products` plus `./gradlew -p products/<product> <selected-platform-test-tasks>` and the Product's certification commands.
