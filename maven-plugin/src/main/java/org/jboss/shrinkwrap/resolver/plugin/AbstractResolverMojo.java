package org.jboss.shrinkwrap.resolver.plugin;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractResolverMojo implements Mojo {

    /**
     * The current build session instance.
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    private Log log;

    @Override
    public void setLog(Log log) {
        this.log = log;
    }

    @Override
    public Log getLog() {
        if (log == null) {
            log = new SystemStreamLog();
        }
        return log;
    }

}
