package com.snowgears.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Guards the plexus-utils / plexus-xml pairing.
 *
 * <p>plexus-utils 4.x moved {@code org.codehaus.plexus.util.xml} into the separate
 * {@code plexus-xml} artifact. Paper's {@code LibraryLoader} builds an aether
 * {@code RepositorySystem} while the plugin enables, and the Maven runtime it uses
 * still calls the 3.x XML API.
 *
 * <p>When the XML package is absent, {@code DefaultServiceLocator} swallows the
 * resulting {@code NoClassDefFoundError} and returns {@code null}, so the failure
 * surfaces as a NullPointerException at {@code LibraryLoader.<init>} — once per test
 * that enables the plugin, which is to say nearly all of them, and with no mention
 * of the real cause. This test asserts the whole thing directly so the breakage
 * names itself.
 */
class PlexusXmlOnClasspathTest {

    /** Every symbol the Maven runtime resolves out of {@code plexus-utils}. */
    private static final String[] REQUIRED_CLASSES = {
            "org.codehaus.plexus.util.xml.Xpp3Dom",
            "org.codehaus.plexus.util.xml.Xpp3DomBuilder",
            "org.codehaus.plexus.util.xml.XmlStreamReader",
            "org.codehaus.plexus.util.xml.XmlStreamWriter",
            "org.codehaus.plexus.util.xml.pull.MXParser",
            "org.codehaus.plexus.util.xml.pull.MXSerializer",
            "org.codehaus.plexus.util.xml.pull.XmlPullParser",
            "org.codehaus.plexus.util.xml.pull.XmlSerializer",
            "org.codehaus.plexus.util.xml.pull.XmlPullParserException",
            "org.codehaus.plexus.util.xml.pull.EntityReplacementMap",
    };

    @Test
    void mavenRuntimeCanResolveItsRepositorySystem() {
        // Mirrors what org.bukkit.plugin.java.LibraryLoader does on plugin load.
        // Fails with the offending class named rather than a bare NPE.
        assertDoesNotThrow(() -> {
            org.eclipse.aether.impl.DefaultServiceLocator locator =
                    org.apache.maven.repository.internal.MavenRepositorySystemUtils.newServiceLocator();
            locator.addService(
                    org.eclipse.aether.spi.connector.RepositoryConnectorFactory.class,
                    org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory.class);
            locator.addService(
                    org.eclipse.aether.spi.connector.transport.TransporterFactory.class,
                    org.eclipse.aether.transport.http.HttpTransporterFactory.class);

            Object repositorySystem = locator.getService(org.eclipse.aether.RepositorySystem.class);
            // DefaultServiceLocator reports a failed component as null, never as an
            // exception, so the null case is the one worth asserting on.
            org.junit.jupiter.api.Assertions.assertNotNull(repositorySystem,
                    "Paper's LibraryLoader cannot build an aether RepositorySystem; "
                            + "org.codehaus.plexus.util.xml is missing from the classpath. "
                            + "plexus-utils 4.x ships it as the separate plexus-xml artifact.");
        });
    }

    @Test
    void plexusXmlClassesAreOnTheClasspath() {
        for (String className : REQUIRED_CLASSES) {
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> Class.forName(className),
                    className + " is missing. plexus-utils 4.x moved the util.xml package "
                            + "into plexus-xml; declare it explicitly, not only in dependencyManagement.");
        }
    }
}