package org.hibernate.infra.bot.event;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

import io.quarkiverse.githubapp.event.Actions;
import io.quarkiverse.githubapp.event.CheckSuite;

/**
 * Workaround for https://github.com/quarkiverse/quarkus-github-app/pull/94
 */
@CheckSuite(CheckSuiteRequested.NAME)
@Target(PARAMETER)
@Retention(RUNTIME)
@Qualifier
public @interface CheckSuiteRequested {

	String NAME = "requested";

}
