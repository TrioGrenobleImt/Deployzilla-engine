package fr.imt.deployzilla.deployzilla.business.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DirectorySanitizer {

    /**
     * Sanitize directory name to prevent path traversal attacks.
     */
    public static String sanitizeDirectoryName(String dirName) {
        return dirName
                .replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("\\.\\.", "_");
    }

}
