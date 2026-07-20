package com.itangcent.easyapi.core.ai.tools

/**
 * The standard set of tools handed to the [ToolRegistry] for a real
 * conversation.
 *
 * 12 perception tools + 1 staging action (`propose_rule_content`).
 * `write_rule_file` is intentionally NOT registered in v1 — the disk write
 * happens only through the user-confirmed "Save…" UI flow.
 *
 * Detection pairs:
 * - [FindClassesByAnnotationTool] — annotation-declared components
 * (`@RestController`, `@WebFilter`,...).
 * - [FindClassesBySupertypeTool] — inheritance-declared components
 * (filters extending `OncePerRequestFilter`, interceptors implementing
 * `HandlerInterceptor`,...). Without it the agent reports false negatives
 * like "no Filters" for the standard Spring Boot declaration style.
 * - [FindClassesByNameTool] — resolves class simple names to FQNs via
 * `PsiShortNamesCache`, with an FQN short-circuit and batch mode.
 */
fun standardRuleTools(): List<AiTool> = listOf(
    ListRuleKeysTool(),
    GetPluginDocTool(),
    ReadRuleFileTool(),
    ListProjectEndpointsTool(),
    GetPsiClassInfoTool(),
    GetPsiMethodInfoTool(),
    FindClassesByAnnotationTool(),
    FindClassesBySupertypeTool(),
    FindClassesByNameTool(),
    GetExistingRulesForKeyTool(),
    GetModuleDependencyGraphTool(),
    AskClarificationTool(),
    ProposeRuleContentTool()
    // WriteRuleFileTool() is reserved — not registered in v1.
)
