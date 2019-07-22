package org.aion.fastvm;

/**
 * A class that provides static access to an externally-defined {@link IExternalCapabilities} class.
 *
 * This class is defensively designed to ensure that we are only ever loading and unloading the
 * same capabilities and not doing anything wrong that could easily go undetected, thus the rules
 * for installing, accessing and removing the capabilities are strict.
 *
 * External capabilities must be installed prior to invoking the run method of the {@link FastVM}
 * class, and then must be removed once that call returns.
 *
 * It is the responsibility of the caller to ensure that installation of the capabilities, calling
 * of the fvm, and removal of the capabilities is atomic, since the fvm makes use of a static
 * {@link Callback} class which in turn calls into this class, this is the only way to guarantee
 * that the fvm can safely use this provider in a multi-threaded environment.
 */
public final class CapabilitiesProvider {
    private static IExternalCapabilities externalCapabilities;

    /**
     * Returns the capabilities. If there are no capabilities installed yet, an exception is thrown.
     *
     * @return the capabilities.
     */
    public static IExternalCapabilities getExternalCapabilities() {
        if (externalCapabilities == null) {
            throw new IllegalStateException("Cannot get capabilities - it has not been set yet!");
        }
        return externalCapabilities;
    }

    /**
     * Installs the capabilities. An exception is thrown if either the capabilities to be installed
     * is null or if there is already a capabilities installed.
     *
     * @param capabilities the capabilities to install.
     */
    public static void installExternalCapabilities(IExternalCapabilities capabilities) {
        if (capabilities == null) {
            throw new NullPointerException("Cannot set null capabilities!");
        }
        if (externalCapabilities != null) {
            throw new IllegalStateException("External capabilities are already set, cannot overwrite!");
        }
        externalCapabilities = capabilities;
    }

    /**
     * Removes the capabilities. This action is always safe.
     */
    public static void removeExternalCapabilities() {
        externalCapabilities = null;
    }
}
