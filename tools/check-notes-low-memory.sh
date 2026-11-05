#!/usr/bin/env bash
set -euo pipefail
cobalt_script_dir="$(dirname -- "${BASH_SOURCE[0]}")"
cd -- "${cobalt_script_dir}/.."
# No emulator, R8, APK packaging or persistent Gradle-property changes.
exec ./gradlew :app:testDebugUnitTest \
  --tests 'com.example.notes.*' \
  --tests com.example.ui.components.ContinuousMarkdownViewTest \
  --tests com.example.ui.components.DiskMarkdownViewTest \
  --tests com.example.ui.components.ViewportMarkdownSpansTest \
  --tests com.example.ui.screens.MarkdownReadSessionTest \
  --offline --no-daemon --max-workers=1 --no-parallel \
  --init-script tools/notes-test-limits.gradle \
  -Pkotlin.compiler.execution.strategy=in-process \
  '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=384m -XX:ActiveProcessorCount=1 -Dfile.encoding=UTF-8' \
  "$@"
