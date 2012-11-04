/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.shrinkwrap.resolver.api.maven;

import org.jboss.shrinkwrap.resolver.api.ResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

/**
 * Encapsulation of a resolved Maven-based artifact's metadata
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 * @author <a href="mailto:kpiwko@redhat.com">Karel Piwko</a>
 */
public interface MavenResolvedArtifact extends ResolvedArtifact<MavenResolvedArtifact> {

    /**
     * Returns the defined coordinate (i.e. address) of this resolved artifact.
     *
     * @return
     */
    MavenCoordinate getCoordinate();

    /**
     * Returns the resolved "version" portion of this artifact's coordinates; SNAPSHOTs may declare a version field (as
     * represented by {@link VersionedMavenCoordinate#getVersion()}, which must resolve to a versioned snapshot version
     * number. That resolved version number is reflected by this field. In the case of true versions (ie.
     * non-SNAPSHOTs), this call will be equal to {@link VersionedMavenCoordinate#getVersion()}.
     *
     * @return
     */
    String getResolvedVersion();

    /**
     * Returns whether or not this artifact is using a SNAPSHOT version.
     *
     * @return
     */
    boolean isSnapshotVersion();

    /**
     * Returns the file extension of this artifact, ie. ("jar")
     *
     * @return The file extension, which is never null
     */
    String getExtension();
}
