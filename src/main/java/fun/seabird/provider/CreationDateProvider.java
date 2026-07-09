package fun.seabird.provider;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Provides a creation date for a given media file.
 *
 * <p>Implementations are responsible for extracting or inferring a creation
 * timestamp from a file (e.g. from EXIF metadata, filesystem attributes, or
 * embedded tags). An optional hour offset can be applied to adjust for
 * timezone differences or camera clock drift.
 */
public interface CreationDateProvider
{
    /**
     * Formatter for date/time strings found in image metadata (e.g. EXIF
     * {@code DateTimeOriginal}), following the pattern {@code yyyy:MM:dd HH:mm:ss}.
     */
    static final DateTimeFormatter imageDtf = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    /**
     * Formatter for timezone-aware date/time strings (e.g. from video metadata),
     * following the pattern {@code EEE MMM dd HH:mm:ss zzz yyyy}.
     */
    static final DateTimeFormatter timezoneDtf = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy");

    /**
     * Parses a date/time string into a {@link LocalDateTime} using the given formatter.
     *
     * @param dateTimeOrigStr the date/time string to parse; may be {@code null}
     * @param dtf             the formatter to use for parsing
     * @return the parsed {@link LocalDateTime}, or {@code null} if
     *         {@code dateTimeOrigStr} is {@code null} or cannot be parsed
     */
    default LocalDateTime parseTime(String dateTimeOrigStr, DateTimeFormatter dtf)
    {
        try
        {
            return dateTimeOrigStr == null ? null : LocalDateTime.parse(dateTimeOrigStr, dtf);
        }
        catch (DateTimeParseException dtpe)
        {
            System.err.println("Can't parse LocalDateTime for " + dateTimeOrigStr);
            return null;
        }
    }

    /**
     * Parses a date/time string into a {@link LocalDateTime} and applies an
     * hour offset to the result.
     *
     * <p>This is useful for correcting timestamps that were recorded in a
     * different timezone or when a camera clock was set incorrectly.
     *
     * @param dateTimeOrigStr the date/time string to parse; may be {@code null}
     * @param dtf             the formatter to use for parsing
     * @param hrsOffset       the number of hours to add to the parsed time;
     *                        use {@code 0} to leave the time unchanged, negative
     *                        values to subtract hours
     * @return the parsed and offset-adjusted {@link LocalDateTime}, or
     *         {@code null} if {@code dateTimeOrigStr} is {@code null} or
     *         cannot be parsed
     */
    default LocalDateTime parseTime(String dateTimeOrigStr, DateTimeFormatter dtf, long hrsOffset)
    {
        LocalDateTime mediaTime = parseTime(dateTimeOrigStr, dtf);
        return mediaTime == null ? null : (hrsOffset == 0l ? mediaTime : mediaTime.plusHours(hrsOffset));
    }

    /**
     * Determines the creation date of the given file.
     *
     * @param f         the path to the media file
     * @param hrsOffset the number of hours to add to the raw timestamp extracted
     *                  from the file; use {@code 0} to leave the time unchanged
     * @return the creation date/time of the file, or {@code null} if it cannot
     *         be determined
     * @throws IOException if an I/O error occurs while reading the file
     */
    LocalDateTime findCreationDate(Path f, long hrsOffset) throws IOException;
}