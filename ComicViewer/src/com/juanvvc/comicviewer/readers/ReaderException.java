package com.juanvvc.comicviewer.readers;

/** An Exception that Readers may throw.
 * @author juanvi
 *
 */
public class ReaderException extends Exception {
    /** A random number for the Serializable interface. */
    private static final long serialVersionUID = 1L;

    /** Constructs an exception from a message.
     * @param msg The message of the exception
     */
    public ReaderException(final String msg) {
        super(msg);
    }
}
