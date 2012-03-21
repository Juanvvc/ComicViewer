/**
 * Provides the classes necessary to manage comic files.
 * For example, classes to read a RAR file, ZIP file,
 * or an images directory.
 *
 * All these reader extend from a base reader: Reader.
 * This one is in charge of common tasks such as taking
 * trace of the currently displayed image, or controlling
 * that the application is not requesting an out-of-bound
 * page.
 *
 * @since 1.0
 */
package com.juanvvc.comicviewer.readers;
