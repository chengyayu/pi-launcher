.PHONY: build clean test verify sign publish install run-ide help

PLUGIN_NAME := pi-launcher
VERSION := $(shell grep '^version' build.gradle.kts | head -1 | sed 's/.*"\(.*\)"/\1/')
JAR := build/libs/$(PLUGIN_NAME)-$(VERSION).jar
ZIP := build/distributions/$(PLUGIN_NAME)-$(VERSION).zip

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-15s\033[0m %s\n", $$1, $$2}'

build: ## Build plugin jar
	./gradlew buildPlugin

clean: ## Clean build artifacts
	./gradlew clean

test: ## Run tests
	./gradlew test

verify: ## Run plugin verifier (compatibility check)
	./gradlew verifyPlugin

sign: ## Sign plugin (requires CERTIFICATE_CHAIN, PRIVATE_KEY, PRIVATE_KEY_PASSWORD env vars)
	./gradlew signPlugin

publish: ## Publish to JetBrains Marketplace (requires PUBLISH_TOKEN env var)
	./gradlew publishPlugin

install: build ## Build and show install path
	@echo ""
	@echo "Plugin zip: build/distributions/$(PLUGIN_NAME)-$(VERSION).zip"
	@echo "Plugin jar: $(JAR)"
	@echo ""
	@echo "Install: IDE → Settings → Plugins → ⚙️ → Install Plugin from Disk → select zip or jar"
	@echo "Upload: https://plugins.jetbrains.com → Upload Update → select zip"

run-ide: ## Launch a sandbox IDE with the plugin loaded
	./gradlew runIde

bump-patch: ## Bump patch version (0.1.3 → 0.1.4)
	@CURRENT=$(VERSION); \
	MAJOR=$$(echo $$CURRENT | cut -d. -f1); \
	MINOR=$$(echo $$CURRENT | cut -d. -f2); \
	PATCH=$$(echo $$CURRENT | cut -d. -f3); \
	NEW="$$MAJOR.$$MINOR.$$((PATCH + 1))"; \
	sed -i '' "s/version = \"$$CURRENT\"/version = \"$$NEW\"/" build.gradle.kts; \
	echo "\033[32m✓ Bumped $$CURRENT → $$NEW\033[0m"

bump-minor: ## Bump minor version (0.1.3 → 0.2.0)
	@CURRENT=$(VERSION); \
	MAJOR=$$(echo $$CURRENT | cut -d. -f1); \
	MINOR=$$(echo $$CURRENT | cut -d. -f2); \
	NEW="$$MAJOR.$$((MINOR + 1)).0"; \
	sed -i '' "s/version = \"$$CURRENT\"/version = \"$$NEW\"/" build.gradle.kts; \
	echo "\033[32m✓ Bumped $$CURRENT → $$NEW\033[0m"

bump-major: ## Bump major version (0.1.3 → 1.0.0)
	@CURRENT=$(VERSION); \
	MAJOR=$$(echo $$CURRENT | cut -d. -f1); \
	NEW="$$((MAJOR + 1)).0.0"; \
	sed -i '' "s/version = \"$$CURRENT\"/version = \"$$NEW\"/" build.gradle.kts; \
	echo "\033[32m✓ Bumped $$CURRENT → $$NEW\033[0m"

release: build ## Create GitHub release and publish to JetBrains Marketplace
	@if gh release view v$(VERSION) >/dev/null 2>&1; then \
		echo "\033[33m⚠ v$(VERSION) already released. Bump version in build.gradle.kts first.\033[0m"; \
		exit 1; \
	fi
	@echo "Generating change notes..."
	@PREV_TAG=$$(git describe --tags --abbrev=0 2>/dev/null || echo ""); \
	if [ -z "$$PREV_TAG" ]; then \
		NOTES=$$(git log --pretty=format:"<li>%s</li>" | head -20); \
	else \
		NOTES=$$(git log $$PREV_TAG..HEAD --pretty=format:"<li>%s</li>"); \
	fi; \
	sed -i '' '/<change-notes>/,/<\/change-notes>/c\
    <change-notes><![CDATA[\
    <h3>'$(VERSION)'</h3>\
    <ul>\
    '"$$NOTES"'\
    </ul>\
    ]]></change-notes>' src/main/resources/META-INF/plugin.xml
	@echo "Rebuilding with updated change notes..."
	./gradlew buildPlugin
	@echo "Releasing v$(VERSION)..."
	git tag -f v$(VERSION)
	git push origin v$(VERSION) --force
	gh release create v$(VERSION) \
		$(ZIP) \
		$(JAR) \
		--title "v$(VERSION)" \
		--generate-notes
	@echo ""
	@echo "✓ GitHub Release: https://github.com/chengyayu/pi-launcher/releases/tag/v$(VERSION)"
	@echo "Publishing to JetBrains Marketplace..."
	./gradlew publishPlugin
	@echo "✓ Published to JetBrains Marketplace"
