# Features and tweaks

AppHub runs the whole loop from a prompt to a pull request with a green CI, on its own. The unit of work is a **feature** (a new pull request)
or a **tweak** (more changes to the pull request of a feature). Both are one `Feature` record (`domain/Feature.kt`), kept on the device by
`PrefsFeatureStore`, and both run as a **session**: one conversation with one LLM, started fresh, that never sees another.

## What the user does

- **Start new feature**: a button in each repo card on Home. It opens the Prompt page (`Route.Prompt(repo)`), where one prompt is typed.
- **Tweak**: a button on the page of a pull request that a feature made (`Route.Prompt(repo, number)`). The new tweak is listed on that page at once,
  with a summary and a status icon: a spinner while it runs, a tick when it is completed.
- Sending the prompt **closes the input for good**: `PromptUiState.sent`, and the page leaves. No page takes a second prompt.
  The one exception is `ask_user`: when the model is blocked, the session shows **Needs your answer** and the Feature page (`Route.Feature(id)`) takes the answer.
- A feature shows **Ready for review** and a tweak **Completed** when the session has ended on a green CI. **Stop** ends a session by hand.
- While a session works on a pull request, another tweak of it waits (`FeatureManager.isBusy`): two sessions would push to the same branch.

## What the session does

`FeatureRunner` (pure Kotlin) runs the loop, with these parts, each behind an interface so each can be replaced and tested:

| Part | Interface | Implementation |
|---|---|---|
| The model | `LlmClient` (`domain/Llm.kt`) | `AnthropicLlmClient`, `OpenAiCompatibleLlmClient` (DeepSeek) |
| Which model | `LlmSource`, `LlmSettings` | `LlmClientSource`, `PrefsLlmSettings`, keys in `LlmKeyStores` |
| GitHub | `CodeHost` | `KtorCodeHost` (Git data API: one commit per phase) |
| State | `FeatureStore` | `PrefsFeatureStore` |

1. **Work phase.** The model has the tools of `AgentTools`: `list_files`, `read_file`, `write_file`, `replace_in_file`, `delete_file`, `ask_user` and `submit`.
   Edits are staged in memory and visible to the model's own reads. Nothing under `.github/workflows` or `.git` can be written.
2. **Submit.** `submit` ends the phase. The runner makes one commit on the branch (`feature/<words>-<id>`, made from the default branch;
   or the branch of the pull request for a tweak) and, for a feature the first time, opens the pull request.
3. **CI wait, with no model.** The runner polls the workflow runs of the commit (`CodeHost.ciStatus`). Nothing is sent to the LLM while it waits.
   Green means every run of the commit is completed without failure, seen twice in a row (so a workflow that starts a moment late is not missed). A commit that has no run at all after three
   minutes counts as green (a change CI does not build).
4. **Red.** The report (failed job, step, compiler errors and the end of the log) goes to the model as a message, and it fixes the code and
   submits again: up to 5 fixes, then the session fails.
5. **Green.** The runner clears the conversation, sets the status, and returns. **That is the kill**: no session, coroutine or poll is left, and the model is never called again.

The first message of a tweak session is preceded by the hidden preamble (`AgentPrompts.tweak`):
"You are working on an existing PR for [App Name]: [PR link]. Review the user's requested adjustments below, make the necessary modifications to the
codebase, push the changes to this same PR branch, monitor the CI until it goes green, and then immediately archive/kill this session."
The user never sees it, and the user's prompt is the first message.

## Limits worth knowing

- Sessions run inside the app process, kept alive by a foreground service while any session is active (`FeatureKeepAlive`). If Android or the user
  still ends the process, the session is over: on the next start it is shown as Failed (`FeatureManager.failInterrupted`). Conversations are not saved.
- A session is bounded: 40 model turns per work phase, about 150,000 tokens of conversation, 45 minutes of CI.
- Settings → GitHub: the token needs Contents, Pull requests (read and write) and Actions (read) on the repo. Settings → AI model for features:
  the provider, model and API key. A key is encrypted with an Android Keystore key, like the GitHub token, and sent only to its provider.

## Adding another LLM provider

1. Add an entry to `LlmProvider` (label, default model, suggested models, how to get a key).
2. Write an `LlmClient` for it in `data/`, or reuse `OpenAiCompatibleLlmClient` if it speaks the OpenAI chat API.
3. Add its branch to `LlmClientSource`. Nothing else changes: Settings lists it by itself.
