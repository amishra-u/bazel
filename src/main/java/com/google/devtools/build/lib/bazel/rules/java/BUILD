load("@rules_java//java:defs.bzl", "java_library")
load("//:distdir_deps.bzl", "gen_workspace_stanza")

package(
    default_applicable_licenses = ["//:license"],
    default_visibility = ["//src:__subpackages__"],
)

filegroup(
    name = "srcs",
    srcs = glob(["*"]) + [
        "//src/main/java/com/google/devtools/build/lib/bazel/rules/java/proto:srcs",
    ],
    visibility = ["//src:__subpackages__"],
)

exports_files(["java_stub_template.txt"])

java_library(
    name = "java",
    srcs = glob(
        ["*.java"],
        exclude = ["BazelJavaSemantics.java"],
    ),
    resources = glob(
        [
            "*.txt",
        ],
    ) + [
        ":jdk.WORKSPACE",
        ":rules_java_builtin.WORKSPACE",
    ],
    deps = [
        ":bazel_java_semantics",
        "//src/main/java/com/google/devtools/build/lib/analysis:analysis_cluster",
        "//src/main/java/com/google/devtools/build/lib/analysis:build_info",
        "//src/main/java/com/google/devtools/build/lib/analysis:config/execution_transition_factory",
        "//src/main/java/com/google/devtools/build/lib/analysis:rule_definition_environment",
        "//src/main/java/com/google/devtools/build/lib/bazel/rules/cpp",
        "//src/main/java/com/google/devtools/build/lib/packages",
        "//src/main/java/com/google/devtools/build/lib/rules/cpp",
        "//src/main/java/com/google/devtools/build/lib/rules/java:java-compilation",
        "//src/main/java/com/google/devtools/build/lib/rules/java:java-rules",
        "//src/main/java/com/google/devtools/build/lib/skyframe/serialization/autocodec",
        "//src/main/java/com/google/devtools/build/lib/skyframe/serialization/autocodec:serialization-constant",
        "//src/main/java/com/google/devtools/build/lib/util:filetype",
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

java_library(
    name = "bazel_java_semantics",
    srcs = ["BazelJavaSemantics.java"],
    deps = [
        "//src/main/java/com/google/devtools/build/lib/actions:artifacts",
        "//src/main/java/com/google/devtools/build/lib/analysis:actions/custom_command_line",
        "//src/main/java/com/google/devtools/build/lib/analysis:actions/launcher_file_write_action",
        "//src/main/java/com/google/devtools/build/lib/analysis:actions/lazy_write_paths_file_action",
        "//src/main/java/com/google/devtools/build/lib/analysis:actions/substitution",
        "//src/main/java/com/google/devtools/build/lib/analysis:actions/template",
        "//src/main/java/com/google/devtools/build/lib/analysis:actions/template_expansion_action",
        "//src/main/java/com/google/devtools/build/lib/analysis:analysis_cluster",
        "//src/main/java/com/google/devtools/build/lib/analysis:test/test_configuration",
        "//src/main/java/com/google/devtools/build/lib/analysis:transitive_info_collection",
        "//src/main/java/com/google/devtools/build/lib/cmdline",
        "//src/main/java/com/google/devtools/build/lib/collect/nestedset",
        "//src/main/java/com/google/devtools/build/lib/packages",
        "//src/main/java/com/google/devtools/build/lib/rules/cpp",
        "//src/main/java/com/google/devtools/build/lib/rules/java:java-compilation",
        "//src/main/java/com/google/devtools/build/lib/shell",
        "//src/main/java/com/google/devtools/build/lib/skyframe/serialization/autocodec:serialization-constant",
        "//src/main/java/com/google/devtools/build/lib/util",
        "//src/main/java/com/google/devtools/build/lib/util:os",
        "//src/main/java/com/google/devtools/build/lib/util:shell_escaper",
        "//src/main/java/com/google/devtools/build/lib/vfs",
        "//src/main/java/com/google/devtools/build/lib/vfs:pathfragment",
        "//third_party:guava",
        "//third_party:jsr305",
    ],
)

gen_workspace_stanza(
    name = "workspace_with_java_toolchains",
    out = "jdk.WORKSPACE",
    repo_clause = """
    name = "{repo}",
    sha256 = "{sha256}",
    strip_prefix = {strip_prefix},
    urls = {urls},
    """,
    repos = [
        "rules_cc",
        "rules_java",
        "rules_proto",
        "rules_python",
    ],
    template = "jdk.WORKSPACE.tmpl",
    visibility = ["//:__pkg__"],
)

gen_workspace_stanza(
    name = "rules_java_builtin",
    out = "rules_java_builtin.WORKSPACE",
    # The WORKSPACE_PREFIX_* comments are used in
    # ConfiguredRuleClassProvider#clearWorkspaceFileSuffixForTesting to remove rules_java_builtin
    # definition for tests.
    postamble = """
        # WORKSPACE_PREFIX_END (rules_java_builtin)
    """,
    preamble = """
        # WORKSPACE_PREFIX_BEGIN (rules_java_builtin)
        load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
    """,
    repo_clause = """
http_archive(
    name = "{repo}",
    sha256 = "{sha256}",
    strip_prefix = {strip_prefix},
    urls = {urls},
    repo_mapping = {{"@rules_java" : "@rules_java_builtin"}},
)
    """,
    repos = ["rules_java_builtin"],
    visibility = ["//:__pkg__"],
)
