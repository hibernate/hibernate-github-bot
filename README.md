# Hibernate GitHub Bot

## Powered by

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, visit its website: https://quarkus.io/.

Specifically, most of the GitHub-related features in this bot are powered by
[the `quarkus-github-app` extension](https://github.com/quarkiverse/quarkus-github-app). 

## Features

This bot checks various contribution rules on pull requests submitted to Hibernate projects on GitHub,
and notifies the pull request authors of any change they need to work on.

This includes:

* Basic formatting of the pull request: at least two words in the title, ...
* Proper referencing of related JIRA tickets: the ticket key must be mentioned in the PR description.
* Proper formatting of commits: every commit message must start with the key of a JIRA ticket.
* Etc.

The bot can also be configured to automatically add links to JIRA issues in PR descriptions. When this is enabled
links to JIRA tickets will be appended at the bottom of the PR body.

## Configuration

### Enabling the bot in a new repository

You will need admin rights in the Hibernate organization.

Go to [the installed application settings](https://github.com/organizations/hibernate/settings/installations/15390286)
and add your repository under "Repository access".

If you wish to enable the JIRA-related features as well,
create the file `.github/hibernate-github-bot.yml` in default branch of your repository,
with the following content:

```yaml
---
jira:
  projectKey: "HSEARCH" # Change to whatever your project key is
  insertLinksInPullRequests: true # This is optional and enables automatically adding issue links to PR descriptions
  # To skip JIRA-related checks (pull request title/body includes JIRA keys/links etc.),
  # a list of ignore rules can be configured:
  ignore:
    - user: dependabot[bot]
      titlePattern: ".*\\bmaven\\b.*\\bplugin\\b.*" # will ignore build dependency upgrades i.e. maven plugin version upgrades.
    - user: all-contributors[bot]
      titlePattern: ".*"
    # Add even more here if you want to.
```

### Altering the infrastructure

This should only be needed very rarely, so think twice before trying this.

You will need admin rights in the Hibernate organization.

The infrastructure configuration can be found [here](https://github.com/hibernate/ci.hibernate.org).

The GitHub registration of this bot can be found [here](https://github.com/organizations/hibernate/settings/apps/hibernate-github-bot).

## Contributing

### Development and testing

Always test your changes locally before pushing them.

You can run the bot locally by:

1. Registering a test instance of the GitHub application on a fake, "playground" repository
   [as explained here](https://quarkiverse.github.io/quarkiverse-docs/quarkus-github-app/dev/register-github-app.html).
2. Adding an `.env` file at the root of the repository,
   [as explained here](https://quarkiverse.github.io/quarkiverse-docs/quarkus-github-app/dev/create-github-app.html#_initialize_the_configuration).   
3. Running `./mvnw quarkus:dev`.

### Deployment

Just push to the `main` branch.

The `main` branch is automatically deployed in production through
[this Jenkins job](https://ci.hibernate.org/job/hibernate-github-bot/),
configured through the `Jenkinsfile` at the root of this repository.

Container images are pushed to [this quay.io repository](https://quay.io/repository/hibernate/hibernate-github-bot).

### Maintenance

You can check the current health of the bot like this:

```shell
curl https://infra.hibernate.org/bot/github/q/health | jq
```
