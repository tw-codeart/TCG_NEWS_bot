package com.twcodeart.tcgnews.source;

/**
 * Reports an external source retrieval or parsing failure to the caller.
 */
public class NewsSourceException extends Exception {

    public NewsSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
