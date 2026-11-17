package com.plumb5.monitor.core;

/**
 * Defines the severity level of a tracked error.
 */
public enum P5ErrorSeverity {
    /** General information about a non-critical issue. */
    INFO,

    /** A potential issue that might need attention. */
    WARNING,

    /** A critical failure that impacts app functionality. */
    CRITICAL
}
