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

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jboss.shrinkwrap.resolver.api.formatprocessor.FormatProcessor;
import org.jboss.shrinkwrap.resolver.api.formatprocessor.FormatProcessors;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.PackagingType;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinates;
import org.jboss.shrinkwrap.resolver.impl.maven.util.IOUtil;
import org.jboss.shrinkwrap.resolver.impl.maven.util.Validate;
import org.sonatype.aether.artifact.Artifact;

/**
 * Immutable implementation of {@link MavenResolvedArtifact}.
 *
 * @author <a href="mailto:mmatloka@gmail.com">Michal Matloka</a>
 * @author <a href="mailto:kpiwko@redhat.com">Michal Matloka</a>
 */
public class MavenResolvedArtifactImpl implements MavenResolvedArtifact {
    private static final Logger log = Logger.getLogger(MavenResolvedArtifactImpl.class.getName());

    private final MavenCoordinate mavenCoordinate;
    private final String resolvedVersion;
    private final boolean snapshotVersion;
    private final String extension;
    private final File file;

    private MavenResolvedArtifactImpl(final MavenCoordinate mavenCoordinate, final String resolvedVersion,
            final boolean snapshotVersion, final String extension, final File file) {
        this.mavenCoordinate = mavenCoordinate;
        this.resolvedVersion = resolvedVersion;
        this.snapshotVersion = snapshotVersion;
        this.extension = extension;
        this.file = file;
    }

    /**
     * Creates ResolvedArtifactInfo based on Artifact.
     *
     * @param artifact
     * artifact
     * @param file
     * file contained in artifact
     * @return
     */
    static MavenResolvedArtifact fromArtifact(final Artifact artifact) {
        final MavenCoordinate mavenCoordinate = MavenCoordinates.createCoordinate(artifact.getGroupId(),
                artifact.getArtifactId(), artifact.getBaseVersion(),
                PackagingType.fromPackagingType(artifact.getExtension()), artifact.getClassifier());

        return new MavenResolvedArtifactImpl(mavenCoordinate, artifact.getVersion(), artifact.isSnapshot(),
                artifact.getExtension(), artifactToFile(artifact));
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifactImpl#getCoordinate()
     */
    @Override
    public MavenCoordinate getCoordinate() {
        return mavenCoordinate;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact#getResolvedVersion()
     */
    @Override
    public String getResolvedVersion() {
        return resolvedVersion;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact#isSnapshotVersion()
     */
    @Override
    public boolean isSnapshotVersion() {
        return snapshotVersion;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact#getExtension()
     */
    @Override
    public String getExtension() {
        return extension;
    }

    @Override
    public <RETURNTYPE> RETURNTYPE as(Class<RETURNTYPE> returnType) {
        if (returnType == null) {
            throw new IllegalArgumentException("Type must be specified.");
        }

        FormatProcessor<? super MavenResolvedArtifact, RETURNTYPE> processor = FormatProcessors.find(
                MavenResolvedArtifact.class, returnType);

        return processor.process(this, returnType);
    }

    @Override
    public File asFile() {
        return file;
    }

    @Override
    public InputStream asInputStream() {
        return as(InputStream.class);
    }

    @Override
    public MavenResolvedArtifact asResolvedArtifact() {
        return as(MavenResolvedArtifact.class);
    }

    /**
     * Maps an artifact to a file. This allows ShrinkWrap Maven resolver to package reactor related dependencies.
     *
     *
     */
    private static File artifactToFile(final Artifact artifact) throws IllegalArgumentException {
        if (artifact == null) {
            throw new IllegalArgumentException("ArtifactResult must not be null");
        }

        // FIXME: this is not a safe assumption, file can have a different name
        if ("pom.xml".equals(artifact.getFile().getName())) {

            String artifactId = artifact.getArtifactId();
            String extension = artifact.getExtension();

            File root = new File(artifact.getFile().getParentFile(), "target/classes");
            try {
                File archive = File.createTempFile(artifactId + "-", "." + extension);
                archive.deleteOnExit();
                PackageDirHelper.packageDirectories(archive, root);
                return archive;
            } catch (IOException e) {
                throw new IllegalArgumentException("Unable to get artifact " + artifactId + " from the classpath", e);

            }

        } else {
            return artifact.getFile();
        }
    }

    /**
     * I/O Utilities needed by the enclosing class
     *
     * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
     */
    private static class PackageDirHelper {
        private PackageDirHelper() {
            throw new UnsupportedOperationException("No instances should be created; stateless class");
        }

        private static void safelyClose(final Closeable closeable) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (final IOException ignore) {
                    if (log.isLoggable(Level.FINER)) {
                        log.finer("Could not close stream due to: " + ignore.getMessage() + "; ignoring");
                    }
                }
            }
        }

        static void packageDirectories(final File outputFile, final File... directories) throws IOException {

            Validate.notNullAndNoNullValues(directories, "Directories to be packaged must be specified");

            final ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(outputFile));

            for (File directory : directories) {
                for (String entry : fileListing(directory)) {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(new File(directory, entry));
                        zipFile.putNextEntry(new ZipEntry(entry));
                        IOUtil.copy(fis, zipFile);
                    } finally {
                        safelyClose(fis);
                    }
                }
            }
            safelyClose(zipFile);
        }

        private static List<String> fileListing(final File directory) {
            final List<String> list = new ArrayList<String>();
            generateFileList(list, directory, directory);
            return list;
        }

        private static void generateFileList(final List<String> list, final File root, final File file) {
            if (file.isFile()) {
                list.add(file.getAbsolutePath().substring(root.getAbsolutePath().length() + 1));
            } else if (file.isDirectory()) {
                for (File next : file.listFiles()) {
                    generateFileList(list, root, next);
                }
            }
        }
    }

}
