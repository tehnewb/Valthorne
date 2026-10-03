# Automatic development versions

Before merging a pull request into `main`, choose at most one label:

| Label | Result |
| --- | --- |
| `version:hotfix` | Increment the fourth number: `1.0.0.9` becomes `1.0.0.10`. |
| `version:patch` | Increment the third number and reset the fourth: `1.0.0.9` becomes `1.0.1.0`. |
| Neither | Record the merge without changing the version. |

The first two numbers never change automatically. Increase them explicitly in
`gradle.properties` when preparing a major or big feature release. An increase
to either number requests automatic Maven Central publication after merge.
Labels must be selected before
merging and remain unchanged until the workflow processes the PR. Both labels
together fail the run; remove the incorrect label and run the workflow again.

The workflow scans all merged PRs since the cutoff in `.github/version-state.json`
(October 3, 2026, 20:53:41 UTC). Earlier merges are deliberately excluded. Each
merge is recorded once, including unlabeled merges. Changing a label after a PR
is recorded does not retroactively change its version bump. The automation's own
setup PR should have neither version label.

Every push to `main` reconciles pending merges. An hourly schedule and the manual
**Run workflow** button recover missed or interrupted runs. Merges are processed
in merge-time order, with PR number breaking ties. Version and processing state
are committed together, and competing pushes are retried without force pushing.
Several pending PRs may be combined into one commit, with one bump per labeled PR.

Routine merges update `gradle.properties` and the processing ledger only.
For an urgent fix, add **`release:urgent`** alongside **one** version label before
merging. That additional label requests automatic Maven Central publication.
An urgent PR without a version label fails before any update. Ordinary hotfixes
are not automatically published.

Publication requests are written to `.github/release-request.json` in the same
commit as the version update. The publishing workflow checks that exact commit
belongs to `main`, validates the version and reason, runs the build and release
checks, signs the artifacts, and invokes `publishAndReleaseToMavenCentral`.
The App-created commit triggers this workflow; the default Actions token would
not trigger another push workflow.

Several pending merges can be included in a single publication of the resulting
version. It includes all changes present in that snapshot, not just the urgent
fix. The README's released dependency examples and GitHub release tags remain
manually maintained. Do not include a manual version bump in a labeled PR,
or the automatic bump will be applied on top of it.

## One-time GitHub App setup

The workflow is disabled until `VERSION_APP_CLIENT_ID` is configured. Repository
protection currently permits only the owner to write, so the default Actions
token cannot perform these updates.

1. Under your GitHub account's **Settings → Developer settings → GitHub Apps**,
   create a private GitHub App for this automation. Disable its webhook and grant
   repository **Contents: Read and write** and **Pull requests: Read-only**.
   Install it on **Valthorne only**. Do not grant administration or workflow write
   permissions.
2. Generate a private key. In Valthorne's **Settings → Secrets and variables →
   Actions**, store the complete key as secret `VERSION_APP_PRIVATE_KEY` and the
   App's Client ID as variable `VERSION_APP_CLIENT_ID`. Never commit the key.
3. Under **Settings → Rules → Rulesets**, change **Owner-only branch control** to
   exclude `refs/heads/main`. Create a separate active branch ruleset for `main`
   restricting creation, updates, and deletion. Give **Repository admin** and
   this App **Always** bypass in that new ruleset. All other branches and tags
   retain their existing owner-only restrictions.
4. Keep **Main history protection** unchanged: no bypass for deletion or force
   pushes, including for the App.
5. Merge these automation files, then manually run **Update development version**
   to verify the App can write. If it fails, check its installation, permissions,
   private key, and bypass selection. Pending PRs remain recoverable on rerun.

GitHub's bypass is a branch-level permission, not a file-specific permission.
The App credential therefore technically permits updates to all files on `main`;
this workflow limits its writes to the version, ledger, and release-request files. Protect the private key
and review changes to this workflow as carefully as changes to repository access.
No other collaborator receives merge or push permission through this setup.

## Maven Central credentials and retries

The publisher uses repository Actions secrets `MAVEN_CENTRAL_USERNAME`,
`MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY` (complete armored private key), and
`SIGNING_PASSWORD`. These have been configured from the private local Gradle
settings and installed signing key. The signing key's short ID is `5152FDD3`;
its fingerprint is `D9A2478E33B55EAA82363D9C2238610E5152FDD3`.
The corresponding public key must be available from a Central-supported keyserver.

Publication jobs never receive credentials until release verification succeeds.
They build from the verified SHA, never force-push, and do not have repository
write permissions. A version already visible in Maven Central is skipped.
Maven Central versions are immutable; do not reuse an existing version number.

For a failed run, inspect its failure first, then rerun that publishing workflow
or use **Publish automatic Maven release → Run workflow**, supplying the exact
release-request commit SHA. If an upload timed out after reaching Central, check
the Central Portal deployment before retrying: a 404 on the public repository
may reflect publication still in progress. Never change an already published
version's artifacts.

Nothing is uploaded by local verification. This setup does not publish the
current `1.0.0.0` automatically; the next eligible merge requests publication.

## Verification

Run `node --test .github/scripts/update-version.test.cjs .github/scripts/release-policy.test.cjs`. Tests cover component
increments and resets, merge ordering, duplicate processing, unlabeled and
ineligible PRs, invalid input, atomic commits, competing updates, automatic
publication eligibility, and exact release snapshot validation.
