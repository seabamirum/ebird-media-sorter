package dev.seabird.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.avi.AviDirectory;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.mov.media.QuickTimeMediaDirectory;
import com.drew.metadata.mp4.Mp4Directory;

/**
 * A {@link CreationDateProvider} that extracts creation timestamps from media
 * file metadata using the
 * <a href="https://drewnoakes.com/code/exif/">metadata-extractor</a> library.
 *
 * <p>Supported file types:
 * <ul>
 *   <li><b>Images</b> – reads {@code DateTimeOriginal} from the EXIF SubIFD
 *       directory, formatted as {@code yyyy:MM:dd HH:mm:ss}.</li>
 *   <li><b>Videos</b> – reads the creation time tag from the format-specific
 *       directory:
 *       <ul>
 *         <li>{@code .mp4} → {@link Mp4Directory}</li>
 *         <li>{@code .mov} → {@link QuickTimeMediaDirectory}</li>
 *         <li>{@code .avi} → {@link AviDirectory}</li>
 *       </ul>
 *       Video timestamps are parsed using a timezone-aware formatter.</li>
 * </ul>
 *
 * <p>Files whose extension is not recognised as an image or video return
 * {@code null} without attempting to read metadata. If metadata cannot be
 * read or the relevant directory/tag is absent, {@code null} is also returned.
 */
public class ExifCreationDateProvider implements CreationDateProvider
{
    private static final Logger log = LoggerFactory.getLogger(ExifCreationDateProvider.class);

    /** Formatter for EXIF image date/time strings ({@code yyyy:MM:dd HH:mm:ss}). */
    private static final DateTimeFormatter imageDtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    /**
     * Returns the metadata {@link Directory} that holds the creation time for
     * the given video file extension.
     *
     * @param metadata the parsed file metadata
     * @param ext      the lowercase file extension (e.g. {@code "mp4"})
     * @return the relevant directory, or {@code null} if the extension is not
     *         a recognised video type or the directory is absent from the metadata
     */
    private static Directory getVideoDirectory(Metadata metadata, String ext) {
        return switch (ext) {
            case "mp4" -> metadata.getFirstDirectoryOfType(Mp4Directory.class);
            case "mov" -> metadata.getFirstDirectoryOfType(QuickTimeMediaDirectory.class);
            case "avi" -> metadata.getFirstDirectoryOfType(AviDirectory.class);
            default -> null;
        };
    }

    /**
     * Returns the metadata tag constant that represents the creation time for
     * the given video file extension.
     *
     * @param ext the lowercase file extension (e.g. {@code "mov"})
     * @return the tag identifier to pass to {@link Directory#getString(int)}
     * @throws IllegalArgumentException if {@code ext} is not a supported video
     *                                  extension
     */
    private static int getVideoTag(String ext) {
        return switch (ext) {
            case "mp4" -> Mp4Directory.TAG_CREATION_TIME;
            case "mov" -> QuickTimeMediaDirectory.TAG_CREATION_TIME;
            case "avi" -> AviDirectory.TAG_DATETIME_ORIGINAL;
            default -> throw new IllegalArgumentException("Unsupported video extension: " + ext);
        };
    }

    /**
     * Reads the creation date of a media file from its embedded metadata.
     *
     * <p>The method opens the file as a stream, delegates metadata parsing to
     * {@link ImageMetadataReader}, then locates the appropriate directory and
     * tag for the file type. Any warning-level issues (e.g. a corrupt metadata
     * block) are logged but do not propagate as exceptions.
     *
     * @param f         the path to the image or video file
     * @param hrsOffset the number of hours to add to the raw timestamp; useful
     *                  for correcting timezone or camera-clock drift; use
     *                  {@code 0} for no adjustment
     * @return the creation {@link LocalDateTime} with {@code hrsOffset} applied,
     *         or {@code null} if the file type is unsupported, metadata cannot
     *         be read, or the creation-time tag is absent
     * @throws IOException if an I/O error occurs while opening or reading the file
     */
    @Override
    public LocalDateTime findCreationDate(Path f, long hrsOffset) throws IOException {
        String fileName = f.getFileName().toString();
        String fileExt = MediaSortUtils.getFileExtension(fileName).toLowerCase();

        boolean isImage = MediaSortUtils.imageExtensions.contains(fileExt);
        boolean isVideo = MediaSortUtils.videoExtensions.contains(fileExt);

        if (!isImage && !isVideo) {
            return null;
        }

        Metadata metadata = null;
        try (InputStream stream = Files.newInputStream(f)) {
            try {
                metadata = ImageMetadataReader.readMetadata(stream);
            } catch (ImageProcessingException | IOException e) {
                log.warn("Error reading metadata from {}", fileName, e);
            }
        }

        if (metadata == null) {
            return null;
        }

        DateTimeFormatter dtf = isImage ? imageDtf : timezoneDtf;
        Directory directory = isImage
                ? metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class)
                : getVideoDirectory(metadata, fileExt);

        if (directory == null)
            return null;

        int tag = isImage ? ExifDirectoryBase.TAG_DATETIME_ORIGINAL : getVideoTag(fileExt);
        String dateTimeStr = directory.getString(tag);

        return parseTime(dateTimeStr, dtf, hrsOffset);
    }
}