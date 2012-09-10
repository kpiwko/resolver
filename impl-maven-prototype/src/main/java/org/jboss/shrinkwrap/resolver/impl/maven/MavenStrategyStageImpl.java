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
package org.jboss.shrinkwrap.resolver.impl.maven;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.shrinkwrap.resolver.api.NoResolvedResultException;
import org.jboss.shrinkwrap.resolver.api.maven.MavenFormatStage;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolutionFilter;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolutionStrategy;
import org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStage;
import org.jboss.shrinkwrap.resolver.api.maven.PackagingType;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinates;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependencies;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependency;
import org.jboss.shrinkwrap.resolver.impl.maven.convert.MavenConverter;
import org.jboss.shrinkwrap.resolver.impl.maven.filter.AcceptAllFilter;
import org.jboss.shrinkwrap.resolver.impl.maven.filter.MavenResolutionFilterInternalView;
import org.jboss.shrinkwrap.resolver.impl.maven.strategy.NonTransitiveStrategy;
import org.jboss.shrinkwrap.resolver.impl.maven.strategy.TransitiveStrategy;
import org.jboss.shrinkwrap.resolver.impl.maven.util.Validate;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.collection.CollectResult;
import org.sonatype.aether.collection.DependencyCollectionException;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.resolution.DependencyResult;
import org.sonatype.aether.transfer.ArtifactNotFoundException;

/**
 * Implementation of {@link MavenStrategyStage}
 *
 * @author <a href="mailto:kpiwko@redhat.com">Karel Piwko</a>
 */
public class MavenStrategyStageImpl implements MavenStrategyStage, MavenWorkingSessionContainer {

    private static final Logger log = Logger.getLogger(MavenStrategyStageImpl.class.getName());

    private final MavenWorkingSession session;

    public MavenStrategyStageImpl(final MavenWorkingSession session) {
        this.session = session;
    }

    @Override
    public MavenFormatStage withTransitivity() {
        return using(new TransitiveStrategy());
    }

    @Override
    public MavenFormatStage withoutTransitivity() {
        return using(new NonTransitiveStrategy());
    }

    @Override
    public MavenWorkingSession getMavenWorkingSession() {
        return session;
    }

    private List<MavenDependency> preFilter(MavenResolutionFilter filter, List<MavenDependency> heap) {

        if (filter == null) {
            return heap;
        }

        List<MavenDependency> filtered = new ArrayList<MavenDependency>();
        for (MavenDependency candidate : heap) {
            if (filter.accepts(candidate)) {
                filtered.add(candidate);
            }
        }

        return filtered;
    }

    @Override
    public MavenFormatStage using(MavenResolutionStrategy strategy) throws IllegalArgumentException {
        // first, get dependencies specified for resolution in the session
        Validate.notEmpty(session.getDependencies(), "No dependencies were set for resolution");

        // create a copy
        final List<MavenDependency> prefilteredDependencies = preFilter(
                configureFilterFromSession(session, strategy.getPreResolutionFilter()), session.getDependencies());
        final List<MavenDependency> prefilteredDepsList = new ArrayList<MavenDependency>(prefilteredDependencies);
        final List<MavenDependency> depManagement = new ArrayList<MavenDependency>(session.getDependencyManagement());

        final List<RemoteRepository> repos = session.getRemoteRepositories();
        final CollectRequest request = new CollectRequest(MavenConverter.asDependencies(prefilteredDepsList),
                MavenConverter.asDependencies(depManagement), repos);

        // wrap artifact files to archives
        Collection<ArtifactResult> artifactResults = null;
        try {
            // resolution filtering
            artifactResults = session.execute(request, configureFilterFromSession(session, strategy.getResolutionFilter()));
        } catch (DependencyResolutionException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                if (cause instanceof ArtifactResolutionException) {
                    throw AetherExceptionTransformer.INSTANCE.transform((ArtifactResolutionException) cause);
                } else if (cause instanceof DependencyCollectionException) {
                    throw AetherExceptionTransformer.INSTANCE.transform((DependencyCollectionException) cause);
                }
            }
            throw AetherExceptionTransformer.INSTANCE.transform(e);
        }

