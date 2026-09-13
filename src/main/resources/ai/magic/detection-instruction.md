I'm starting a new rule file '{{name}}' (currently empty). Detect any custom framework patterns in this project that lack a rule, then propose initial rule content for them.

Standard HTTP frameworks (Spring MVC, WebFlux, JAX-RS, Feign) need no rules. The task list seeded below covers Custom-Pattern and Workflow-Pattern Catalog detections (Filter/HandlerInterceptor/WebFilter, ResponseBodyAdvice, HandlerMethodArgumentResolver, custom annotations, auth-token chaining, static auth, correlation/idempotency headers, HMAC signing) — one task per detection family.

A task list has been seeded for you with one task per detection pattern. Do NOT call create_task_list — the task list is already in working memory. You are the ORCHESTRATOR: you have three tools (run_sub_agent, update_task, propose_rule_content) and NO perception tools.

Seeded tasks — use these EXACT ids verbatim as the taskId argument to run_sub_agent and update_task. Do NOT invent, abbreviate, reformat, or guess ids; a wrong id is rejected with "unknown task id".

{{manifest}}

For each PENDING task:

1. Call run_sub_agent(taskId=...) to spawn a sub-agent for that task. The sub-agent perceives the project's PSI in isolation, decides whether the pattern is present, and reports back via report_findings. run_sub_agent returns the sub-agent's findings (detected, findings, proposedRules) as text — the evidence plus the rules it drafted. It ALSO automatically records the task status (completed if the sub-agent ran successfully — whether or not it detected the pattern; failed on error) and ticks the checklist card in the UI, so you do NOT need to call update_task afterwards for the status. "Nothing detected" is a valid finding, NOT a skip.
2. After EVERY task is closed (run_sub_agent returned for all of them), call propose_rule_content ONCE with a suggestedFileName. You do NOT need to compose the merged content yourself — the tool merges the collected sub-agent findings and rules, keeping each detection's findings as comments (lines starting with `#`) above the rules they produced. If no rules were drafted, the tool stages no proposal and the turn ends without prompting the user.

Do NOT call propose_rule_content until all tasks are closed. Do NOT call perception tools (find_classes_by_annotation, get_detection_prompt, get_psi_class_info, etc.) — they are not in your tool set. Each detection runs inside its own sub-agent with a fresh memory; the results are merged for you.
