# Continuous integration

The `CI` GitHub Actions workflow compiles and tests every service after each
push and for every pull request targeting `main`.

The four services run independently and in parallel. The final `CI quality
gate` check succeeds only when all of them have completed successfully. When a
build fails, its Gradle test reports are attached to the workflow run for seven
days.

## Protect `main`

The workflow reports the result, but a GitHub repository rule must make that
result mandatory before a merge. After this workflow has run on GitHub at least
once:

1. Open **Settings > Rules > Rulesets** in the GitHub repository.
2. Create a branch ruleset in **Active** enforcement mode targeting the default
   branch (`main`).
3. Enable **Require a pull request before merging**.
4. Enable **Require status checks to pass** and add `CI quality gate` as a
   required check, using GitHub Actions as its source.
5. Enable **Require branches to be up to date before merging** if every pull
   request must also be tested against the latest `main`.
6. Do not add administrators or repository roles to the bypass list if the rule
   must apply to everyone.

With that ruleset active, a failed or pending `CI quality gate` prevents the
pull request from being merged into `main`. Direct commits should be made on a
feature branch and merged through a pull request.

## Run the same checks locally

On Linux or macOS, run this command once from each service directory:

```shell
./gradlew clean build
```

On Windows, run:

```powershell
.\gradlew.bat clean build
```