        // TODO Do we need to do real post-resolution filtering? Seems excessive, plus it breaks tests
        // https://community.jboss.org/message/756355#756355
        // final MavenResolutionFilter postResolutionFilter = new CombinedFilter(RestrictPomArtifactFilter.INSTANCE,
        // (MavenResolutionFilterInternalView) strategy.getPostResolutionFilter());
        final MavenResolutionFilter postResolutionFilter = RestrictPomArtifactFilter.INSTANCE;

        // Run post-resolution filtering to weed out POMs
        final Collection<Artifact> filteredArtifacts = new ArrayList<Artifact>();
        final Collection<Artifact> artifactsToFilter = new ArrayList<Artifact>();
        for (final ArtifactResult result : artifactResults) {
            artifactsToFilter.add(result.getArtifact());
        }

        for (final Artifact artifact : artifactsToFilter) {
            final MavenCoordinate coordinate = MavenCoordinates.createCoordinate(artifact.getGroupId(),
                    artifact.getArtifactId(), artifact.getBaseVersion(),
                    PackagingType.fromPackagingType(artifact.getExtension()), artifact.getClassifier());
            final MavenDependency dependency = MavenDependencies.createDependency(coordinate, ScopeType.COMPILE, false);
            if (postResolutionFilter.accepts(dependency)) {
                filteredArtifacts.add(artifact);
            }
        }

