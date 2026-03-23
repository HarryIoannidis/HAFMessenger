package com.haf.shared.utils;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

public final class FilePerms {
    private static final Set<PosixFilePermission> DIR_700 =
            EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);

    private static final Set<PosixFilePermission> FILE_600 =
            EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);

    /**
     * Prevents instantiation of this utility class.
     */
    private FilePerms() {}

    /**
     * Ensures that the given directory exists and is 700.
     *
     * @param dir the directory to ensure
     * @throws IOException if the directory cannot be created or secured
     */
    public static void ensureDir700(Path dir) throws IOException {
        if (!Files.exists(dir)) Files.createDirectories(dir);
        try {
            Files.setPosixFilePermissions(dir, DIR_700);
        } catch (UnsupportedOperationException ignored) {
            if (!Files.isWritable(dir)) {
                throw new AccessDeniedException(dir.toString(), null, "Cannot write to directory on non-POSIX system");
            }
        }
    }

    /**
     * Writes the given data to the given file and sets the permissions to 600.
     *
     * @param file the file to write to
     * @param data the data to write
     * @throws IOException if the file cannot be created, written, or secured
     */
    public static void writeFile600(Path file, byte[] data) throws IOException {
        if (Files.notExists(file)) Files.createFile(file);

        Files.write(file, data, StandardOpenOption.TRUNCATE_EXISTING);

        try { 
            Files.setPosixFilePermissions(file, FILE_600); 
        } catch (UnsupportedOperationException ignored) {}
    }
}
