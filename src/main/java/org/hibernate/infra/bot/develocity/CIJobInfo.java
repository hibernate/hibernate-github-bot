package org.hibernate.infra.bot.develocity;

import java.net.URL;
import java.util.Optional;

public record CIJobInfo(String name, Optional<URL> url) {
}
