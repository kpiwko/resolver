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
package org.jboss.shrinkwrap.resolver.api.maven.strategy;

import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependency;
import org.jboss.shrinkwrap.resolver.api.maven.filter.MavenResolutionFilter;

/**
 * {@link MavenResolutionStrategy} implementation where {@link MavenDependency}s are accepted based on transitivity.
 * This is implemented as a pass-through filter, as the backend will be supplying the transitive dependencies; we'll
 * just allow them.
 *
 * @author <a href="mailto:kpiwko@redhat.com">Karel Piwko</a>
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public enum TransitiveStrategy implements MavenResolutionStrategy {

    INSTANCE;

    /**
     * Returns a {@link MavenResolutionFilter} chain allowing all {@link MavenDependency}s to pass-through.
     *
     * @see org.jboss.shrinkwrap.resolver.api.maven.strategy.MavenResolutionStrategy#getPreResolutionFilters()
     */
    @Override
    public MavenResolutionFilter[] getResolutionFilters() {
        return MavenResolutionFilterUtil.getEmptyChain();
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.shrinkwrap.resolver.api.maven.strategy.MavenResolutionStrategy#getTransitiveExclusionPolicy()
     */
    @Override
    public TransitiveExclusionPolicy getTransitiveExclusionPolicy() {
        return DefaultTransitiveExclusionPolicy.INSTANCE;
    }
}
