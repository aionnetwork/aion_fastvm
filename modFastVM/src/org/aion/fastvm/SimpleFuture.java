package org.aion.fastvm;

/**
 * A very simple alternative to {@link java.util.concurrent.Future}, which still represents the
 * result of an asynchronous computation, but without all of the methods and exceptions that are
 * irrelevant to the use of this class.
 *
 * <p>The {@code get()} method is a blocking method that will block until the asynchronous
 * computation completes, and then returns its result.
 *
 * <p>A {@code SimpleFuture} retains no reference to the computation that it represents, and it is
 * the responsibility of the caller to use this object in a meaningful way.
 *
 * @param <R> The result of some asynchronous computation.
 */
public interface SimpleFuture<R> {

    /**
     * Returns the result of the asynchronous computation that returned this {@code SimpleFuture} to
     * its caller.
     *
     * <p>This method will block until the computation has completed.
     *
     * @return The result of the computation.
     */
    R get();
}