        // Proceed to format stage
        // TODO Poor encapsulation, passing around Aether (Artifact) objects when we should be using our own
        // representation
        return new MavenFormatStageImpl(filteredArtifacts);
    }

    /**
     * {@link MavenResolutionFilter} implementation which does not allow POMs to pass through
     *
     * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
     */
    private enum RestrictPomArtifactFilter implements MavenResolutionFilterInternalView {

        INSTANCE;

        @Override
        public boolean accepts(final MavenDependency coordinate) throws IllegalArgumentException {
            if (PackagingType.POM.equals(coordinate.getPackaging())) {
                if (log.isLoggable(Level.FINER)) {
                    log.finer("Filtering out POM dependency resolution: " + coordinate
                            + "; its transitive dependencies will be included");
                }

                return false;
            }
            return true;
        }

        @Override
        public MavenResolutionFilterInternalView setDefinedDependencies(final List<MavenDependency> dependencies) {
            return this;
        }

        @Override
        public MavenResolutionFilterInternalView setDefinedDependencyManagement(final List<MavenDependency> dependencyManagement) {
            return this;
        }

    }

    private MavenResolutionFilter configureFilterFromSession(final MavenWorkingSession session,
            final MavenResolutionFilter filter) {

        Validate.notNull(session, "MavenWorkingSession must not be null");
        // filter can be null
        if (filter == null) {
            return AcceptAllFilter.INSTANCE;
        }

        // Represent as our internal SPI type
        assert filter instanceof MavenResolutionFilterInternalView : "All filters must conform to the internal SPI: "
                + MavenResolutionFilterInternalView.class.getName();
        final MavenResolutionFilterInternalView internalFilterView = MavenResolutionFilterInternalView.class.cast(filter);

        // prepare dependencies
        List<MavenDependency> dependencies = session.getDependencies();
        List<MavenDependency> dependenciesList;
        if (dependencies == null || dependencies.size() == 0) {
            dependenciesList = Collections.emptyList();
        } else {
            dependenciesList = new ArrayList<MavenDependency>(dependencies);
        }

        // prepare dependency management
        Set<MavenDependency> dependenciesMngmt = session.getDependencyManagement();
        List<MavenDependency> dependenciesMngmtList;
        if (dependenciesMngmt == null || dependenciesMngmt.size() == 0) {
            dependenciesMngmtList = Collections.emptyList();
        } else {
            dependenciesMngmtList = new ArrayList<MavenDependency>(dependencies);
        }

        // configure filter
        internalFilterView.setDefinedDependencies(dependenciesList);
        internalFilterView.setDefinedDependencyManagement(dependenciesMngmtList);

        return internalFilterView;
    }

    @Override
    public MavenStrategyStage offline(final boolean offline) {
        // Set session offline flag via the abstraction
        this.session.setOffline(offline);
        return this;
    }

    @Override
    public MavenStrategyStage offline() {
        // Delegate
        return this.offline(true);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStageBase#withClassPathResolution(boolean)
     */
    @Override
    public MavenStrategyStage withClassPathResolution(boolean useClassPathResolution) {
        if (!useClassPathResolution) {
            this.session.disableClassPathWorkspaceReader();
        }
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStageBase#withMavenCentralRepo(boolean)
     */
    @Override
    public MavenStrategyStage withMavenCentralRepo(boolean useMavenCentral) {
        if (!useMavenCentral) {
            this.session.disableMavenCentral();
        }
        return this;
    }

    /**
     * An utility to wrap an Aether resolution Exception into NoResolutionException
     *
     * @author <a href="mailto:kpiwko@redhat.com">Karel Piwko</a>
     *
     */
    private static enum AetherExceptionTransformer {
        INSTANCE;

        public NoResolvedResultException transform(DependencyCollectionException cause) {
            StringBuilder sb = new StringBuilder(
                    "Unable to get artifact(s) from the repository: Failed to collect dependencies for: ");
            CollectResult result = cause.getResult();
            if (result != null) {
                CollectRequest request = result.getRequest();
                Dependency root = request.getRoot();
                if (root != null) {
                    sb.append(root);
                } else {
                    sb.append(request.getDependencies());
                }
            }
            if (result != null && !result.getExceptions().isEmpty()) {
                sb.append(", due to ").append(result.getExceptions().get(0).getMessage());
            }

            return new NoResolvedResultException(sb.toString());

        }

        public NoResolvedResultException transform(ArtifactResolutionException cause) {
            StringBuilder sb = new StringBuilder(
                    "Unable to get artifact(s) from the repository: Failed to resolve dependencies: ");
            // add all missing resolution artifacts
            for (ArtifactResult result : cause.getResults()) {
                if (!result.isResolved()) {
                    sb.append(result.getRequest().getArtifact()).append(", ");
                }

            }
            int lastDelimiter = sb.lastIndexOf(", ");
            if (lastDelimiter == sb.length() - 2) {
                sb.delete(lastDelimiter, sb.length());
            }

            // create a detailed message
            sb.append("\n").append("Detailed message: ");
            for (ArtifactResult result : ((ArtifactResolutionException) cause).getResults()) {
                if (!result.isResolved()) {
                    sb.append(result.getRequest().getArtifact()).append(":\n");
                    for (Exception detailedCause : result.getExceptions()) {
                        if (detailedCause instanceof ArtifactNotFoundException) {
                            sb.append("\t").append(((ArtifactNotFoundException) detailedCause).getMessage()).append("\n");
                        }
                    }
                }
            }

            return new NoResolvedResultException(sb.toString());
        }

        public NoResolvedResultException transform(DependencyResolutionException cause) {
            StringBuilder sb = new StringBuilder(
                    "Unable to get artifact(s) from the repository: Failed to resolve dependencies for: ");
            DependencyResult result = cause.getResult();
            if (result != null) {
                DependencyRequest request = result.getRequest();
                DependencyNode root = request.getRoot();
                if (root != null) {
                    sb.append(root);
                }
            }

            // create a detailed message
            sb.append("\n").append("Detailed message: ");
            for (ArtifactResult artifactResult : result.getArtifactResults()) {
                if (!artifactResult.isResolved()) {
                    sb.append(artifactResult.getRequest().getArtifact()).append(":\n");
                    for (Exception detailedCause : artifactResult.getExceptions()) {
                        if (detailedCause instanceof ArtifactNotFoundException) {
                            sb.append("\t").append(((ArtifactNotFoundException) detailedCause).getMessage()).append("\n");
                        }
                    }
                }
            }

            return new NoResolvedResultException(sb.toString());

        }
    }
}
